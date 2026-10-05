package org.restaurantordersmanagement.backend.order.web;

import org.restaurantordersmanagement.backend.order.service.AdminOrderService;
import org.restaurantordersmanagement.backend.order.service.OrderHistoryService;
import org.restaurantordersmanagement.backend.staff.security.StaffPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin order actions. Needs a sign-in (SecurityConfig's default), and every
 * method carries its own @PreAuthorize for the roles.
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;
    private final OrderHistoryService orderHistoryService;

    public AdminOrderController(AdminOrderService adminOrderService, OrderHistoryService orderHistoryService) {
        this.adminOrderService = adminOrderService;
        this.orderHistoryService = orderHistoryService;
    }

    /** The audit trail: every order, newest first, with every status change, its time and who made it. */
    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderHistoryPageResponse history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer orderNumber) {
        return orderHistoryService.page(page, size, orderNumber);
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminOrderResponse cancel(
            @PathVariable Long orderId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        return AdminOrderResponse.from(adminOrderService.cancel(orderId, principal.getStaffAccount().getId()));
    }

}
