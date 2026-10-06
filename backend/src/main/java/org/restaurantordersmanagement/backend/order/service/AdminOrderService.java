package org.restaurantordersmanagement.backend.order.service;

import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin-side order actions. Like the kitchen's moves, a cancellation goes
 * through OrderStateMachineService - the only place that decides what is
 * legal - and records the acting staff member in the audit trail.
 */
@Service
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final OrderStateMachineService orderStateMachineService;
    private final StaffAccountRepository staffAccountRepository;

    public AdminOrderService(OrderRepository orderRepository,
                             OrderStateMachineService orderStateMachineService,
                             StaffAccountRepository staffAccountRepository) {
        this.orderRepository = orderRepository;
        this.orderStateMachineService = orderStateMachineService;
        this.staffAccountRepository = staffAccountRepository;
    }

    /**
     * Moves an order forward (Start, Ready, Served) with the admin recorded as
     * the actor. Only the three forward targets are accepted here: CANCELLED has
     * its own endpoint, and submitting is the guest's own step (it prices the
     * order and takes its number), so neither is reachable from this one.
     * Whether the step is legal from the order's current status is the state
     * machine's call (409 when it is not).
     */
    @Transactional
    public Order advance(Long orderId, OrderStatus target, Long staffAccountId) {
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        if (target == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Use the cancel endpoint to cancel an order");
        }
        if (target == OrderStatus.DRAFT || target == OrderStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff cannot submit an order");
        }

        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order with id " + orderId + " not found"));

        StaffAccount actor = staffAccountRepository.getReferenceById(staffAccountId);

        try {
            return orderStateMachineService.transition(order, target, actor);
        } catch (IllegalOrderTransitionException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @Transactional
    public Order cancel(Long orderId, Long staffAccountId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order with id " + orderId + " not found"));

        StaffAccount actor = staffAccountRepository.getReferenceById(staffAccountId);

        try {
            return orderStateMachineService.transition(order, OrderStatus.CANCELLED, actor);
        } catch (IllegalOrderTransitionException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

}
