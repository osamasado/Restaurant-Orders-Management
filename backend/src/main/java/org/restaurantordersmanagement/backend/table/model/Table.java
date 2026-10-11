package org.restaurantordersmanagement.backend.table.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@jakarta.persistence.Table(name = "restaurant_table", uniqueConstraints = @UniqueConstraint(columnNames = "table_number"))
@Getter
@Setter
@NoArgsConstructor
public class Table {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tableNumber;

    private String room;

    private int seats;

    private String pairedDeviceId;

    private Instant lastSeenAt;

    /**
     * The one reserved table that every take-away order is placed against. It is never paired, edited, deleted or
     * listed in Tables & devices, and screens show a translated "Take away" label for it instead of its number.
     */
    private boolean takeAway;

}
