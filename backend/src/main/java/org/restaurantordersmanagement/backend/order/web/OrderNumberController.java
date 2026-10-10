package org.restaurantordersmanagement.backend.order.web;

import org.restaurantordersmanagement.backend.order.service.OrderNumberResetService;
import org.restaurantordersmanagement.backend.staff.security.StaffPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The displayed order number, from the admin's Settings page. Admin only. */
@RestController
@RequestMapping("/api/settings/order-number")
public class OrderNumberController {

    private final OrderNumberResetService resetService;

    public OrderNumberController(OrderNumberResetService resetService) {
        this.resetService = resetService;
    }

    /** The next number, how many orders are open (a reset is refused while there are any) and the last reset. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public OrderNumberStatusResponse status() {
        return resetService.status();
    }

    /** Restarts the displayed number at 001 (409 while any order is open); recorded with the admin and the time. */
    @PostMapping("/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderNumberStatusResponse reset(@AuthenticationPrincipal StaffPrincipal principal) {
        return resetService.reset(principal.getStaffAccount().getId());
    }

}
