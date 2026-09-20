package org.restaurantordersmanagement.backend.menu.web;

import java.math.BigDecimal;

public record RawMaterialRequest(String name, String unit, BigDecimal inStockQuantity, String supplier) {
}
