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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.restaurantordersmanagement.backend.settings.model.PaymentMethod;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
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

    /**
     * Null while the order is a DRAFT; assigned by
     * {@link org.restaurantordersmanagement.backend.order.service.OrderNumberService}
     * the moment it's submitted.
     */
    private Integer orderNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "table_id")
    private Table table;

    /**
     * No public setter - status only ever changes via {@link #recordTransition},
     * so it's structurally impossible to change it without an audit row.
     * {@link org.restaurantordersmanagement.backend.order.service.OrderStateMachineService}
     * is what additionally guards which transitions are legal before calling it.
     */
    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private OrderStatus status = OrderStatus.DRAFT;

    private Instant placedAt;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC")
    private List<OrderStatusHistory> history = new ArrayList<>();

    /**
     * The only way to change {@link #status} - mechanically changes it and
     * appends the matching audit row in the same call. Does not validate
     * legality; that's the service's job.
     */
    public void recordTransition(OrderStatus newStatus, Instant changedAt, StaffAccount changedBy) {
        this.status = newStatus;

        OrderStatusHistory entry = new OrderStatusHistory();
        entry.setOrder(this);
        entry.setStatus(newStatus);
        entry.setChangedAt(changedAt);
        entry.setChangedBy(changedBy);
        this.history.add(entry);
    }

}
