package org.restaurantordersmanagement.backend.order.web;

import org.restaurantordersmanagement.backend.order.service.AdminOrderService;
import org.restaurantordersmanagement.backend.staff.security.StaffPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin order actions. Not covered by SecurityConfig's URL rules
 * (anyRequest().permitAll()), so every method carries its own @PreAuthorize.
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminOrderResponse cancel(
            @PathVariable Long orderId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        return AdminOrderResponse.from(adminOrderService.cancel(orderId, principal.getStaffAccount().getId()));
    }

}
