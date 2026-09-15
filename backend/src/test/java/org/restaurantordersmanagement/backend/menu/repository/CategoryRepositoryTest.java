package org.restaurantordersmanagement.backend.menu.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.CategoryTranslation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void savesCategoryWithCascadedTranslations() {
        Category category = new Category();
        category.setSortOrder(1);

        CategoryTranslation en = new CategoryTranslation();
        en.setCategory(category);
        en.setLanguage(Language.EN);
        en.setName("Starters");
        category.getTranslations().add(en);

        CategoryTranslation de = new CategoryTranslation();
        de.setCategory(category);
        de.setLanguage(Language.DE);
        de.setName("Vorspeisen");
        category.getTranslations().add(de);

        Category saved = categoryRepository.saveAndFlush(category);

        Optional<Category> found = categoryRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(2, found.get().getTranslations().size());
        assertEquals(1, found.get().getSortOrder());
    }

}
