package org.restaurantordersmanagement.backend.menu.web;

import java.math.BigDecimal;

public record RecipeLineRequest(Long rawMaterialId, BigDecimal quantity) {
}
