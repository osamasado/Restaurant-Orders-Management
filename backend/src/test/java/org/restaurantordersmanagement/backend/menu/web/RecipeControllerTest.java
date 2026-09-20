package org.restaurantordersmanagement.backend.menu.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.CategoryTranslation;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.model.RawMaterial;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.repository.MealRepository;
import org.restaurantordersmanagement.backend.menu.repository.RawMaterialRepository;
import org.restaurantordersmanagement.backend.menu.repository.RecipeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Not @Transactional - see CategoryControllerTest's javadoc for why. tearDown() deletes tracked ids instead. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RecipeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private RawMaterialRepository rawMaterialRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    private final List<Long> createdMealIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();
    private final List<Long> createdRawMaterialIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long mealId : createdMealIds) {
            mealRepository.findById(mealId).ifPresent(mealRepository::delete);
        }
        for (Long categoryId : createdCategoryIds) {
            categoryRepository.findById(categoryId).ifPresent(categoryRepository::delete);
        }
        for (Long rawMaterialId : createdRawMaterialIds) {
            rawMaterialRepository.findById(rawMaterialId).ifPresent(rawMaterialRepository::delete);
        }
    }

    private Long createMealSize() {
        Category category = new Category();
        category.setSortOrder(1);
        CategoryTranslation translation = new CategoryTranslation();
        translation.setCategory(category);
        translation.setLanguage(Language.EN);
        translation.setName("Mains");
        category.getTranslations().add(translation);
        Category savedCategory = categoryRepository.saveAndFlush(category);
        createdCategoryIds.add(savedCategory.getId());

        Meal meal = new Meal();
        meal.setCategory(savedCategory);
        MealSize size = new MealSize();
        size.setMeal(meal);
        size.setPrice(new BigDecimal("10.00"));
        meal.getSizes().add(size);
        Meal savedMeal = mealRepository.saveAndFlush(meal);
        createdMealIds.add(savedMeal.getId());

        return savedMeal.getSizes().get(0).getId();
    }

    private Long createRawMaterial(String name) {
        RawMaterial rawMaterial = new RawMaterial();
        rawMaterial.setName(name);
        rawMaterial.setUnit("kg");
        Long id = rawMaterialRepository.saveAndFlush(rawMaterial).getId();
        createdRawMaterialIds.add(id);
        return id;
    }

    @Test
    void getRejectsNonAdminRole() throws Exception {
        Long mealSizeId = createMealSize();

        mockMvc.perform(get("/api/meal-sizes/" + mealSizeId + "/recipe").with(user("waiter").roles("WAITER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getReturns404ForUnknownMealSize() throws Exception {
        mockMvc.perform(get("/api/meal-sizes/999999/recipe").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void replaceRejectsUnknownRawMaterial() throws Exception {
        Long mealSizeId = createMealSize();

        mockMvc.perform(put("/api/meal-sizes/" + mealSizeId + "/recipe")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"rawMaterialId": 999999, "quantity": 1.0}]
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replaceRejectsNegativeQuantity() throws Exception {
        Long mealSizeId = createMealSize();
        Long rawMaterialId = createRawMaterial("Flour");

        mockMvc.perform(put("/api/meal-sizes/" + mealSizeId + "/recipe")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"rawMaterialId\": " + rawMaterialId + ", \"quantity\": -1}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replaceAddsUpdatesAndRemovesLines() throws Exception {
        Long mealSizeId = createMealSize();
        Long flourId = createRawMaterial("Flour");
        Long sugarId = createRawMaterial("Sugar");
        Long eggId = createRawMaterial("Egg");

        mockMvc.perform(put("/api/meal-sizes/" + mealSizeId + "/recipe")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "[{\"rawMaterialId\": " + flourId + ", \"quantity\": 0.5},"
                                        + "{\"rawMaterialId\": " + sugarId + ", \"quantity\": 0.2}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/meal-sizes/" + mealSizeId + "/recipe").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.rawMaterialId == " + flourId + ")].quantity").value(0.5))
                .andExpect(jsonPath("$[?(@.rawMaterialId == " + sugarId + ")].rawMaterialName").value("Sugar"));

        // Drop sugar, bump flour's quantity, add egg.
        mockMvc.perform(put("/api/meal-sizes/" + mealSizeId + "/recipe")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "[{\"rawMaterialId\": " + flourId + ", \"quantity\": 0.75},"
                                        + "{\"rawMaterialId\": " + eggId + ", \"quantity\": 2}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.rawMaterialId == " + flourId + ")].quantity").value(0.75))
                .andExpect(jsonPath("$[?(@.rawMaterialId == " + eggId + ")].quantity").value(2))
                .andExpect(jsonPath("$[?(@.rawMaterialId == " + sugarId + ")]").isEmpty());
    }

}
