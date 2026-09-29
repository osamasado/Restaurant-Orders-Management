package org.restaurantordersmanagement.backend.kitchen.web;

import java.time.Instant;
import java.util.List;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.service.OrderTransitions;

public record KitchenOrderResponse(
        Long orderId,
        Integer orderNumber,
        String tableNumber,
        OrderStatus status,
        Instant placedAt,
        List<Item> items,
        OrderStatus nextStatus) {

    /** One line on the card - a snapshot, exactly as the guest ordered it.*/
    public record Item(String name, String size, int quantity, String note) {
    }

    public static KitchenOrderResponse from(Order order) {
        List<Item> items = order.getItems().stream()
                .map(item -> new Item(item.getName(), item.getSize(), item.getQuantity(), item.getNote()))
                .toList();
        return new KitchenOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getTable().getTableNumber(),
                order.getStatus(),
                order.getPlacedAt(),
                items,
                kitchenNextStatus(order.getStatus()));
    }

    /**
     * The kitchen's one forward step: every legal target except CANCELLED
     * (cancelling is admin-only). SUBMITTED -> PREPARING, PREPARING -> READY,
     * READY -> SERVED; null once nothing is left for the kitchen to do.
     */
    private static OrderStatus kitchenNextStatus(OrderStatus status) {
        return OrderTransitions.legalTargets(status).stream()
                .filter(target -> target != OrderStatus.CANCELLED)
                .findFirst()
                .orElse(null);
    }

}
