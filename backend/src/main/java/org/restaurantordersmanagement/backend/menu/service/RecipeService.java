package org.restaurantordersmanagement.backend.menu.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.model.RawMaterial;
import org.restaurantordersmanagement.backend.menu.model.Recipe;
import org.restaurantordersmanagement.backend.menu.repository.MealSizeRepository;
import org.restaurantordersmanagement.backend.menu.repository.RawMaterialRepository;
import org.restaurantordersmanagement.backend.menu.repository.RecipeRepository;
import org.restaurantordersmanagement.backend.menu.web.RecipeLineRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Recipe rows aren't a child collection cascaded from MealSize (no
 * MealSize.recipes field exists), so replacing them is a plain
 * delete-removed/save-kept operation - no orphanRemoval/session-timing
 * concerns like Meal/Category's translation-replace logic in #13.
 */
@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final MealSizeRepository mealSizeRepository;
    private final RawMaterialRepository rawMaterialRepository;

    public RecipeService(
            RecipeRepository recipeRepository,
            MealSizeRepository mealSizeRepository,
            RawMaterialRepository rawMaterialRepository) {
        this.recipeRepository = recipeRepository;
        this.mealSizeRepository = mealSizeRepository;
        this.rawMaterialRepository = rawMaterialRepository;
    }

    public List<Recipe> getRecipe(Long mealSizeId) {
        ensureMealSizeExists(mealSizeId);
        return recipeRepository.findByMealSizeId(mealSizeId);
    }

    @Transactional
    public List<Recipe> replaceRecipe(Long mealSizeId, List<RecipeLineRequest> lines) {
        MealSize mealSize = mealSizeRepository.findById(mealSizeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal size not found"));

        List<Recipe> existing = recipeRepository.findByMealSizeId(mealSizeId);
        Map<Long, Recipe> existingByRawMaterialId =
                existing.stream().collect(Collectors.toMap(r -> r.getRawMaterial().getId(), r -> r));

        List<Recipe> toKeep = new ArrayList<>();
        for (RecipeLineRequest line : lines) {
            RawMaterial rawMaterial = rawMaterialRepository.findById(line.rawMaterialId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Raw material not found"));
            if (line.quantity() == null || line.quantity().signum() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be non-negative");
            }

            Recipe recipe = existingByRawMaterialId.get(line.rawMaterialId());
            if (recipe == null) {
                recipe = new Recipe();
                recipe.setMealSize(mealSize);
                recipe.setRawMaterial(rawMaterial);
            }
            recipe.setQuantity(line.quantity());
            toKeep.add(recipe);
        }

        List<Recipe> toDelete = existing.stream()
                .filter(recipe -> toKeep.stream()
                        .noneMatch(kept -> kept.getRawMaterial().getId().equals(recipe.getRawMaterial().getId())))
                .toList();

        recipeRepository.deleteAll(toDelete);
        return recipeRepository.saveAll(toKeep);
    }

    private void ensureMealSizeExists(Long mealSizeId) {
        if (!mealSizeRepository.existsById(mealSizeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal size not found");
        }
    }

}
