package org.restaurantordersmanagement.backend.order.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * name/size/unitPrice are copied from the Meal/MealSize at order time, never
 * referenced live - no FK back to Meal/MealSize at all, so a later menu edit
 * can never rewrite a historical order. See {@link #snapshotFrom}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    private String name;

    private String size;

    private int quantity;

    private BigDecimal unitPrice;

    private String note;

}
