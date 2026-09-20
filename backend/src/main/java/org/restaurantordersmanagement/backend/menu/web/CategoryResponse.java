package org.restaurantordersmanagement.backend.menu.web;

import java.util.List;
import org.restaurantordersmanagement.backend.menu.model.Category;

public record CategoryResponse(Long id, int sortOrder, List<CategoryTranslationDto> translations) {

    public static CategoryResponse from(Category category) {
        List<CategoryTranslationDto> translations = category.getTranslations().stream()
                .map(t -> new CategoryTranslationDto(t.getLanguage(), t.getName()))
                .toList();
        return new CategoryResponse(category.getId(), category.getSortOrder(), translations);
    }

}
