package org.restaurantordersmanagement.backend.order.repository;

import java.time.Instant;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * Building blocks for listing orders. A filter that is not set adds no
 * condition at all (instead of "? is null or ..."), because PostgreSQL cannot
 * work out the type of a null parameter in that form.
 */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    /**
     * Orders the guests actually placed (placedAt IS NOT NULL leaves out drafts,
     * and drafts cancelled before they were ever submitted), optionally only one
     * status and only those placed from (inclusive) up to (exclusive) the given
     * instants. The client decides what a "day" is, so the server needs no time zone.
     */
    public static Specification<Order> placed(OrderStatus status, Instant from, Instant to) {
        Specification<Order> specification = (root, query, builder) -> builder.isNotNull(root.get("placedAt"));
        if (status != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (from != null) {
            specification = specification.and(
                    (root, query, builder) -> builder.greaterThanOrEqualTo(root.<Instant>get("placedAt"), from));
        }
        if (to != null) {
            specification = specification.and(
                    (root, query, builder) -> builder.lessThan(root.<Instant>get("placedAt"), to));
        }
        return specification;
    }

}
