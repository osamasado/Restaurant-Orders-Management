package org.restaurantordersmanagement.backend.order.service;

import org.restaurantordersmanagement.backend.order.model.OrderStatus;

public class IllegalOrderTransitionException extends RuntimeException {

    public IllegalOrderTransitionException(Long orderId, OrderStatus from, OrderStatus to) {
        super("Cannot transition order " + orderId + " from " + from + " to " + to);
    }

}
