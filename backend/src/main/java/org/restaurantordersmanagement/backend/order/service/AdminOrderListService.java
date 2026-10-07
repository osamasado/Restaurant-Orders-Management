package org.restaurantordersmanagement.backend.order.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderItem;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderItemRepository;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.order.repository.OrderSpecifications;
import org.restaurantordersmanagement.backend.order.web.AdminOrderPageResponse;
import org.restaurantordersmanagement.backend.order.web.AdminOrderRowResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads the orders for the admin Orders screen. Read-only, so listing can never
 * change an order. The legal next statuses come from OrderTransitions, the same
 * graph the state machine enforces.
 */
@Service
public class AdminOrderListService {

    static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public AdminOrderListService(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * Newest placed orders first, optionally only one status and only orders
     * placed from (inclusive) up to (exclusive) the given instants. Two queries for the page plus its count: the
     * orders with their table, then the lines of all of them together - never
     * one query per order.
     */
    @Transactional(readOnly = true)
    public AdminOrderPageResponse page(int page, int size, OrderStatus status, Instant from, Instant to) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        }

        if (from != null && to != null && !from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("placedAt"), Sort.Order.desc("id")));
        Page<Order> orders = orderRepository.findAll(OrderSpecifications.placed(status, from, to), pageable);

        Map<Long, List<AdminOrderRowResponse.Item>> itemsByOrder = itemsOf(orders.getContent());

        List<AdminOrderRowResponse> rows = orders.getContent().stream()
                .map(order -> toRow(order, itemsByOrder.getOrDefault(order.getId(), List.of())))
                .toList();
        return new AdminOrderPageResponse(rows, orders.getNumber(), orders.getTotalPages(), orders.getTotalElements());
    }

    private Map<Long, List<AdminOrderRowResponse.Item>> itemsOf(Collection<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = orders.stream().map(Order::getId).toList();
        Map<Long, List<AdminOrderRowResponse.Item>> grouped = new LinkedHashMap<>();
        for (OrderItem item : orderItemRepository.findByOrderIdInOrderByIdAsc(ids)) {
            grouped.computeIfAbsent(item.getOrder().getId(), id -> new ArrayList<>())
                    .add(new AdminOrderRowResponse.Item(
                            item.getName(), item.getSize(), item.getQuantity(), item.getNote()));
        }
        return grouped;
    }

    private static AdminOrderRowResponse toRow(Order order, List<AdminOrderRowResponse.Item> items) {
        return new AdminOrderRowResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getTable().getTableNumber(),
                order.getStatus(),
                order.getPlacedAt(),
                order.getPaymentMethod(),
                order.getTotal(),
                items,
                nextStatuses(order.getStatus()));
    }

    /** Every legal target except CANCELLED, in the order the order moves through them. */
    private static List<OrderStatus> nextStatuses(OrderStatus status) {
        return OrderTransitions.legalTargets(status).stream()
                .filter(target -> target != OrderStatus.CANCELLED)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

}
