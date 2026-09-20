package org.restaurantordersmanagement.backend.menu.service;

import java.util.List;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.CategoryTranslation;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.web.CategoryRequest;
import org.restaurantordersmanagement.backend.menu.web.CategoryTranslationDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    @Transactional
    public Category create(CategoryRequest request) {
        Category category = new Category();
        applyRequest(category, request);
        return categoryRepository.saveAndFlush(category);
    }

    /**
     * @Transactional so the category stays managed across the clear+rebuild
     * of its translations - orphanRemoval only reliably deletes the removed
     * rows when that happens inside one persistence context, not via a
     * detached-entity merge.
     */
    @Transactional
    public Category update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        applyRequest(category, request);
        return categoryRepository.saveAndFlush(category);
    }

    /** Lets DataIntegrityViolationException (a meal still references this category) propagate - the controller maps it to 409. */
    public void delete(Long id) {
        categoryRepository.deleteById(id);
    }

    private void applyRequest(Category category, CategoryRequest request) {
        category.setSortOrder(request.sortOrder());
        category.getTranslations().clear();
        for (CategoryTranslationDto dto : request.translations()) {
            CategoryTranslation translation = new CategoryTranslation();
            translation.setCategory(category);
            translation.setLanguage(dto.language());
            translation.setName(dto.name());
            category.getTranslations().add(translation);
        }
    }

}
