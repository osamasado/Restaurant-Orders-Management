package org.restaurantordersmanagement.backend.menu.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.CategoryTranslation;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.repository.MealRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Deliberately NOT @Transactional: each real HTTP request gets its own
 * short-lived session (open-in-view=false), and a test-wide transaction
 * changes that semantics (a still-referenced Meal from the same test would
 * stay in the same persistence context as a later category-delete attempt,
 * which Hibernate handles differently than the two genuinely separate
 * sessions production traffic would use). tearDown() instead deletes exactly
 * what each test created, by id, matching OrderNumberConcurrencyTest's fix
 * for the same class of leaked-row problem.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CategoryControllerTest {

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

    private Category createCategory(int sortOrder, String nameEn) {
        Category category = new Category();
        category.setSortOrder(sortOrder);
        CategoryTranslation translation = new CategoryTranslation();
        translation.setCategory(category);
        translation.setLanguage(Language.EN);
        translation.setName(nameEn);
        category.getTranslations().add(translation);
        Category saved = categoryRepository.saveAndFlush(category);
        createdCategoryIds.add(saved.getId());
        return saved;
    }

    @Test
    void listRejectsNonAdminRole() throws Exception {
        mockMvc.perform(get("/api/categories").with(user("waiter").roles("WAITER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createListUpdateAndDeleteRoundTrip() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/categories")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                    "sortOrder": 1,
                                    "translations": [
                                        {"language": "EN", "name": "Starters"},
                                        {"language": "DE", "name": "Vorspeisen"},
                                        {"language": "AR", "name": "مقبلات"}
                                    ]
                                }
                                """
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortOrder").value(1))
                .andReturn();

        String body = createResult.getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        createdCategoryIds.add(id);

        mockMvc.perform(get("/api/categories").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());

        mockMvc.perform(put("/api/categories/" + id)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                    "sortOrder": 2,
                                    "translations": [
                                        {"language": "EN", "name": "Starters (renamed)"}
                                    ]
                                }
                                """
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortOrder").value(2))
                .andExpect(jsonPath("$.translations[0].name").value("Starters (renamed)"));

        mockMvc.perform(delete("/api/categories/" + id).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        assertTrue(categoryRepository.findById(id).isEmpty());
    }

    @Test
    void createRejectsEmptyTranslations() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                    "sortOrder": 1,
                                    "translations": []
                                }
                                """
                        ))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingACategoryStillReferencedByAMealIsRejected() throws Exception {
        Category category = createCategory(1, "Mains");

        Meal meal = new Meal();
        meal.setCategory(category);
        Meal savedMeal = mealRepository.saveAndFlush(meal);
        createdMealIds.add(savedMeal.getId());

        mockMvc.perform(delete("/api/categories/" + category.getId()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict());

        assertTrue(categoryRepository.findById(category.getId()).isPresent());
    }

}
