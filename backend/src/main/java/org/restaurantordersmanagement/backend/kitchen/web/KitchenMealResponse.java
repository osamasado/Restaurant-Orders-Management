package org.restaurantordersmanagement.backend.kitchen.web;

import java.util.List;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Meal;

/**
 * One chip of the kitchen's "ran out?" footer. Carries every translated name
 * so the screen can show it in whichever language the staff member picked,
 * and nothing else - the kitchen does not get the admin's full meal data.
 */
public record KitchenMealResponse(Long id, boolean available, List<Name> names) {

    public record Name(Language language, String name) {
    }

    public static KitchenMealResponse from(Meal meal) {
        List<Name> names = meal.getTranslations().stream()
                .map(translation -> new Name(translation.getLanguage(), translation.getName()))
                .toList();
        return new KitchenMealResponse(meal.getId(), meal.isAvailable(), names);
    }

}
