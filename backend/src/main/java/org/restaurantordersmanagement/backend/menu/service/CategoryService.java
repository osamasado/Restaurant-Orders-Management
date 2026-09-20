package org.restaurantordersmanagement.backend.menu.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hibernate.Hibernate;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.CategoryTranslation;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.web.CategoryRequest;
import org.restaurantordersmanagement.backend.menu.web.CategoryTranslationDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * @Transactional (with spring.jpa.open-in-view=false, no request-scoped
     * session exists otherwise) so the manual initialize() calls below run in
     * the same session as the fetch - without it, the controller's later
     * CategoryResponse.from(category) would throw LazyInitializationException.
     */
    @Transactional(readOnly = true)
    public List<Category> findAll() {
        List<Category> categories = categoryRepository.findAll();
        categories.forEach(category -> Hibernate.initialize(category.getTranslations()));
        return categories;
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

    /**
     * Lets DataIntegrityViolationException (a meal still references this
     * category) propagate - the controller maps it to 409. The explicit
     * flush forces the constraint check to happen here rather than being
     * deferred to whatever later flush/commit the caller's transaction has.
     */
    public void delete(Long id) {
        categoryRepository.deleteById(id);
        categoryRepository.flush();
    }

    /**
     * Matches existing translations by language and updates them in place,
     * rather than deleting and re-inserting rows that keep the same language -
     * a blind clear()+re-add would have Hibernate flush the insert of the new
     * row before the delete of the old one, violating the
     * (category_id, language) unique constraint whenever a language is kept
     * across an update.
     */
    private void applyRequest(Category category, CategoryRequest request) {
        category.setSortOrder(request.sortOrder());

        Map<Language, CategoryTranslation> existingByLanguage =
                category.getTranslations().stream().collect(Collectors.toMap(CategoryTranslation::getLanguage, t -> t));

        List<CategoryTranslation> updated = new ArrayList<>();
        for (CategoryTranslationDto dto : request.translations()) {
            CategoryTranslation translation = existingByLanguage.get(dto.language());
            if (translation == null) {
                translation = new CategoryTranslation();
                translation.setCategory(category);
            }
            translation.setLanguage(dto.language());
            translation.setName(dto.name());
            updated.add(translation);
        }
        category.getTranslations().clear();
        category.getTranslations().addAll(updated);
    }

}
