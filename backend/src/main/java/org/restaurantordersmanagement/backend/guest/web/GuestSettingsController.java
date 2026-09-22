package org.restaurantordersmanagement.backend.guest.web;

import org.restaurantordersmanagement.backend.settings.service.SettingsService;
import org.restaurantordersmanagement.backend.settings.web.ConfigResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, no @PreAuthorize - nothing in Config is sensitive (currency/tax/
 * language/payment methods), and #20/#21's cart/checkout will need this same
 * data, so this one small endpoint covers this issue and those without
 * repeating the work.
 */
@RestController
@RequestMapping("/api/guest/settings")
public class GuestSettingsController {

    private final SettingsService settingsService;

    public GuestSettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ConfigResponse get() {
        return ConfigResponse.from(settingsService.getConfig());
    }

}
