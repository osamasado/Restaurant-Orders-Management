package org.restaurantordersmanagement.backend.order.service;

import java.time.Instant;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.springframework.stereotype.Service;

/**
 * The single place order.status is allowed to change. Validates legality via
 * {@link OrderTransitions}, then delegates the actual mutation + audit entry
 * to {@link Order#recordTransition}.
 *
 * Authorization (which role may trigger which transition, e.g. "cancel is
 * admin-only") is deliberately out of scope here - that's issue #29's job.
 */
@Service
public class OrderStateMachineService {

    private final OrderRepository orderRepository;

    public OrderStateMachineService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * @param actor the staff member who triggered this transition, or null for a
     *              guest-triggered one (e.g. submitting their own draft)
     */
    public Order transition(Order order, OrderStatus newStatus, StaffAccount actor) {
        OrderStatus currentStatus = order.getStatus();
        if (!OrderTransitions.isLegal(currentStatus, newStatus)) {
            throw new IllegalOrderTransitionException(order.getId(), currentStatus, newStatus);
        }

        Instant now = Instant.now();
        if (newStatus == OrderStatus.SUBMITTED) {
            order.setPlacedAt(now);
        }
        order.recordTransition(newStatus, now, actor);

        return orderRepository.saveAndFlush(order);
    }

}
