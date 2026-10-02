package org.restaurantordersmanagement.backend.kitchen.web;

import org.restaurantordersmanagement.backend.order.model.Order;

/** One line of the kitchen's cancelled-order banner: which order to stop working on, and where it was going. */
public record CancelledOrderResponse(Long orderId, Integer orderNumber, String tableNumber) {

    public static CancelledOrderResponse from(Order order) {
        return new CancelledOrderResponse(order.getId(), order.getOrderNumber(), order.getTable().getTableNumber());
    }

}
