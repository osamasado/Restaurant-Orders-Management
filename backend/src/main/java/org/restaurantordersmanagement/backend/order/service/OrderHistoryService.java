package org.restaurantordersmanagement.backend.order.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderStatusHistory;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.order.repository.OrderStatusHistoryRepository;
import org.restaurantordersmanagement.backend.order.web.OrderHistoryPageResponse;
import org.restaurantordersmanagement.backend.order.web.OrderHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads the audit trail that OrderStateMachineService writes: for each order,
 * every status it reached, when, and who made the change. Read-only, so it can
 * never alter the trail it reports.
 */
@Service
public class OrderHistoryService {

    static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;

    public OrderHistoryService(OrderRepository orderRepository, OrderStatusHistoryRepository historyRepository) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
    }

    /**
     * Newest orders first. Two queries for the page plus its count: the orders,
     * then all of their history rows together - never one query per order.
     *
     * @param orderNumber when set, only the order with that number
     */
    @Transactional(readOnly = true)
    public OrderHistoryPageResponse page(int page, int size, Integer orderNumber) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        }

        Pageable pageable = PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("placedAt").nullsLast(), Sort.Order.desc("id")));
        Page<Order> orders = orderNumber == null
                ? orderRepository.findAllBy(pageable)
                : orderRepository.findByOrderNumber(orderNumber, pageable);

        Map<Long, List<OrderStatusHistory>> historyByOrder = historyOf(orders.getContent());

        List<OrderHistoryResponse> responses = orders.getContent().stream()
                .map(order -> toResponse(order, historyByOrder.getOrDefault(order.getId(), List.of())))
                .toList();
        return new OrderHistoryPageResponse(responses, orders.getNumber(), orders.getTotalPages(), orders.getTotalElements());
    }

    private Map<Long, List<OrderStatusHistory>> historyOf(Collection<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderStatusHistory>> grouped = new LinkedHashMap<>();
        for (OrderStatusHistory row : historyRepository.findByOrderIdInOrderByChangedAtAscIdAsc(ids)) {
            grouped.computeIfAbsent(row.getOrder().getId(), id -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private static OrderHistoryResponse toResponse(Order order, List<OrderStatusHistory> rows) {
        List<OrderHistoryResponse.Entry> entries = rows.stream()
                .map(row -> new OrderHistoryResponse.Entry(
                        row.getStatus(), row.getChangedAt(), row.getChangedBy() == null ? null : row.getChangedBy().getName()))
                .collect(Collectors.toList());

        OrderHistoryResponse.Acknowledgement acknowledgement = order.getCancellationAcknowledgedAt() == null
                ? null
                : new OrderHistoryResponse.Acknowledgement(
                        order.getCancellationAcknowledgedBy() == null ? null : order.getCancellationAcknowledgedBy().getName(),
                        order.getCancellationAcknowledgedAt());

        return new OrderHistoryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getTable().getTableNumber(),
                order.getStatus(),
                order.getPlacedAt(),
                entries,
                acknowledgement);
    }

}
