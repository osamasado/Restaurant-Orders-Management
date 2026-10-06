package org.restaurantordersmanagement.backend.order.web;

import java.time.Instant;
import java.util.List;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;

/**
 * One order and its full audit trail, for the admin history screen.
 * {@code actor} is the staff member's name; it is null when nobody on staff
 * was behind the change, which is a guest submitting their own order.
 * {@code cancellationAcknowledgement} is the kitchen's read-receipt for a
 * cancelled order (not a status change), null until someone acknowledges it.
 * The staff account itself (PIN hash and all) never leaves the server.
 */
public record OrderHistoryResponse(
        Long orderId,
        Integer orderNumber,
        String tableNumber,
        OrderStatus status,
        Instant placedAt,
        List<Entry> entries,
        Acknowledgement cancellationAcknowledgement) {

    public record Entry(OrderStatus stage, Instant changedAt, String actor) {
    }

    public record Acknowledgement(String by, Instant at) {
    }

}
