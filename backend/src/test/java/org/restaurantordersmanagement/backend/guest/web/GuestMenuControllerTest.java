package org.restaurantordersmanagement.backend.guest.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.restaurantordersmanagement.backend.menu.model.MealSizeTranslation;
import org.restaurantordersmanagement.backend.menu.model.MealTranslation;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.repository.MealRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Not @Transactional - see CategoryControllerTest's javadoc for why. tearDown() deletes tracked ids instead. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GuestMenuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MealRepository mealRepository;

    private final List<Long> createdMealIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long mealId : createdMealIds) {
            mealRepository.findById(mealId).ifPresent(mealRepository::delete);
        }
        for (Long categoryId : createdCategoryIds) {
            categoryRepository.findById(categoryId).ifPresent(categoryRepository::delete);
        }
    }

    private Category createCategory(int sortOrder, String nameEn, String nameDe) {
        Category category = new Category();
        category.setSortOrder(sortOrder);

        CategoryTranslation en = new CategoryTranslation();
        en.setCategory(category);
        en.setLanguage(Language.EN);
        en.setName(nameEn);
        category.getTranslations().add(en);

        CategoryTranslation de = new CategoryTranslation();
        de.setCategory(category);
        de.setLanguage(Language.DE);
        de.setName(nameDe);
        category.getTranslations().add(de);

        Category saved = categoryRepository.saveAndFlush(category);
        createdCategoryIds.add(saved.getId());
        return saved;
    }

    private Meal createMeal(Category category, boolean available, String nameEn, String nameDe) {
        Meal meal = new Meal();
        meal.setCategory(category);
        meal.setAvailable(available);

        MealTranslation en = new MealTranslation();
        en.setMeal(meal);
        en.setLanguage(Language.EN);
        en.setName(nameEn);
        en.setDescription("A tasty dish");
        meal.getTranslations().add(en);

        MealTranslation de = new MealTranslation();
        de.setMeal(meal);
        de.setLanguage(Language.DE);
        de.setName(nameDe);
        meal.getTranslations().add(de);

        MealSize size = new MealSize();
        size.setMeal(meal);
        size.setPrice(new BigDecimal("12.50"));
        MealSizeTranslation sizeEn = new MealSizeTranslation();
        sizeEn.setMealSize(size);
        sizeEn.setLanguage(Language.EN);
        sizeEn.setLabel("Regular");
        size.getTranslations().add(sizeEn);
        meal.getSizes().add(size);

        Meal saved = mealRepository.saveAndFlush(meal);
        createdMealIds.add(saved.getId());
        return saved;
    }

    @Test
    void menuIsReachableWithoutAuthentication() throws Exception {
        Category category = createCategory(1, "Mains", "Hauptspeisen");
        createMeal(category, true, "Schnitzel", "Schnitzel");

        mockMvc.perform(get("/api/guest/menu").param("language", "EN"))
                .andExpect(status().isOk());
    }

    @Test
    void unavailableMealsAreExcludedAndEmptyCategoriesOmitted() throws Exception {
        Category categoryWithAvailable = createCategory(1, "Mains", "Hauptspeisen");
        createMeal(categoryWithAvailable, true, "Schnitzel", "Schnitzel");
        createMeal(categoryWithAvailable, false, "Sold Out Dish", "Ausverkauft");

        Category categoryAllUnavailable = createCategory(2, "Desserts", "Nachspeisen");
        createMeal(categoryAllUnavailable, false, "Hidden Dessert", "Verstecktes Dessert");

        mockMvc.perform(get("/api/guest/menu").param("language", "EN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Mains"))
                .andExpect(jsonPath("$[0].meals.length()").value(1))
                .andExpect(jsonPath("$[0].meals[0].name").value("Schnitzel"));
    }

    @Test
    void resolvesRequestedLanguageAndFallsBackToEnglish() throws Exception {
        Category category = createCategory(1, "Mains", "Hauptspeisen");
        createMeal(category, true, "Schnitzel EN", "Schnitzel DE");

        mockMvc.perform(get("/api/guest/menu").param("language", "DE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Hauptspeisen"))
                .andExpect(jsonPath("$[0].meals[0].name").value("Schnitzel DE"));

        // No AR translation exists for this category/meal - falls back to EN.
        mockMvc.perform(get("/api/guest/menu").param("language", "AR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Mains"))
                .andExpect(jsonPath("$[0].meals[0].name").value("Schnitzel EN"))
                .andExpect(jsonPath("$[0].meals[0].sizes[0].label").value("Regular"));
    }

    @Test
    void missingLanguageParamIsRejected() throws Exception {
        mockMvc.perform(get("/api/guest/menu")).andExpect(status().isBadRequest());
    }

}
