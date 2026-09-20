package org.restaurantordersmanagement.backend.menu.web;

import java.util.List;

public record CategoryRequest(int sortOrder, List<CategoryTranslationDto> translations) {
}
