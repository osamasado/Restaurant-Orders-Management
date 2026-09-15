package org.restaurantordersmanagement.backend.menu.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class MealSize {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "meal_id")
    private Meal meal;

    private BigDecimal price;

    @OneToMany(mappedBy = "mealSize", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MealSizeTranslation> translations = new ArrayList<>();

}
