package org.restaurantordersmanagement.backend.order.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.settings.model.PaymentMethod;

/**
 * One row of the admin Orders list. {@code nextStatuses} is computed by the
 * server from the state machine, so the screen never decides what is legal:
 * every legal target except CANCELLED (cancelling has its own button and
 * endpoint), empty once the order is served or cancelled.
 */
public record AdminOrderRowResponse(
        Long orderId,
        Integer orderNumber,
        String tableNumber,
        OrderStatus status,
        Instant placedAt,
        PaymentMethod paymentMethod,
        BigDecimal total,
        List<Item> items,
        List<OrderStatus> nextStatuses) {

    /** One order line - a snapshot, exactly as the guest ordered it. */
    public record Item(String name, String size, int quantity, String note) {
    }

}
