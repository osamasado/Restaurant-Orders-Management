package org.restaurantordersmanagement.backend.kitchen.web;

import org.restaurantordersmanagement.backend.order.model.OrderStatus;

/** The status the kitchen wants to move an order to - validated by the state machine, never trusted. */
public record KitchenTransitionRequest(OrderStatus status) {
}
