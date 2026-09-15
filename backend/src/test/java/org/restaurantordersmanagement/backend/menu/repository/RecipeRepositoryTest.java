package org.restaurantordersmanagement.backend.menu.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.model.RawMaterial;
import org.restaurantordersmanagement.backend.menu.model.Recipe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class RecipeRepositoryTest {

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RawMaterialRepository rawMaterialRepository;

    @Test
    void linksMealSizeToRawMaterialWithQuantity() {
        Category category = new Category();
        category.setSortOrder(1);
        category = categoryRepository.saveAndFlush(category);

        Meal meal = new Meal();
        meal.setCategory(category);
        meal.setAvailable(true);
        MealSize size = new MealSize();
        size.setMeal(meal);
        size.setLabel("200 g");
        size.setPrice(new BigDecimal("14.50"));
        meal.getSizes().add(size);
        meal = mealRepository.saveAndFlush(meal);
        MealSize savedSize = meal.getSizes().get(0);

        RawMaterial veal = new RawMaterial();
        veal.setName("Veal");
        veal.setUnit("g");
        veal = rawMaterialRepository.saveAndFlush(veal);

        Recipe recipe = new Recipe();
        recipe.setMealSize(savedSize);
        recipe.setRawMaterial(veal);
        recipe.setQuantity(new BigDecimal("180.00"));

        Recipe saved = recipeRepository.saveAndFlush(recipe);

        Optional<Recipe> found = recipeRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(savedSize.getId(), found.get().getMealSize().getId());
        assertEquals(veal.getId(), found.get().getRawMaterial().getId());
        assertEquals(0, new BigDecimal("180.00").compareTo(found.get().getQuantity()));
    }

}
