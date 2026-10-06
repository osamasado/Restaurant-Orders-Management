package org.restaurantordersmanagement.backend.order.repository;

import java.util.Collection;
import java.util.List;
import org.restaurantordersmanagement.backend.order.model.OrderStatusHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    /**
     * The history rows of a whole page of orders in one query, oldest first
     * (id breaks ties between two changes in the same instant), with the staff
     * member joined in so naming the actor costs no extra queries.
     */
    @EntityGraph(attributePaths = {"changedBy"})
    List<OrderStatusHistory> findByOrderIdInOrderByChangedAtAscIdAsc(Collection<Long> orderIds);

}
