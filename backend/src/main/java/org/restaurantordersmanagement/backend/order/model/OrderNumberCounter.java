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

    /** The one row. */
    public static final long ROW_ID = 1L;

    @Id
    private Long id;

    private Integer nextValue;

    /** The displayed number the next order gets; goes back to 1 on a reset. nextValue never does. */
    private Integer nextDisplayValue;

}
