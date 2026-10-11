package org.restaurantordersmanagement.backend.guest.web;

import org.restaurantordersmanagement.backend.guest.service.GuestOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, no @PreAuthorize - the device's pairing code identifies the table
 * (in the body for submit), or the order is a take-away one (no table, no code).
 * A status read proves itself with the order's own secret (X-Order-Token, handed out
 * when the order was placed), or, for a dine-in order, with the paired device's code
 * (X-Device-Code).
 */
@RestController
@RequestMapping("/api/guest/orders")
public class GuestOrderController {

    private final GuestOrderService guestOrderService;

    public GuestOrderController(GuestOrderService guestOrderService) {
        this.guestOrderService = guestOrderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GuestOrderResponse submit(@RequestBody GuestOrderRequest request) {
        GuestOrderService.PlacedOrder placed = guestOrderService.submit(request);
        return GuestOrderResponse.from(placed.order(), placed.accessToken());
    }

    @GetMapping("/{orderId}")
    public GuestOrderStatusResponse status(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-Order-Token", required = false) String orderToken,
            @RequestHeader(value = "X-Device-Code", required = false) String deviceCode) {
        return guestOrderService.getStatus(orderId, orderToken, deviceCode);
    }

}
