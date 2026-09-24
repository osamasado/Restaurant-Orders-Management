package org.restaurantordersmanagement.backend.guest.web;

import org.restaurantordersmanagement.backend.guest.service.GuestOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public, no @PreAuthorize - the device's pairing code in the body identifies the table. */
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
        return GuestOrderResponse.from(guestOrderService.submit(request));
    }

}
