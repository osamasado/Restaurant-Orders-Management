package org.restaurantordersmanagement.backend.order.service;

import java.time.Instant;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final OrderNumberService orderNumberService;
    private final OrderPricingService orderPricingService;

    public OrderStateMachineService(
            OrderRepository orderRepository,
            OrderNumberService orderNumberService,
            OrderPricingService orderPricingService) {
        this.orderRepository = orderRepository;
        this.orderNumberService = orderNumberService;
        this.orderPricingService = orderPricingService;
    }

    /**
     * @Transactional so that, for the DRAFT -&gt; SUBMITTED transition, the
     * order-number counter's row lock (see OrderNumberService) and this
     * order's save happen as one atomic unit.
     *
     * @param actor the staff member who triggered this transition, or null for a
     *              guest-triggered one (e.g. submitting their own draft)
     */
    @Transactional
    public Order transition(Order order, OrderStatus newStatus, StaffAccount actor) {
        OrderStatus currentStatus = order.getStatus();
        if (!OrderTransitions.isLegal(currentStatus, newStatus)) {
            throw new IllegalOrderTransitionException(order.getId(), currentStatus, newStatus);
        }

        Instant now = Instant.now();
        if (newStatus == OrderStatus.SUBMITTED) {
            order.setPlacedAt(now);
            order.setOrderNumber(orderNumberService.assignNextOrderNumber());
            orderPricingService.applyPricing(order);
        }
        order.recordTransition(newStatus, now, actor);

        return orderRepository.saveAndFlush(order);
    }

}
