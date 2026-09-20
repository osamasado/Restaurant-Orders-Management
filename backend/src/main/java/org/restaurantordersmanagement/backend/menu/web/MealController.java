package org.restaurantordersmanagement.backend.menu.web;

import java.util.List;
import org.restaurantordersmanagement.backend.menu.service.InvalidImageException;
import org.restaurantordersmanagement.backend.menu.service.MealService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/meals")
public class MealController {

    private final MealService mealService;

    public MealController(MealService mealService) {
        this.mealService = mealService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<MealResponse> list() {
        return mealService.findAll().stream().map(MealResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MealResponse get(@PathVariable Long id) {
        return MealResponse.from(mealService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public MealResponse create(@RequestBody MealRequest request) {
        validate(request);
        return MealResponse.from(mealService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MealResponse update(@PathVariable Long id, @RequestBody MealRequest request) {
        validate(request);
        return MealResponse.from(mealService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        mealService.delete(id);
    }

    @PatchMapping("/{id}/availability")
    @PreAuthorize("hasRole('ADMIN')")
    public MealResponse setAvailability(@PathVariable Long id, @RequestBody AvailabilityRequest request) {
        return MealResponse.from(mealService.setAvailability(id, request.available()));
    }

    @PostMapping("/{id}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public MealResponse uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            return MealResponse.from(mealService.uploadImage(id, file));
        } catch (InvalidImageException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{id}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public MealResponse deleteImage(@PathVariable Long id) {
        return MealResponse.from(mealService.deleteImage(id));
    }

    private void validate(MealRequest request) {
        if (request.categoryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryId is required");
        }
        if (request.translations() == null || request.translations().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one translation is required");
        }
        if (request.sizes() == null || request.sizes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one size is required");
        }
        for (MealSizeDto size : request.sizes()) {
            if (size.price() == null || size.price().signum() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size price must be non-negative");
            }
        }
    }

}
