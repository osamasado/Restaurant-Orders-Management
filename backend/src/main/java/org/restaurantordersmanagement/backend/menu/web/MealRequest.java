package org.restaurantordersmanagement.backend.menu.web;

import java.util.List;

public record MealRequest(
        Long categoryId, boolean available, List<MealTranslationDto> translations, List<MealSizeDto> sizes) {
}
