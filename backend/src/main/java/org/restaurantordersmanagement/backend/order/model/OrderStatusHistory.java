package org.restaurantordersmanagement.backend.order.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;

/**
 * One row per status the order has reached - {@code status} is the status
 * reached, not a from/to pair; the previous state is just the prior row in
 * {@link Order#getHistory()}. {@code changedBy} is nullable: a guest
 * submitting their own draft has no staff member behind the transition.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Instant changedAt;

    @ManyToOne
    @JoinColumn(name = "staff_account_id")
    private StaffAccount changedBy;

}
