package org.restaurantordersmanagement.backend.guest.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import org.restaurantordersmanagement.backend.guest.web.GuestCategoryResponse;
import org.restaurantordersmanagement.backend.guest.web.GuestMealResponse;
import org.restaurantordersmanagement.backend.guest.web.GuestMealSizeResponse;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.CategoryTranslation;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.model.MealSizeTranslation;
import org.restaurantordersmanagement.backend.menu.model.MealTranslation;
import org.restaurantordersmanagement.backend.menu.service.CategoryService;
import org.restaurantordersmanagement.backend.menu.service.MealService;
import org.springframework.stereotype.Service;

/**
 * Composes the existing admin-facing CategoryService/MealService into a
 * public, single-language, available-only view - no changes to those
 * services or their admin controllers.
 */
@Service
public class GuestMenuService {

    private final CategoryService categoryService;
    private final MealService mealService;

    public GuestMenuService(CategoryService categoryService, MealService mealService) {
        this.categoryService = categoryService;
        this.mealService = mealService;
    }

    /** Categories with no available meals are omitted entirely, not shown as an empty section. */
    public List<GuestCategoryResponse> getMenu(Language language) {
        List<Category> categories = categoryService.findAll().stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .toList();
        List<Meal> availableMeals = mealService.findAll().stream().filter(Meal::isAvailable).toList();

        List<GuestCategoryResponse> menu = new ArrayList<>();
        for (Category category : categories) {
            List<GuestMealResponse> meals = availableMeals.stream()
                    .filter(meal -> meal.getCategory().getId().equals(category.getId()))
                    .map(meal -> toGuestMeal(meal, language))
                    .toList();
            if (!meals.isEmpty()) {
                CategoryTranslation translation =
                        resolveByLanguage(category.getTranslations(), language, CategoryTranslation::getLanguage);
                menu.add(new GuestCategoryResponse(category.getId(), translation.getName(), meals));
            }
        }
        return menu;
    }

    private GuestMealResponse toGuestMeal(Meal meal, Language language) {
        MealTranslation translation = resolveByLanguage(meal.getTranslations(), language, MealTranslation::getLanguage);
        List<GuestMealSizeResponse> sizes = meal.getSizes().stream()
                .map(size -> toGuestMealSize(size, language))
                .toList();
        String imageUrl = meal.getImagePath() != null ? "/images/" + meal.getImagePath() : null;

        return new GuestMealResponse(
                meal.getId(),
                meal.getCategory().getId(),
                translation.getName(),
                translation.getDescription(),
                translation.getPreparationMethod(),
                translation.getIngredients(),
                imageUrl,
                sizes);
    }

    private GuestMealSizeResponse toGuestMealSize(MealSize size, Language language) {
        MealSizeTranslation translation =
                resolveByLanguage(size.getTranslations(), language, MealSizeTranslation::getLanguage);
        return new GuestMealSizeResponse(size.getId(), size.getPrice(), translation.getLabel());
    }

    /**
     * Resolves the whole translation entity for the requested language (falling
     * back to EN, then whatever exists) rather than mapping straight to a
     * nullable field - some fields (description, preparationMethod) are
     * legitimately null for non-EN translations in the seed data, and
     * Stream.findFirst() on a stream of nulls throws when wrapped in Optional.
     */
    private <T> T resolveByLanguage(List<T> translations, Language language, Function<T, Language> languageOf) {
        return translations.stream()
                .filter(translation -> languageOf.apply(translation) == language)
                .findFirst()
                .or(() -> translations.stream().filter(translation -> languageOf.apply(translation) == Language.EN).findFirst())
                .or(() -> translations.stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("No translations available"));
    }

}
