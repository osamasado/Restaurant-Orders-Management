package org.restaurantordersmanagement.backend.menu.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.restaurantordersmanagement.backend.i18n.Language;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"meal_id", "language"}))
@Getter
@Setter
@NoArgsConstructor
public class MealTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "meal_id")
    private Meal meal;

    @Enumerated(EnumType.STRING)
    private Language language;

    private String name;

    private String description;

    private String preparationMethod;

    @ElementCollection
    @CollectionTable(
            name = "meal_translation_ingredient",
            joinColumns = @JoinColumn(name = "meal_translation_id"))
    @OrderColumn(name = "position")
    @Column(name = "ingredient")
    private List<String> ingredients = new ArrayList<>();

}
