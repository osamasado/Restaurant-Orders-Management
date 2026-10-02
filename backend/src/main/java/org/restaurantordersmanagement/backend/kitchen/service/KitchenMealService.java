package org.restaurantordersmanagement.backend.kitchen.service;

import java.util.Comparator;
import java.util.List;
import org.restaurantordersmanagement.backend.kitchen.web.KitchenMealResponse;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.service.MealService;
import org.springframework.stereotype.Service;

/**
 * The kitchen's "ran out?" view of the menu. Availability itself is changed
 * by MealService.setAvailability - the very method behind the admin's toggle -
 * so both screens have exactly the same effect on the guest menu.
 */
@Service
public class KitchenMealService {

    private final MealService mealService;

    public KitchenMealService(MealService mealService) {
        this.mealService = mealService;
    }

    /** In menu order: by category, then by meal id, so the footer chips never jump around between polls. */
    public List<KitchenMealResponse> listMeals() {
        return mealService.findAll().stream()
                .sorted(Comparator.comparingInt((Meal meal) -> meal.getCategory().getSortOrder())
                        .thenComparing(Meal::getId))
                .map(KitchenMealResponse::from)
                .toList();
    }

    public KitchenMealResponse setAvailability(Long mealId, boolean available) {
        return KitchenMealResponse.from(mealService.setAvailability(mealId, available));
    }

}
