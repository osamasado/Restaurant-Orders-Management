package org.restaurantordersmanagement.backend.menu.web;

import java.util.List;
import org.restaurantordersmanagement.backend.menu.service.CategoryService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<CategoryResponse> list() {
        return categoryService.findAll().stream().map(CategoryResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse create(@RequestBody CategoryRequest request) {
        validate(request);
        return CategoryResponse.from(categoryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse update(@PathVariable Long id, @RequestBody CategoryRequest request) {
        validate(request);
        return CategoryResponse.from(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        try {
            categoryService.delete(id);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category has meals assigned");
        }
    }

    private void validate(CategoryRequest request) {
        if (request.translations() == null || request.translations().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one translation is required");
        }
    }

}
