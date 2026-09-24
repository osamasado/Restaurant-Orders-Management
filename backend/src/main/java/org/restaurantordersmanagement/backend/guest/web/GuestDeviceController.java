package org.restaurantordersmanagement.backend.guest.web;

import org.restaurantordersmanagement.backend.guest.service.GuestDeviceService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, no @PreAuthorize - the pairing code itself is the credential.
 * Idempotent, so the guest app also calls it on start to re-check a stored
 * code (an admin may have unpaired the table since).
 */
@RestController
@RequestMapping("/api/guest/device")
public class GuestDeviceController {

    private final GuestDeviceService guestDeviceService;

    public GuestDeviceController(GuestDeviceService guestDeviceService) {
        this.guestDeviceService = guestDeviceService;
    }

    @PostMapping("/claim")
    public GuestTableResponse claim(@RequestBody DeviceClaimRequest request) {
        return GuestTableResponse.from(guestDeviceService.claim(request.code()));
    }

}
