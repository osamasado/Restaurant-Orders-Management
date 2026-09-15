package org.restaurantordersmanagement.backend.menu.model;

import jakarta.persistence.*;
import org.restaurantordersmanagement.backend.i18n.Language;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"meal_size_id", "language"}))
@Getter
@Setter
@NoArgsConstructor
public class MealSizeTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "meal_size_id")
    private MealSize mealSize;

    @Enumerated(EnumType.STRING)
    private Language language;

    private String label;

}
