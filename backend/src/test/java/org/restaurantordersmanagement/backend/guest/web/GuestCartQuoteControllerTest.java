package org.restaurantordersmanagement.backend.guest.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.model.MealSizeTranslation;
import org.restaurantordersmanagement.backend.menu.model.MealTranslation;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.repository.MealRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Config's tax rate is seeded at 19.00 by V9__config.sql. Not @Transactional -
 * see CategoryControllerTest's javadoc for why. tearDown() deletes tracked ids instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GuestCartQuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MealRepository mealRepository;

    private final List<Long> createdMealIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long mealId : createdMealIds) {
            mealRepository.findById(mealId).ifPresent(mealRepository::delete);
        }
        for (Long categoryId : createdCategoryIds) {
            categoryRepository.findById(categoryId).ifPresent(categoryRepository::delete);
        }
    }

    private Long createMealSize(String price, boolean available) {
        Category category = new Category();
        category.setSortOrder(1);
        category = categoryRepository.saveAndFlush(category);
        createdCategoryIds.add(category.getId());

        Meal meal = new Meal();
        meal.setCategory(category);
        meal.setAvailable(available);
        MealTranslation name = new MealTranslation();
        name.setMeal(meal);
        name.setLanguage(Language.EN);
        name.setName("Test Meal");
        meal.getTranslations().add(name);

        MealSize size = new MealSize();
        size.setMeal(meal);
        size.setPrice(new BigDecimal(price));
        MealSizeTranslation label = new MealSizeTranslation();
        label.setMealSize(size);
        label.setLanguage(Language.EN);
        label.setLabel("Regular");
        size.getTranslations().add(label);
        meal.getSizes().add(size);

        meal = mealRepository.saveAndFlush(meal);
        createdMealIds.add(meal.getId());
        return meal.getSizes().get(0).getId();
    }

    private String body(String items) {
        return "{\"items\":[" + items + "]}";
    }

    @Test
    void quotesSubtotalTaxAndTotalFromDatabasePrices() throws Exception {
        Long sizeA = createMealSize("8.40", true);
        Long sizeB = createMealSize("4.30", true);

        // 2 x 8.40 + 1 x 4.30 = 21.10; 19% of that is 4.009 -> 4.01; total 25.11
        mockMvc.perform(post("/api/guest/cart/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"sizeId\":" + sizeA + ",\"quantity\":2},{\"sizeId\":" + sizeB + ",\"quantity\":1}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(21.10))
                .andExpect(jsonPath("$.taxRate").value(19.00))
                .andExpect(jsonPath("$.taxAmount").value(4.01))
                .andExpect(jsonPath("$.total").value(25.11))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(8.40))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(16.80))
                .andExpect(jsonPath("$.lines[0].available").value(true));
    }

    @Test
    void flagsLinesWhoseMealIsNoLongerAvailable() throws Exception {
        Long size = createMealSize("5.00", false);

        mockMvc.perform(post("/api/guest/cart/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"sizeId\":" + size + ",\"quantity\":1}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].available").value(false));
    }

    @Test
    void emptyCartQuotesZero() throws Exception {
        mockMvc.perform(post("/api/guest/cart/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(0))
                .andExpect(jsonPath("$.taxAmount").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void unknownSizeIsRejected() throws Exception {
        mockMvc.perform(post("/api/guest/cart/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"sizeId\":999999999,\"quantity\":1}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void outOfRangeQuantityIsRejected() throws Exception {
        Long size = createMealSize("5.00", true);

        mockMvc.perform(post("/api/guest/cart/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"sizeId\":" + size + ",\"quantity\":0}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/guest/cart/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"sizeId\":" + size + ",\"quantity\":21}")))
                .andExpect(status().isBadRequest());
    }

}
