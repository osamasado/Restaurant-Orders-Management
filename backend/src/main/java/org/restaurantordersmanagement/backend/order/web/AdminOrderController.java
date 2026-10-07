package org.restaurantordersmanagement.backend.order.web;

import org.restaurantordersmanagement.backend.order.service.AdminOrderListService;
import org.restaurantordersmanagement.backend.order.service.AdminOrderService;
import org.restaurantordersmanagement.backend.order.service.OrderHistoryService;
import org.restaurantordersmanagement.backend.staff.security.StaffPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final AdminOrderListService adminOrderListService;

    public AdminOrderController(
            AdminOrderService adminOrderService,
            OrderHistoryService orderHistoryService,
            AdminOrderListService adminOrderListService) {
        this.adminOrderService = adminOrderService;
        this.orderHistoryService = orderHistoryService;
        this.adminOrderListService = adminOrderListService;
    }

    /**
     * The live Orders list: placed orders, newest first, with items, payment,
     * total and the legal next statuses (computed by the server).
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminOrderPageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminOrderListService.page(page, size);
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

    /** Start, Ready or Served, with the admin recorded as the actor. Cancel has its own endpoint. */
    @PostMapping("/{orderId}/transition")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminOrderResponse advance(
            @PathVariable Long orderId,
            @RequestBody AdminTransitionRequest request,
            @AuthenticationPrincipal StaffPrincipal principal) {
        return AdminOrderResponse.from(
                adminOrderService.advance(orderId, request.status(), principal.getStaffAccount().getId()));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminOrderResponse cancel(
            @PathVariable Long orderId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        return AdminOrderResponse.from(adminOrderService.cancel(orderId, principal.getStaffAccount().getId()));
    }

}
