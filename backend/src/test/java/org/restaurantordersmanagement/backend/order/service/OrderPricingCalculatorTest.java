package org.restaurantordersmanagement.backend.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.order.model.OrderItem;

/** Plain JUnit - no Spring, no Testcontainers, no Docker dependency at all. */
class OrderPricingCalculatorTest {

    private static OrderItem item(String unitPrice, int quantity) {
        OrderItem item = new OrderItem();
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setQuantity(quantity);
        return item;
    }

    @Test
    void sumsMultipleItemsAndAppliesTaxRate() {
        List<OrderItem> items = List.of(item("10.00", 2), item("5.00", 1));

        OrderPricing pricing = OrderPricingCalculator.calculate(items, new BigDecimal("20"));

        assertEquals(new BigDecimal("25.00"), pricing.subtotal());
        assertEquals(new BigDecimal("5.00"), pricing.taxAmount());
        assertEquals(new BigDecimal("30.00"), pricing.total());
    }

    @Test
    void roundsTaxAmountHalfUp() {
        // 33.33 * 15% = 4.9995, which must round up to 5.00, not truncate to 4.99.
        List<OrderItem> items = List.of(item("33.33", 1));

        OrderPricing pricing = OrderPricingCalculator.calculate(items, new BigDecimal("15"));

        assertEquals(new BigDecimal("33.33"), pricing.subtotal());
        assertEquals(new BigDecimal("5.00"), pricing.taxAmount());
        assertEquals(new BigDecimal("38.33"), pricing.total());
    }

    @Test
    void zeroTaxRateProducesZeroTax() {
        List<OrderItem> items = List.of(item("10.00", 3));

        OrderPricing pricing = OrderPricingCalculator.calculate(items, BigDecimal.ZERO);

        assertEquals(new BigDecimal("30.00"), pricing.subtotal());
        assertEquals(new BigDecimal("0.00"), pricing.taxAmount());
        assertEquals(new BigDecimal("30.00"), pricing.total());
    }

    @Test
    void emptyItemsProduceZeroEverything() {
        OrderPricing pricing = OrderPricingCalculator.calculate(List.of(), new BigDecimal("19"));

        assertEquals(new BigDecimal("0.00"), pricing.subtotal());
        assertEquals(new BigDecimal("0.00"), pricing.taxAmount());
        assertEquals(new BigDecimal("0.00"), pricing.total());
    }

}
