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

}
