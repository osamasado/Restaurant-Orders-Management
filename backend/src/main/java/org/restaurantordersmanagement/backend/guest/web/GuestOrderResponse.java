package org.restaurantordersmanagement.backend.guest.web;

import java.math.BigDecimal;
import java.time.Instant;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.settings.model.PaymentMethod;

/** The authoritative result of a submission - the guest app shows these numbers, not its own preview. */
public record GuestOrderResponse(
        Long orderId,
        Integer orderNumber,
        OrderStatus status,
        Instant placedAt,
        PaymentMethod paymentMethod,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal total) {

    public static GuestOrderResponse from(Order order) {
        return new GuestOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getPlacedAt(),
                order.getPaymentMethod(),
                order.getSubtotal(),
                order.getTaxAmount(),
                order.getTotal());
    }

}
