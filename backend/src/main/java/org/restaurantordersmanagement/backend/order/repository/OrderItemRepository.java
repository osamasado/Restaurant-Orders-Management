package org.restaurantordersmanagement.backend.order.repository;

import java.util.Collection;
import java.util.List;
import org.restaurantordersmanagement.backend.order.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * The lines of a whole page of orders in one query, in the order they were
     * added (id), so the admin list costs one query for its items instead of
     * one per order.
     */
    List<OrderItem> findByOrderIdInOrderByIdAsc(Collection<Long> orderIds);

}
