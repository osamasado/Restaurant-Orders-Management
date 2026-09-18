package org.restaurantordersmanagement.backend.order.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;

/** Plain JUnit - no Spring, no Testcontainers, no Docker dependency at all. */
class OrderTransitionsTest {

    @Test
    void everyLegalEdgeIsAccepted() {
        assertTrue(OrderTransitions.isLegal(OrderStatus.DRAFT, OrderStatus.SUBMITTED));
        assertTrue(OrderTransitions.isLegal(OrderStatus.DRAFT, OrderStatus.CANCELLED));
        assertTrue(OrderTransitions.isLegal(OrderStatus.SUBMITTED, OrderStatus.PREPARING));
        assertTrue(OrderTransitions.isLegal(OrderStatus.SUBMITTED, OrderStatus.CANCELLED));
        assertTrue(OrderTransitions.isLegal(OrderStatus.PREPARING, OrderStatus.READY));
        assertTrue(OrderTransitions.isLegal(OrderStatus.PREPARING, OrderStatus.CANCELLED));
        assertTrue(OrderTransitions.isLegal(OrderStatus.READY, OrderStatus.SERVED));
        assertTrue(OrderTransitions.isLegal(OrderStatus.READY, OrderStatus.CANCELLED));
    }

    @Test
    void readyCannotGoBackToDraft() {
        // The exact illegal example the issue itself names.
        assertFalse(OrderTransitions.isLegal(OrderStatus.READY, OrderStatus.DRAFT));
    }

    @Test
    void servedOrderCannotBeCancelled() {
        // The other illegal example the issue itself names.
        assertFalse(OrderTransitions.isLegal(OrderStatus.SERVED, OrderStatus.CANCELLED));
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void terminalStatusesHaveNoLegalOutboundTransition(OrderStatus target) {
        for (OrderStatus terminal : EnumSet.of(OrderStatus.SERVED, OrderStatus.CANCELLED)) {
            assertFalse(OrderTransitions.isLegal(terminal, target));
        }
    }

    @Test
    void skippingAStageIsIllegal() {
        assertFalse(OrderTransitions.isLegal(OrderStatus.DRAFT, OrderStatus.PREPARING));
        assertFalse(OrderTransitions.isLegal(OrderStatus.SUBMITTED, OrderStatus.READY));
        assertFalse(OrderTransitions.isLegal(OrderStatus.PREPARING, OrderStatus.SERVED));
    }

    @Test
    void transitioningToTheSameStatusIsIllegal() {
        for (OrderStatus status : OrderStatus.values()) {
            assertFalse(OrderTransitions.isLegal(status, status));
        }
    }

}
