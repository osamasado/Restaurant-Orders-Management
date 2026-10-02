package org.restaurantordersmanagement.backend.kitchen.service;

import java.time.Instant;
import java.util.List;
import org.restaurantordersmanagement.backend.kitchen.web.CancelledOrderResponse;
import org.restaurantordersmanagement.backend.kitchen.web.KitchenOrderResponse;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.order.service.IllegalOrderTransitionException;
import org.restaurantordersmanagement.backend.order.service.OrderStateMachineService;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The kitchen board's view of orders and its one-step-forward actions.
 * Every move goes through OrderStateMachineService, the single place that
 * decides what is legal, and records the acting staff member.
 */
@Service
public class KitchenOrderService {

    /** The three columns of the board: New, In preparation, Ready. */
    private static final List<OrderStatus> ACTIVE_STATUSES =
            List.of(OrderStatus.SUBMITTED, OrderStatus.PREPARING, OrderStatus.READY);

    private final OrderRepository orderRepository;
    private final OrderStateMachineService orderStateMachineService;
    private final StaffAccountRepository staffAccountRepository;

    public KitchenOrderService(OrderRepository orderRepository,
                               OrderStateMachineService orderStateMachineService,
                               StaffAccountRepository staffAccountRepository) {
        this.orderRepository = orderRepository;
        this.orderStateMachineService = orderStateMachineService;
        this.staffAccountRepository = staffAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<KitchenOrderResponse> listActive() {
        return orderRepository.findByStatusInOrderByPlacedAtAsc(ACTIVE_STATUSES).stream()
                .map(KitchenOrderResponse::from)
                .toList();
    }

    /** Cancelled orders nobody in the kitchen has acknowledged yet, oldest first. */
    @Transactional(readOnly = true)
    public List<CancelledOrderResponse> listUnacknowledgedCancellations() {
        return orderRepository
                .findByStatusAndPlacedAtIsNotNullAndCancellationAcknowledgedAtIsNullOrderByPlacedAtAsc(
                        OrderStatus.CANCELLED)
                .stream()
                .map(CancelledOrderResponse::from)
                .toList();
    }

    /**
     * Records that the kitchen has seen a cancellation. Only the read-receipt
     * fields change, never the status. Acknowledging twice keeps the first
     * receipt, so two screens pressing the button at once is harmless.
     */
    @Transactional
    public void acknowledgeCancellation(Long orderId, Long staffAccountId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order with id " + orderId + " not found"));

        if (order.getStatus() != OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order with id " + orderId + " is not cancelled");
        }

        if (order.getCancellationAcknowledgedAt() != null) {
            return;
        }

        order.setCancellationAcknowledgedAt(Instant.now());
        order.setCancellationAcknowledgedBy(staffAccountRepository.getReferenceById(staffAccountId));
        orderRepository.saveAndFlush(order);
    }

    @Transactional
    public KitchenOrderResponse advance(Long orderId, OrderStatus target, Long staffAccountId) {
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }

        if (target == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Kitchen cannot cancel orders");
        }

        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order with id " + orderId + " not found"));

        StaffAccount actor = staffAccountRepository.getReferenceById(staffAccountId);

        try {
            order = orderStateMachineService.transition(order, target, actor);
        } catch (IllegalOrderTransitionException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }

        return KitchenOrderResponse.from(order);
    }

}
