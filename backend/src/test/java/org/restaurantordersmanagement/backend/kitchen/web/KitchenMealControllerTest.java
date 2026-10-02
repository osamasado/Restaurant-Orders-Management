package org.restaurantordersmanagement.backend.kitchen.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.CategoryTranslation;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.model.MealSizeTranslation;
import org.restaurantordersmanagement.backend.menu.model.MealTranslation;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.repository.MealRepository;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.staff.security.StaffPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Not @Transactional - see CategoryControllerTest's javadoc for why.
 * tearDown() deletes tracked ids instead: meals first, then categories, then staff.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class KitchenMealControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    private final List<Long> createdMealIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();
    private final List<Long> createdStaffIds = new ArrayList<>();

    private StaffAccount kitchenStaff;

    @BeforeEach
    void setUp() {
        StaffAccount staff = new StaffAccount();
        staff.setName("Kitchen-" + UUID.randomUUID());
        staff.setRole(Role.KITCHEN);
        kitchenStaff = staffAccountRepository.saveAndFlush(staff);
        createdStaffIds.add(kitchenStaff.getId());
    }

    @AfterEach
    void tearDown() {
        for (Long mealId : createdMealIds) {
            mealRepository.findById(mealId).ifPresent(mealRepository::delete);
        }
        for (Long categoryId : createdCategoryIds) {
            categoryRepository.findById(categoryId).ifPresent(categoryRepository::delete);
        }
        createdStaffIds.forEach(staffAccountRepository::deleteById);
    }

    private Category createCategory() {
        Category category = new Category();
        category.setSortOrder(1);

        CategoryTranslation en = new CategoryTranslation();
        en.setCategory(category);
        en.setLanguage(Language.EN);
        en.setName("Mains");
        category.getTranslations().add(en);

        Category saved = categoryRepository.saveAndFlush(category);
        createdCategoryIds.add(saved.getId());
        return saved;
    }

    private Meal createMeal(Category category, boolean available, String nameEn, String nameDe) {
        Meal meal = new Meal();
        meal.setCategory(category);
        meal.setAvailable(available);

        MealTranslation en = new MealTranslation();
        en.setMeal(meal);
        en.setLanguage(Language.EN);
        en.setName(nameEn);
        meal.getTranslations().add(en);

        MealTranslation de = new MealTranslation();
        de.setMeal(meal);
        de.setLanguage(Language.DE);
        de.setName(nameDe);
        meal.getTranslations().add(de);

        MealSize size = new MealSize();
        size.setMeal(meal);
        size.setPrice(new BigDecimal("12.50"));
        MealSizeTranslation sizeEn = new MealSizeTranslation();
        sizeEn.setMealSize(size);
        sizeEn.setLanguage(Language.EN);
        sizeEn.setLabel("Regular");
        size.getTranslations().add(sizeEn);
        meal.getSizes().add(size);

        Meal saved = mealRepository.saveAndFlush(meal);
        createdMealIds.add(saved.getId());
        return saved;
    }

    /** Logged in as the real kitchen account - a StaffPrincipal, like after /api/staff/login. */
    private RequestPostProcessor asKitchen() {
        return user(new StaffPrincipal(kitchenStaff));
    }

    private ResultActions setAvailability(Long mealId, boolean available, RequestPostProcessor login)
            throws Exception {
        return mockMvc.perform(patch("/api/kitchen/meals/" + mealId + "/availability")
                .with(login)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":" + available + "}"));
    }

    @Test
    void anonymousCannotListOrToggle() throws Exception {
        Meal meal = createMeal(createCategory(), true, "Schnitzel", "Schnitzel DE");

        mockMvc.perform(get("/api/kitchen/meals"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/kitchen/meals/" + meal.getId() + "/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void waiterCannotListOrToggle() throws Exception {
        Meal meal = createMeal(createCategory(), true, "Schnitzel", "Schnitzel DE");

        mockMvc.perform(get("/api/kitchen/meals").with(user("waiter").roles("WAITER")))
                .andExpect(status().isForbidden());
        setAvailability(meal.getId(), false, user("waiter").roles("WAITER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listsMealsWithTheirTranslatedNamesAndAvailability() throws Exception {
        Category category = createCategory();
        Meal available = createMeal(category, true, "Schnitzel", "Schnitzel DE");
        Meal soldOut = createMeal(category, false, "Goulash", "Gulasch");

        mockMvc.perform(get("/api/kitchen/meals").with(asKitchen()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + available.getId() + ")].available").value(contains(true)))
                .andExpect(jsonPath("$[?(@.id == " + soldOut.getId() + ")].available").value(contains(false)))
                .andExpect(jsonPath("$[?(@.id == " + soldOut.getId() + ")].names[?(@.language == 'DE')].name")
                        .value(contains("Gulasch")));
    }

    @Test
    void kitchenToggleTakesTheMealOffAndBackOnTheGuestMenuImmediately() throws Exception {
        Meal meal = createMeal(createCategory(), true, "Schnitzel", "Schnitzel DE");
        String mealOnGuestMenu = "$[*].meals[?(@.id == " + meal.getId() + ")]";

        mockMvc.perform(get("/api/guest/menu").param("language", "EN"))
                .andExpect(jsonPath(mealOnGuestMenu).isNotEmpty());

        setAvailability(meal.getId(), false, asKitchen())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(meal.getId()))
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(get("/api/guest/menu").param("language", "EN"))
                .andExpect(jsonPath(mealOnGuestMenu).isEmpty());

        setAvailability(meal.getId(), true, asKitchen())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        mockMvc.perform(get("/api/guest/menu").param("language", "EN"))
                .andExpect(jsonPath(mealOnGuestMenu).isNotEmpty());
    }

    @Test
    void unknownMealIsNotFound() throws Exception {
        setAvailability(9999999L, false, asKitchen()).andExpect(status().isNotFound());
    }

}
