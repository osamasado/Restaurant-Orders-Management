package org.restaurantordersmanagement.backend.order.repository;

import java.util.Optional;
import org.restaurantordersmanagement.backend.order.model.OrderNumberReset;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderNumberResetRepository extends JpaRepository<OrderNumberReset, Long> {

    @EntityGraph(attributePaths = {"resetBy"})
    Optional<OrderNumberReset> findFirstByOrderByResetAtDescIdDesc();

}
