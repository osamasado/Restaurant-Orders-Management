package org.restaurantordersmanagement.backend.order.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.restaurantordersmanagement.backend.settings.model.PaymentMethod;
import org.restaurantordersmanagement.backend.table.model.Table;

/**
 * Mapped to "restaurant_order", not "order" - ORDER is a reserved SQL
 * keyword, same problem already solved once for the Table entity. The
 * @jakarta.persistence.Table annotation is used fully-qualified rather than
 * imported, since this file also needs the domain Table entity (the table
 * this order belongs to) - the same collision-avoidance trick the Table
 * entity itself uses, just from the other direction.
 */
@Entity
@jakarta.persistence.Table(name = "restaurant_order")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Assigned by issue #11's concurrency-safe generator - null until then. */
    private Integer orderNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "table_id")
    private Table table;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.DRAFT;

    private Instant placedAt;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC")
    private List<OrderStatusHistory> history = new ArrayList<>();

}
