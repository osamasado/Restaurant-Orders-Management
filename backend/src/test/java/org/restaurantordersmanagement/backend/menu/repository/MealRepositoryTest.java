package org.restaurantordersmanagement.backend.menu.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.model.MealSizeTranslation;
import org.restaurantordersmanagement.backend.menu.model.MealTranslation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MealRepositoryTest {

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void savesMealWithCascadedTranslationsAndSizes() {
        Category category = new Category();
        category.setSortOrder(1);
        category = categoryRepository.saveAndFlush(category);

        Meal meal = new Meal();
        meal.setCategory(category);
        meal.setAvailable(true);

        MealTranslation en = new MealTranslation();
        en.setMeal(meal);
        en.setLanguage(Language.EN);
        en.setName("Wiener Schnitzel");
        en.setDescription("Breaded veal escalope");
        en.setPreparationMethod("Pan-fried");
        en.setIngredients(List.of("Veal", "Breadcrumbs", "Egg", "Lemon"));
        meal.getTranslations().add(en);

        MealSize small = new MealSize();
        small.setMeal(meal);
        small.setPrice(new BigDecimal("14.50"));
        MealSizeTranslation smallEn = new MealSizeTranslation();
        smallEn.setMealSize(small);
        smallEn.setLanguage(Language.EN);
        smallEn.setLabel("Small");
        small.getTranslations().add(smallEn);
        meal.getSizes().add(small);

        MealSize large = new MealSize();
        large.setMeal(meal);
        large.setPrice(new BigDecimal("18.90"));
        MealSizeTranslation largeEn = new MealSizeTranslation();
        largeEn.setMealSize(large);
        largeEn.setLanguage(Language.EN);
        largeEn.setLabel("Large");
        large.getTranslations().add(largeEn);
        meal.getSizes().add(large);

        Meal saved = mealRepository.saveAndFlush(meal);

        Optional<Meal> found = mealRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertTrue(found.get().isAvailable());
        assertEquals(1, found.get().getTranslations().size());
        assertEquals(4, found.get().getTranslations().get(0).getIngredients().size());
        assertEquals(2, found.get().getSizes().size());
        assertEquals(1, found.get().getSizes().get(0).getTranslations().size());
    }

}
