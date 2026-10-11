package org.restaurantordersmanagement.backend.order.web;

import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;

public record AdminOrderResponse(Long orderId, Integer orderNumber, OrderStatus status) {

    public static AdminOrderResponse from(Order order) {
        return new AdminOrderResponse(order.getId(), order.getDisplayNumber(), order.getStatus());
    }

}
