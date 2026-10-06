package org.restaurantordersmanagement.backend.order.web;

import org.restaurantordersmanagement.backend.order.model.OrderStatus;

/** The status the admin wants to move an order to - validated by the state machine, never trusted. */
public record AdminTransitionRequest(OrderStatus status) {
}
