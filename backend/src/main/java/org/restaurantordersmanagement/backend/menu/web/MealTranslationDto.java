package org.restaurantordersmanagement.backend.menu.web;

import java.util.List;
import org.restaurantordersmanagement.backend.i18n.Language;

public record MealTranslationDto(
        Language language, String name, String description, String preparationMethod, List<String> ingredients) {
}
