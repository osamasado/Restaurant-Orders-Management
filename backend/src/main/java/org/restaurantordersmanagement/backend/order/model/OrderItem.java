package org.restaurantordersmanagement.backend.order.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.model.MealSizeTranslation;
import org.restaurantordersmanagement.backend.menu.model.MealTranslation;

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

    /**
     * Copies the meal title/size label/price for the given language, at this
     * moment, onto a new (not yet persisted/attached to an order) OrderItem.
     * Nothing here keeps referencing the Meal/MealSize afterward - a later
     * edit to either has no effect on the returned snapshot.
     */
    public static OrderItem snapshotFrom(MealSize mealSize, Language language, int quantity, String note) {
        Meal meal = mealSize.getMeal();
        String mealName =
                translationFor(meal.getTranslations(), language, MealTranslation::getLanguage, MealTranslation::getName);
        String sizeLabel = translationFor(
                mealSize.getTranslations(), language, MealSizeTranslation::getLanguage, MealSizeTranslation::getLabel);

        OrderItem item = new OrderItem();
        item.setName(mealName);
        item.setSize(sizeLabel);
        item.setQuantity(quantity);
        item.setUnitPrice(mealSize.getPrice());
        item.setNote(note);
        return item;
    }

    /**
     * Same fallback as the guest menu (requested language, then EN, then
     * whatever exists), so a guest ordering in AR a meal only named in EN
     * gets the EN name snapshotted - the name they actually saw.
     */
    private static <T> String translationFor(
            List<T> translations, Language language, Function<T, Language> languageOf, Function<T, String> valueOf) {
        return translations.stream()
                .filter(translation -> languageOf.apply(translation) == language)
                .findFirst()
                .or(() -> translations.stream().filter(translation -> languageOf.apply(translation) == Language.EN).findFirst())
                .or(() -> translations.stream().findFirst())
                .map(valueOf)
                .orElseThrow(() -> new IllegalStateException("No translations available"));
    }

}
