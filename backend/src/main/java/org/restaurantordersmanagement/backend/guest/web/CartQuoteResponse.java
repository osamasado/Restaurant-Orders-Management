package org.restaurantordersmanagement.backend.guest.web;

import java.math.BigDecimal;
import java.util.List;

/**
 * taxRate is a percentage (19 means 19%), same as Config.taxRate. available
 * lets the cart flag a meal the kitchen marked as ran out after it was added.
 */
public record CartQuoteResponse(
        BigDecimal subtotal, BigDecimal taxRate, BigDecimal taxAmount, BigDecimal total, List<LineQuote> lines) {

    public record LineQuote(Long sizeId, BigDecimal unitPrice, BigDecimal lineTotal, boolean available) {
    }

}
