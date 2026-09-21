package org.restaurantordersmanagement.backend.settings.web;

import org.restaurantordersmanagement.backend.settings.service.SettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ConfigResponse get() {
        return ConfigResponse.from(settingsService.getConfig());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ConfigResponse update(@RequestBody ConfigRequest request) {
        validate(request);
        return ConfigResponse.from(settingsService.updateConfig(request));
    }

    private void validate(ConfigRequest request) {
        if (request.currencyCode() == null || request.currencyCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currencyCode is required");
        }
        if (request.currencySymbol() == null || request.currencySymbol().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currencySymbol is required");
        }
        if (request.symbolPosition() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbolPosition is required");
        }
        if (request.taxRate() == null || request.taxRate().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taxRate must be non-negative");
        }
        if (request.defaultLanguage() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "defaultLanguage is required");
        }
        if (request.enabledPaymentMethods() == null || request.enabledPaymentMethods().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one payment method must be enabled");
        }
    }

}
