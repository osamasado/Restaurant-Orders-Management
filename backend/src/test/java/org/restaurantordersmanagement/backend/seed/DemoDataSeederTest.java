package org.restaurantordersmanagement.backend.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.model.MealTranslation;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.repository.MealRepository;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Transactional so the seeded rows are rolled back afterwards - the test
 * DB is shared across test classes, and leftover demo tables ("1", "2",
 * "9", ...) collided with tables other tests create, depending on run order.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DemoDataSeederTest {

    @Autowired
    private DemoDataSeeder demoDataSeeder;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Test
    void seedAllProducesExpectedCountsAndIsIdempotent() {
        demoDataSeeder.seedAll();

        assertCounts();

        // Running it again must not duplicate anything.
        demoDataSeeder.seedAll();

        assertCounts();
    }

    @Test
    void everyMealHasFullContentInAllThreeLanguages() {
        demoDataSeeder.seedAll();

        for (Meal meal : mealRepository.findAll()) {
            int ingredientCount = translation(meal, Language.EN).getIngredients().size();
            for (Language language : Language.values()) {
                MealTranslation translation = translation(meal, language);
                String label = translation.getName() + " / " + language;
                assertFalse(translation.getName().isBlank(), label + " name");
                assertFalse(translation.getDescription() == null || translation.getDescription().isBlank(), label + " description");
                assertFalse(translation.getPreparationMethod() == null || translation.getPreparationMethod().isBlank(), label + " preparation");
                assertEquals(ingredientCount, translation.getIngredients().size(), label + " ingredient count");
            }
            for (MealSize size : meal.getSizes()) {
                assertEquals(Language.values().length, size.getTranslations().size());
            }
        }
    }

    @Test
    void fillsEmptyGermanAndArabicContentOnAnAlreadySeededMenuWithoutOverwritingEdits() {
        demoDataSeeder.seedAll();
        List<Meal> meals = mealRepository.findAll();
        Meal emptied = meals.get(0);
        Meal edited = meals.get(1);

        // A menu seeded before DE/AR content existed: names only.
        for (Language language : List.of(Language.DE, Language.AR)) {
            MealTranslation translation = translation(emptied, language);
            translation.setDescription(null);
            translation.setPreparationMethod(null);
            translation.setIngredients(new ArrayList<>());
        }
        // An admin has already written their own German description.
        translation(edited, Language.DE).setDescription("Eigener Text vom Admin");
        mealRepository.saveAllAndFlush(List.of(emptied, edited));

        demoDataSeeder.seedAll();

        for (Language language : List.of(Language.DE, Language.AR)) {
            MealTranslation refilled = translation(emptied, language);
            assertFalse(refilled.getDescription() == null || refilled.getDescription().isBlank(), language + " description");
            assertFalse(refilled.getIngredients().isEmpty(), language + " ingredients");
        }
        assertEquals("Eigener Text vom Admin", translation(edited, Language.DE).getDescription());
        assertTrue(mealRepository.count() == 8);
    }

    private static MealTranslation translation(Meal meal, Language language) {
        return meal.getTranslations().stream()
                .filter(translation -> translation.getLanguage() == language)
                .findFirst()
                .orElseThrow();
    }

    private void assertCounts() {
        assertEquals(4, categoryRepository.count());
        assertEquals(8, mealRepository.count());
        assertEquals(6, tableRepository.count());
        assertEquals(4, staffAccountRepository.count());
    }

}
