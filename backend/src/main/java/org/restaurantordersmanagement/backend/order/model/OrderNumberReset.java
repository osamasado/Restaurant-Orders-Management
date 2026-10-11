package org.restaurantordersmanagement.backend.order.model;

import jakarta.persistence.Entity;
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

/** One row per reset of the displayed order number: who did it, when, and where the series stood. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class OrderNumberReset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant resetAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "staff_account_id")
    private StaffAccount resetBy;

    /** The displayed number the next order would have got had the series not been reset. */
    @jakarta.persistence.Column(name = "previous_next_display")
    private Integer previousNextDisplay;

}
