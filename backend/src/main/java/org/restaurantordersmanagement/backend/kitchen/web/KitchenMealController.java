package org.restaurantordersmanagement.backend.kitchen.web;

import java.util.List;
import org.restaurantordersmanagement.backend.kitchen.service.KitchenMealService;
import org.restaurantordersmanagement.backend.menu.web.AvailabilityRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The kitchen's "ran out?" API. Needs a sign-in (SecurityConfig's default), and
 * every method carries its own @PreAuthorize for the roles.
 */
@RestController
@RequestMapping("/api/kitchen/meals")
public class KitchenMealController {

    private final KitchenMealService kitchenMealService;

    public KitchenMealController(KitchenMealService kitchenMealService) {
        this.kitchenMealService = kitchenMealService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('KITCHEN', 'ADMIN')")
    public List<KitchenMealResponse> list() {
        return kitchenMealService.listMeals();
    }

    @PatchMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('KITCHEN', 'ADMIN')")
    public KitchenMealResponse setAvailability(@PathVariable Long id, @RequestBody AvailabilityRequest request) {
        return kitchenMealService.setAvailability(id, request.available());
    }

}
