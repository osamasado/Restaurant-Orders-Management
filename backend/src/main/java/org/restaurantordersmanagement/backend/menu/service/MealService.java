package org.restaurantordersmanagement.backend.menu.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    public List<Meal> findAll() {
        return mealRepository.findAll();
    }

    public Meal findById(Long id) {
        return mealRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found"));
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

    /** Every update fully replaces the translations and sizes collections - see the plan's "mapping strategy" note. */
    private void applyRequest(Meal meal, MealRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found"));
        meal.setCategory(category);
        meal.setAvailable(request.available());

        meal.getTranslations().clear();
        for (MealTranslationDto dto : request.translations()) {
            MealTranslation translation = new MealTranslation();
            translation.setMeal(meal);
            translation.setLanguage(dto.language());
            translation.setName(dto.name());
            translation.setDescription(dto.description());
            translation.setPreparationMethod(dto.preparationMethod());
            if (dto.ingredients() != null) {
                translation.getIngredients().addAll(dto.ingredients());
            }
            meal.getTranslations().add(translation);
        }

        Map<Long, MealSize> existingSizesById = meal.getSizes().stream()
                .filter(size -> size.getId() != null)
                .collect(Collectors.toMap(MealSize::getId, size -> size));

        List<MealSize> updatedSizes = new ArrayList<>();
        for (MealSizeDto dto : request.sizes()) {
            MealSize size = dto.id() != null ? existingSizesById.get(dto.id()) : null;
            if (size == null) {
                size = new MealSize();
                size.setMeal(meal);
            } else {
                size.getTranslations().clear();
            }
            size.setPrice(dto.price());
            for (MealSizeTranslationDto translationDto : dto.translations()) {
                MealSizeTranslation translation = new MealSizeTranslation();
                translation.setMealSize(size);
                translation.setLanguage(translationDto.language());
                translation.setLabel(translationDto.label());
                size.getTranslations().add(translation);
            }
            updatedSizes.add(size);
        }
        meal.getSizes().clear();
        meal.getSizes().addAll(updatedSizes);
    }

}
