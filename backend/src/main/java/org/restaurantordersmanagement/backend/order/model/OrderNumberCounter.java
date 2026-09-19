package org.restaurantordersmanagement.backend.order.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single-row table (id=1, seeded by V13) locked via
 * {@link org.restaurantordersmanagement.backend.order.repository.OrderNumberCounterRepository#lockById}
 * to hand out unique order numbers under concurrent order submissions.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class OrderNumberCounter {

    @Id
    private Long id;

    private Integer nextValue;

}
