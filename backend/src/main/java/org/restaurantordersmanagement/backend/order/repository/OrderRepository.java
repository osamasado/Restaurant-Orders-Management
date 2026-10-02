package org.restaurantordersmanagement.backend.order.repository;

import java.util.Collection;
import java.util.List;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Oldest first - the order the kitchen should cook in. Items and table
     * are fetched in the same query, so the board's DTO mapping needs no
     * extra lazy loads (and no N+1 queries).
     */
    @EntityGraph(attributePaths = {"items", "table"})
    List<Order> findByStatusInOrderByPlacedAtAsc(Collection<OrderStatus> statuses);

    /**
     * Cancelled orders the kitchen has not acknowledged yet - the banner's
     * source. placedAt IS NOT NULL leaves out drafts cancelled before the
     * kitchen ever saw them.
     */
    @EntityGraph(attributePaths = {"table"})
    List<Order> findByStatusAndPlacedAtIsNotNullAndCancellationAcknowledgedAtIsNullOrderByPlacedAtAsc(OrderStatus status);

}
