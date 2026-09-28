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
import org.springframework.transaction.annotation.Transactional;

/**
 * @Transactional so the seeded rows are rolled back afterwards - the test
 * DB is shared across test classes, and leftover demo tables ("1", "2",
 * "9", ...) collided with tables other tests create, depending on run order.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
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
