package org.restaurantordersmanagement.backend.guest.web;

import org.restaurantordersmanagement.backend.guest.service.GuestCartQuoteService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, no @PreAuthorize - SecurityConfig's anyRequest().permitAll() already
 * allows this. A preview only: nothing is persisted, and #21's submission
 * recomputes pricing itself rather than trusting this response.
 */
@RestController
@RequestMapping("/api/guest/cart/quote")
public class GuestCartQuoteController {

    private final GuestCartQuoteService guestCartQuoteService;

    public GuestCartQuoteController(GuestCartQuoteService guestCartQuoteService) {
        this.guestCartQuoteService = guestCartQuoteService;
    }

    @PostMapping
    public CartQuoteResponse quote(@RequestBody CartQuoteRequest request) {
        return guestCartQuoteService.quote(request);
    }

}
