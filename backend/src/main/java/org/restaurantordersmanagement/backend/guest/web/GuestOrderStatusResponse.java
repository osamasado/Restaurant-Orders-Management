package org.restaurantordersmanagement.backend.guest.web;

import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.settings.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GuestOrderStatusResponse(
        Long orderId,
        Integer orderNumber,
        OrderStatus status,
        Instant placedAt,
        PaymentMethod paymentMethod,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal total,
        List<HistoryEntry> history
) {
    /** Guest-safe view of one OrderStatusHistory row - deliberately no changedBy (staff). */
    public record HistoryEntry(OrderStatus status, Instant changedAt) {
    }

    public static GuestOrderStatusResponse from(Order order) {
        List<HistoryEntry> history = order.getHistory().stream()
                .map(entry -> new HistoryEntry(entry.getStatus(), entry.getChangedAt()))
                .toList();
        return new GuestOrderStatusResponse(
                order.getId(),
                order.getDisplayNumber(),
                order.getStatus(),
                order.getPlacedAt(),
                order.getPaymentMethod(),
                order.getSubtotal(),
                order.getTaxAmount(),
                order.getTotal(),
                history
        );
    }
}
