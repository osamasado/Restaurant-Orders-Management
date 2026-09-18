package org.restaurantordersmanagement.backend.order.service;

import java.util.Map;
import java.util.Set;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;

/**
 * The legal-transition graph, as a pure function - no Spring, no
 * persistence, deliberately independently testable from
 * {@link OrderStateMachineService}. Matches CLAUDE.md's state machine
 * exactly: SERVED and CANCELLED are terminal (absent as map keys, so
 * {@link #isLegal} returns false for any transition out of either).
 */
public final class OrderTransitions {

    private static final Map<OrderStatus, Set<OrderStatus>> LEGAL_TARGETS = Map.of(
            OrderStatus.DRAFT, Set.of(OrderStatus.SUBMITTED, OrderStatus.CANCELLED),
            OrderStatus.SUBMITTED, Set.of(OrderStatus.PREPARING, OrderStatus.CANCELLED),
            OrderStatus.PREPARING, Set.of(OrderStatus.READY, OrderStatus.CANCELLED),
            OrderStatus.READY, Set.of(OrderStatus.SERVED, OrderStatus.CANCELLED));

    private OrderTransitions() {
    }

    public static boolean isLegal(OrderStatus from, OrderStatus to) {
        return LEGAL_TARGETS.getOrDefault(from, Set.of()).contains(to);
    }

}
