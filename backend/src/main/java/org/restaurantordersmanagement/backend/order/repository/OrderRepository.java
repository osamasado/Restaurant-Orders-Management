package org.restaurantordersmanagement.backend.order.repository;

import java.util.Collection;
import java.util.List;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

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

    /**
     * A page of orders for the admin audit view. The table and whoever
     * acknowledged a cancellation are fetched in the same query. The history is
     * deliberately not fetched here: joining a collection into a paged query
     * makes Hibernate page in memory, so it is loaded separately for the whole
     * page (see OrderStatusHistoryRepository).
     */
    @EntityGraph(attributePaths = {"table", "cancellationAcknowledgedBy"})
    Page<Order> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"table", "cancellationAcknowledgedBy"})
    Page<Order> findByDisplayNumber(Integer displayNumber, Pageable pageable);

    /** How many orders are in these statuses (for the reset of the displayed number: the open ones). */
    long countByStatusIn(java.util.Collection<OrderStatus> statuses);

    /**
     * The admin Orders list: see {@link OrderSpecifications#placed}. Only the
     * table is fetched here; the items are loaded for the whole page in one
     * query (see OrderItemRepository), since joining a collection into a paged
     * query would page in memory.
     */
    @Override
    @EntityGraph(attributePaths = {"table"})
    Page<Order> findAll(Specification<Order> specification, Pageable pageable);

    /**
     * Only the displayed order number and table number of one status, oldest
     * first (by the internal number, which never repeats or restarts) - the
     * hall board's source. A closed projection, so just those two columns are selected: items, prices and everything else on the order
     * are never even loaded.
     */
    @Query("select o.displayNumber as orderNumber, o.table.tableNumber as tableNumber "
            + "from Order o where o.status = :status order by o.orderNumber")
    List<HallBoardRow> findHallBoardRows(@Param("status") OrderStatus status);

    interface HallBoardRow {

        Integer getOrderNumber();

        String getTableNumber();

    }

}
