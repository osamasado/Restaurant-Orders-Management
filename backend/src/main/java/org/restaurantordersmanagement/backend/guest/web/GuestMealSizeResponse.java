package org.restaurantordersmanagement.backend.guest.web;

import java.math.BigDecimal;

public record GuestMealSizeResponse(Long id, BigDecimal price, String label) {
}
