package org.restaurantordersmanagement.backend.menu.web;

import java.util.List;
import org.restaurantordersmanagement.backend.menu.model.Meal;

public record MealResponse(
        Long id,
        Long categoryId,
        boolean available,
        String imageUrl,
        List<MealTranslationDto> translations,
        List<MealSizeDto> sizes) {

    public static MealResponse from(Meal meal) {
        List<MealTranslationDto> translations = meal.getTranslations().stream()
                .map(t -> new MealTranslationDto(
                        t.getLanguage(), t.getName(), t.getDescription(), t.getPreparationMethod(), t.getIngredients()))
                .toList();

        List<MealSizeDto> sizes = meal.getSizes().stream()
                .map(size -> new MealSizeDto(
                        size.getId(),
                        size.getPrice(),
                        size.getTranslations().stream()
                                .map(t -> new MealSizeTranslationDto(t.getLanguage(), t.getLabel()))
                                .toList()))
                .toList();

        String imageUrl = meal.getImagePath() != null ? "/images/" + meal.getImagePath() : null;

        return new MealResponse(meal.getId(), meal.getCategory().getId(), meal.isAvailable(), imageUrl, translations, sizes);
    }

}
