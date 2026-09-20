package org.restaurantordersmanagement.backend.menu.web;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.CategoryTranslation;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.model.RawMaterial;
import org.restaurantordersmanagement.backend.menu.model.Recipe;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.repository.MealRepository;
import org.restaurantordersmanagement.backend.menu.repository.RawMaterialRepository;
import org.restaurantordersmanagement.backend.menu.repository.RecipeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Not @Transactional - see CategoryControllerTest's javadoc for why. tearDown() deletes tracked ids instead. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RawMaterialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RawMaterialRepository rawMaterialRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private final List<Long> createdRawMaterialIds = new ArrayList<>();
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
        for (Long rawMaterialId : createdRawMaterialIds) {
            rawMaterialRepository.findById(rawMaterialId).ifPresent(rawMaterialRepository::delete);
        }
    }

    private String rawMaterialRequestJson(String name) {
        return """
                {"name": "%s", "unit": "kg", "inStockQuantity": 10.5, "supplier": "Acme"}
                """.formatted(name);
    }

    @Test
    void listRejectsNonAdminRole() throws Exception {
        mockMvc.perform(get("/api/raw-materials").with(user("waiter").roles("WAITER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/raw-materials")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "unit": "kg", "inStockQuantity": 1, "supplier": "Acme"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createListUpdateAndDeleteRoundTrip() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/raw-materials")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawMaterialRequestJson("Flour")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Flour"))
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andReturn();

        Long id = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();
        createdRawMaterialIds.add(id);

        mockMvc.perform(get("/api/raw-materials").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());

        mockMvc.perform(put("/api/raw-materials/" + id)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Flour (renamed)", "unit": "kg", "inStockQuantity": 20, "supplier": "Acme"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Flour (renamed)"))
                .andExpect(jsonPath("$.inStockQuantity").value(20));

        mockMvc.perform(delete("/api/raw-materials/" + id).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        assertTrue(rawMaterialRepository.findById(id).isEmpty());
    }

    @Test
    void uploadReplaceAndDeleteImage() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/raw-materials")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawMaterialRequestJson("Sugar")))
                .andExpect(status().isOk())
                .andReturn();
        Long id = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();
        createdRawMaterialIds.add(id);

        MockMultipartFile file = new MockMultipartFile("file", "sugar.jpg", "image/jpeg", "fake-jpeg-bytes".getBytes());

        mockMvc.perform(multipart("/api/raw-materials/" + id + "/image", id)
                        .file(file)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(startsWith("/images/raw-materials/")));

        mockMvc.perform(delete("/api/raw-materials/" + id + "/image").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
    }

    @Test
    void deletingARawMaterialStillUsedInARecipeIsRejected() throws Exception {
        Category category = new Category();
        category.setSortOrder(1);
        CategoryTranslation translation = new CategoryTranslation();
        translation.setCategory(category);
        translation.setLanguage(Language.EN);
        translation.setName("Mains");
        category.getTranslations().add(translation);
        Category savedCategory = categoryRepository.saveAndFlush(category);
        createdCategoryIds.add(savedCategory.getId());

        Meal meal = new Meal();
        meal.setCategory(savedCategory);
        MealSize size = new MealSize();
        size.setMeal(meal);
        size.setPrice(new BigDecimal("10.00"));
        meal.getSizes().add(size);
        Meal savedMeal = mealRepository.saveAndFlush(meal);
        createdMealIds.add(savedMeal.getId());
        MealSize savedSize = savedMeal.getSizes().get(0);

        RawMaterial rawMaterial = new RawMaterial();
        rawMaterial.setName("Flour");
        rawMaterial.setUnit("kg");
        RawMaterial savedRawMaterial = rawMaterialRepository.saveAndFlush(rawMaterial);
        createdRawMaterialIds.add(savedRawMaterial.getId());

        Recipe recipe = new Recipe();
        recipe.setMealSize(savedSize);
        recipe.setRawMaterial(savedRawMaterial);
        recipe.setQuantity(new BigDecimal("0.50"));
        recipeRepository.saveAndFlush(recipe);

        mockMvc.perform(delete("/api/raw-materials/" + savedRawMaterial.getId()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict());

        assertTrue(rawMaterialRepository.findById(savedRawMaterial.getId()).isPresent());

        recipeRepository.delete(recipe);
    }

}
