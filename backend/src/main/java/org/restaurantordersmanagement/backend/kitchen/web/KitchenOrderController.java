package org.restaurantordersmanagement.backend.kitchen.web;

import java.util.List;
import org.restaurantordersmanagement.backend.kitchen.service.KitchenOrderService;
import org.restaurantordersmanagement.backend.staff.security.StaffPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kitchen board API. Not covered by SecurityConfig's URL rules
 * (anyRequest().permitAll()), so every method carries its own
 * @PreAuthorize - the same approach as the admin controllers.
 */
@RestController
@RequestMapping("/api/kitchen/orders")
public class KitchenOrderController {

    private final KitchenOrderService kitchenOrderService;

    public KitchenOrderController(KitchenOrderService kitchenOrderService) {
        this.kitchenOrderService = kitchenOrderService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('KITCHEN', 'ADMIN')")
    public List<KitchenOrderResponse> list() {
        return kitchenOrderService.listActive();
    }

    @GetMapping("/cancelled")
    @PreAuthorize("hasAnyRole('KITCHEN', 'ADMIN')")
    public List<CancelledOrderResponse> listCancelled() {
        return kitchenOrderService.listUnacknowledgedCancellations();
    }

    @PostMapping("/{orderId}/acknowledge-cancellation")
    @PreAuthorize("hasAnyRole('KITCHEN', 'ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acknowledgeCancellation(
            @PathVariable Long orderId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        kitchenOrderService.acknowledgeCancellation(orderId, principal.getStaffAccount().getId());
    }

    @PostMapping("/{orderId}/transition")
    @PreAuthorize("hasAnyRole('KITCHEN', 'ADMIN')")
    public KitchenOrderResponse advance(
            @PathVariable Long orderId,
            @RequestBody KitchenTransitionRequest request,
            @AuthenticationPrincipal StaffPrincipal principal) {
        return kitchenOrderService.advance(orderId, request.status(), principal.getStaffAccount().getId());
    }

}
