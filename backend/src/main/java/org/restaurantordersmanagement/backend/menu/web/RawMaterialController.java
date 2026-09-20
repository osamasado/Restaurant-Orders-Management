package org.restaurantordersmanagement.backend.menu.web;

import java.util.List;
import org.restaurantordersmanagement.backend.menu.service.InvalidImageException;
import org.restaurantordersmanagement.backend.menu.service.RawMaterialService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/raw-materials")
public class RawMaterialController {

    private final RawMaterialService rawMaterialService;

    public RawMaterialController(RawMaterialService rawMaterialService) {
        this.rawMaterialService = rawMaterialService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<RawMaterialResponse> list() {
        return rawMaterialService.findAll().stream().map(RawMaterialResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RawMaterialResponse get(@PathVariable Long id) {
        return RawMaterialResponse.from(rawMaterialService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public RawMaterialResponse create(@RequestBody RawMaterialRequest request) {
        validate(request);
        return RawMaterialResponse.from(rawMaterialService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RawMaterialResponse update(@PathVariable Long id, @RequestBody RawMaterialRequest request) {
        validate(request);
        return RawMaterialResponse.from(rawMaterialService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        try {
            rawMaterialService.delete(id);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Raw material is used in a recipe");
        }
    }

    @PostMapping("/{id}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public RawMaterialResponse uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            return RawMaterialResponse.from(rawMaterialService.uploadImage(id, file));
        } catch (InvalidImageException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{id}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public RawMaterialResponse deleteImage(@PathVariable Long id) {
        return RawMaterialResponse.from(rawMaterialService.deleteImage(id));
    }

    private void validate(RawMaterialRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (request.unit() == null || request.unit().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unit is required");
        }
        if (request.inStockQuantity() != null && request.inStockQuantity().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "inStockQuantity must be non-negative");
        }
    }

}
