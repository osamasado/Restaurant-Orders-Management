package org.restaurantordersmanagement.backend.order.service;

import java.math.BigDecimal;

public record OrderPricing(BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total) {
}
