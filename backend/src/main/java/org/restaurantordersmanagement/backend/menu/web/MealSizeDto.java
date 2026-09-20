package org.restaurantordersmanagement.backend.menu.web;

import java.math.BigDecimal;
import java.util.List;

/** id is null for a new size being added on update; non-null matches an existing size to update in place. */
public record MealSizeDto(Long id, BigDecimal price, List<MealSizeTranslationDto> translations) {
}
