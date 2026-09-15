package org.restaurantordersmanagement.backend.menu.model;

import jakarta.persistence.*;
import org.restaurantordersmanagement.backend.i18n.Language;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "language"}))
@Getter
@Setter
@NoArgsConstructor
public class CategoryTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    private Language language;

    private String name;

}
