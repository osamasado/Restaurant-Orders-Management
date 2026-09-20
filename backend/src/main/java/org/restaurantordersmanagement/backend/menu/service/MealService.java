package org.restaurantordersmanagement.backend.menu.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hibernate.Hibernate;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.model.MealSizeTranslation;
import org.restaurantordersmanagement.backend.menu.model.MealTranslation;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.repository.MealRepository;
import org.restaurantordersmanagement.backend.menu.web.MealRequest;
import org.restaurantordersmanagement.backend.menu.web.MealSizeDto;
import org.restaurantordersmanagement.backend.menu.web.MealSizeTranslationDto;
import org.restaurantordersmanagement.backend.menu.web.MealTranslationDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MealService {

    private final MealRepository mealRepository;
    private final CategoryRepository categoryRepository;
    private final ImageStorageService imageStorageService;

    public MealService(
            MealRepository mealRepository, CategoryRepository categoryRepository, ImageStorageService imageStorageService) {
        this.mealRepository = mealRepository;
        this.categoryRepository = categoryRepository;
        this.imageStorageService = imageStorageService;
    }

    /**
     * @Transactional (with spring.jpa.open-in-view=false, no request-scoped
     * session exists otherwise) so the manual initialize() calls below run in
     * the same session as the fetch - without it, the controller's later
     * MealResponse.from(meal) would throw LazyInitializationException.
     */
    @Transactional(readOnly = true)
    public List<Meal> findAll() {
        List<Meal> meals = mealRepository.findAll();
        meals.forEach(this::initializeCollections);
        return meals;
    }

    @Transactional(readOnly = true)
    public Meal findById(Long id) {
        Meal meal = mealRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found"));
        initializeCollections(meal);
        return meal;
    }

    @Transactional
    public Meal create(MealRequest request) {
        Meal meal = new Meal();
        applyRequest(meal, request);
        return mealRepository.saveAndFlush(meal);
    }

    /** @Transactional for the same reason as CategoryService.update - orphanRemoval needs one persistence context throughout. */
    @Transactional
    public Meal update(Long id, MealRequest request) {
        Meal meal = findById(id);
        applyRequest(meal, request);
        return mealRepository.saveAndFlush(meal);
    }

    @Transactional
    public Meal setAvailability(Long id, boolean available) {
        Meal meal = findById(id);
        meal.setAvailable(available);
        return mealRepository.saveAndFlush(meal);
    }

    public void delete(Long id) {
        Meal meal = findById(id);
        imageStorageService.delete(meal.getImagePath());
        mealRepository.delete(meal);
    }

    /** Stores the new image before deleting the old one, so a failed upload never leaves the meal without any image. */
    @Transactional
    public Meal uploadImage(Long id, MultipartFile file) {
        Meal meal = findById(id);
        String previousPath = meal.getImagePath();
        meal.setImagePath(imageStorageService.store(file, "meals"));
        Meal saved = mealRepository.saveAndFlush(meal);
        imageStorageService.delete(previousPath);
        return saved;
    }

    @Transactional
    public Meal deleteImage(Long id) {
        Meal meal = findById(id);
        imageStorageService.delete(meal.getImagePath());
        meal.setImagePath(null);
        return mealRepository.saveAndFlush(meal);
    }

    private void initializeCollections(Meal meal) {
        Hibernate.initialize(meal.getTranslations());
        meal.getTranslations().forEach(translation -> Hibernate.initialize(translation.getIngredients()));
        Hibernate.initialize(meal.getSizes());
        meal.getSizes().forEach(size -> Hibernate.initialize(size.getTranslations()));
    }

    /** Every update fully replaces the translations and sizes collections - see the plan's "mapping strategy" note. */
    private void applyRequest(Meal meal, MealRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found"));
        meal.setCategory(category);
        meal.setAvailable(request.available());

        replaceTranslations(meal, request.translations());
        replaceSizes(meal, request.sizes());
    }

    /**
     * Matches existing translations by language and updates them in place,
     * rather than deleting and re-inserting rows that keep the same language -
     * a blind clear()+re-add would have Hibernate flush the insert of the new
     * row before the delete of the old one, violating the (meal_id, language)
     * unique constraint whenever a language is kept across an update.
     */
    private void replaceTranslations(Meal meal, List<MealTranslationDto> dtos) {
        Map<Language, MealTranslation> existingByLanguage =
                meal.getTranslations().stream().collect(Collectors.toMap(MealTranslation::getLanguage, t -> t));

        List<MealTranslation> updated = new ArrayList<>();
        for (MealTranslationDto dto : dtos) {
            MealTranslation translation = existingByLanguage.get(dto.language());
            if (translation == null) {
                translation = new MealTranslation();
                translation.setMeal(meal);
            }
            translation.setLanguage(dto.language());
            translation.setName(dto.name());
            translation.setDescription(dto.description());
            translation.setPreparationMethod(dto.preparationMethod());
            translation.getIngredients().clear();
            if (dto.ingredients() != null) {
                translation.getIngredients().addAll(dto.ingredients());
            }
            updated.add(translation);
        }
        meal.getTranslations().clear();
        meal.getTranslations().addAll(updated);
    }

    private void replaceSizes(Meal meal, List<MealSizeDto> dtos) {
        Map<Long, MealSize> existingById = meal.getSizes().stream()
                .filter(size -> size.getId() != null)
                .collect(Collectors.toMap(MealSize::getId, size -> size));

        List<MealSize> updatedSizes = new ArrayList<>();
        for (MealSizeDto dto : dtos) {
            MealSize size = dto.id() != null ? existingById.get(dto.id()) : null;
            if (size == null) {
                size = new MealSize();
                size.setMeal(meal);
            }
            size.setPrice(dto.price());
            replaceSizeTranslations(size, dto.translations());
            updatedSizes.add(size);
        }
        meal.getSizes().clear();
        meal.getSizes().addAll(updatedSizes);
    }

    /** Same match-by-language-in-place strategy as replaceTranslations, and for the same reason. */
    private void replaceSizeTranslations(MealSize size, List<MealSizeTranslationDto> dtos) {
        Map<Language, MealSizeTranslation> existingByLanguage =
                size.getTranslations().stream().collect(Collectors.toMap(MealSizeTranslation::getLanguage, t -> t));

        List<MealSizeTranslation> updated = new ArrayList<>();
        for (MealSizeTranslationDto dto : dtos) {
            MealSizeTranslation translation = existingByLanguage.get(dto.language());
            if (translation == null) {
                translation = new MealSizeTranslation();
                translation.setMealSize(size);
            }
            translation.setLanguage(dto.language());
            translation.setLabel(dto.label());
            updated.add(translation);
        }
        size.getTranslations().clear();
        size.getTranslations().addAll(updated);
    }

}
