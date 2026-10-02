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
