package org.restaurantordersmanagement.backend.menu.web;

import java.util.List;
import org.restaurantordersmanagement.backend.menu.service.RecipeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meal-sizes/{mealSizeId}/recipe")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<RecipeLineDto> get(@PathVariable Long mealSizeId) {
        return recipeService.getRecipe(mealSizeId).stream().map(RecipeLineDto::from).toList();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<RecipeLineDto> replace(@PathVariable Long mealSizeId, @RequestBody List<RecipeLineRequest> lines) {
        return recipeService.replaceRecipe(mealSizeId, lines).stream().map(RecipeLineDto::from).toList();
    }

}
