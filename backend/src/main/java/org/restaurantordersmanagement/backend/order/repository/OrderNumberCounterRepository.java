package org.restaurantordersmanagement.backend.order.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.restaurantordersmanagement.backend.order.model.OrderNumberCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderNumberCounterRepository extends JpaRepository<OrderNumberCounter, Long> {

    /**
     * Emits SELECT ... FOR UPDATE on the counter row, so concurrent callers
     * serialize on it instead of racing an in-memory counter.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OrderNumberCounter c where c.id = :id")
    Optional<OrderNumberCounter> lockById(Long id);

}
