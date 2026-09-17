package org.restaurantordersmanagement.backend.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.repository.MealRepository;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DemoDataSeederTest {

    @Autowired
    private DemoDataSeeder demoDataSeeder;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Test
    void seedAllProducesExpectedCountsAndIsIdempotent() {
        demoDataSeeder.seedAll();

        assertCounts();

        // Running it again must not duplicate anything.
        demoDataSeeder.seedAll();

        assertCounts();
    }

    private void assertCounts() {
        assertEquals(4, categoryRepository.count());
        assertEquals(8, mealRepository.count());
        assertEquals(6, tableRepository.count());
        assertEquals(4, staffAccountRepository.count());
    }

}
