package org.restaurantordersmanagement.backend.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.restaurantordersmanagement.backend.order.model.OrderItem;

/**
 * Pure, Spring-free pricing logic (same split as OrderTransitions vs.
 * OrderStateMachineService) so the arithmetic is unit-testable without a DB.
 */
public final class OrderPricingCalculator {

    private OrderPricingCalculator() {
    }

    /**
     * @param taxRatePercent a percentage, e.g. 19 means 19% - matches how
     *                       Config.taxRate is stored, not a fraction like 0.19.
     */
    public static OrderPricing calculate(List<OrderItem> items, BigDecimal taxRatePercent) {
        BigDecimal subtotal = items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal taxAmount = subtotal
                .multiply(taxRatePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return new OrderPricing(subtotal, taxAmount, subtotal.add(taxAmount));
    }

}
