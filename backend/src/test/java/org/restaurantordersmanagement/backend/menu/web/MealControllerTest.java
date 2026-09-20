package org.restaurantordersmanagement.backend.menu.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.CategoryTranslation;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.repository.MealRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MealControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MealRepository mealRepository;

    private Long createCategory() throws Exception {
        Category category = new Category();
        category.setSortOrder(1);
        CategoryTranslation translation = new CategoryTranslation();
        translation.setCategory(category);
        translation.setLanguage(Language.EN);
        translation.setName("Mains");
        category.getTranslations().add(translation);
        return categoryRepository.saveAndFlush(category).getId();
    }

    private String mealRequestJson(Long categoryId) {
        return """
                {
                    "categoryId": %d,
                    "available": true,
                    "translations": [
                        {"language": "EN", "name": "Schnitzel", "description": "Crispy", "preparationMethod": "Fried", "ingredients": ["Veal", "Breadcrumbs"]},
                        {"language": "DE", "name": "Schnitzel", "description": null, "preparationMethod": null, "ingredients": []},
                        {"language": "AR", "name": "شنيتزل", "description": null, "preparationMethod": null, "ingredients": []}
                    ],
                    "sizes": [
                        {"id": null, "price": 18.90, "translations": [{"language": "EN", "label": "200 g"}]}
                    ]
                }
                """.formatted(categoryId);
    }

    @Test
    void listRejectsNonAdminRole() throws Exception {
        mockMvc.perform(get("/api/meals").with(user("waiter").roles("WAITER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRejectsMissingSizes() throws Exception {
        Long categoryId = createCategory();

        mockMvc.perform(post("/api/meals")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"categoryId": %d, "available": true, "translations": [{"language":"EN","name":"X","ingredients":[]}], "sizes": []}
                                """
                                        .formatted(categoryId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createListUpdateAvailabilityAndDeleteRoundTrip() throws Exception {
        Long categoryId = createCategory();

        MvcResult createResult = mockMvc.perform(post("/api/meals")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mealRequestJson(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.sizes[0].price").value(18.90))
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andReturn();

        Long id = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(get("/api/meals").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());

        mockMvc.perform(patch("/api/meals/" + id + "/availability")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(delete("/api/meals/" + id).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        assertTrue(mealRepository.findById(id).isEmpty());
    }

    @Test
    void uploadReplaceAndDeleteImage() throws Exception {
        Long categoryId = createCategory();

        MvcResult createResult = mockMvc.perform(post("/api/meals")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mealRequestJson(categoryId)))
                .andExpect(status().isOk())
                .andReturn();
        Long id = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

        MockMultipartFile file = new MockMultipartFile("file", "dish.jpg", "image/jpeg", "fake-jpeg-bytes".getBytes());

        mockMvc.perform(multipart("/api/meals/" + id + "/image", id)
                        .file(file)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.startsWith("/images/meals/")));

        mockMvc.perform(delete("/api/meals/" + id + "/image").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
    }

    @Test
    void uploadRejectsUnsupportedImageType() throws Exception {
        Long categoryId = createCategory();

        MvcResult createResult = mockMvc.perform(post("/api/meals")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mealRequestJson(categoryId)))
                .andExpect(status().isOk())
                .andReturn();
        Long id = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

        MockMultipartFile file = new MockMultipartFile("file", "dish.gif", "image/gif", "fake-gif-bytes".getBytes());

        mockMvc.perform(multipart("/api/meals/" + id + "/image", id)
                        .file(file)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReplacesTranslationsAndSizes() throws Exception {
        Long categoryId = createCategory();

        MvcResult createResult = mockMvc.perform(post("/api/meals")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mealRequestJson(categoryId)))
                .andExpect(status().isOk())
                .andReturn();
        Long id = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(put("/api/meals/" + id)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                    "categoryId": %d,
                                    "available": true,
                                    "translations": [
                                        {"language": "EN", "name": "Schnitzel Deluxe", "description": null, "preparationMethod": null, "ingredients": []}
                                    ],
                                    "sizes": [
                                        {"id": null, "price": 25.00, "translations": [{"language": "EN", "label": "300 g"}]}
                                    ]
                                }
                                """
                                        .formatted(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translations.length()").value(1))
                .andExpect(jsonPath("$.translations[0].name").value("Schnitzel Deluxe"))
                .andExpect(jsonPath("$.sizes.length()").value(1))
                .andExpect(jsonPath("$.sizes[0].price").value(25.00));
    }

}
