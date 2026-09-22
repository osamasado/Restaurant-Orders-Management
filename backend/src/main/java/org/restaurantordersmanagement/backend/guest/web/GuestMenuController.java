package org.restaurantordersmanagement.backend.guest.web;

import java.util.List;
import org.restaurantordersmanagement.backend.guest.service.GuestMenuService;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, no @PreAuthorize - SecurityConfig's anyRequest().permitAll() already allows this. */
@RestController
@RequestMapping("/api/guest/menu")
public class GuestMenuController {

    private final GuestMenuService guestMenuService;

    public GuestMenuController(GuestMenuService guestMenuService) {
        this.guestMenuService = guestMenuService;
    }

    @GetMapping
    public List<GuestCategoryResponse> getMenu(@RequestParam Language language) {
        return guestMenuService.getMenu(language);
    }

}
