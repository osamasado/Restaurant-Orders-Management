package org.restaurantordersmanagement.backend.order.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;
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
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class OrderItemSnapshotTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void editingMealAfterOrderingDoesNotChangeTheOrderedItem() {
        Category category = new Category();
        category.setSortOrder(1);
        category = categoryRepository.saveAndFlush(category);

        Meal meal = new Meal();
        meal.setCategory(category);
        meal.setAvailable(true);

        MealTranslation nameEn = new MealTranslation();
        nameEn.setMeal(meal);
        nameEn.setLanguage(Language.EN);
        nameEn.setName("Wiener Schnitzel");
        meal.getTranslations().add(nameEn);

        MealSize size = new MealSize();
        size.setMeal(meal);
        size.setPrice(new BigDecimal("18.90"));
        MealSizeTranslation sizeLabelEn = new MealSizeTranslation();
        sizeLabelEn.setMealSize(size);
        sizeLabelEn.setLanguage(Language.EN);
        sizeLabelEn.setLabel("200 g");
        size.getTranslations().add(sizeLabelEn);
        meal.getSizes().add(size);

        meal = mealRepository.saveAndFlush(meal);
        MealSize savedSize = meal.getSizes().get(0);

        // Snapshot the item onto an order at "order time".
        OrderItem orderedItem = OrderItem.snapshotFrom(savedSize, Language.EN, 2, "Extra sauce");

        Table table = new Table();
        table.setTableNumber("7");
        table.setRoom("Garden room");
        table.setSeats(4);
        table = tableRepository.saveAndFlush(table);

        Order order = new Order();
        order.setTable(table);
        orderedItem.setOrder(order);
        order.getItems().add(orderedItem);
        Order savedOrder = orderRepository.saveAndFlush(order);

        // Now the price/name change on the ORIGINAL meal - after it was ordered.
        Meal mealToEdit = mealRepository.findById(meal.getId()).orElseThrow();
        mealToEdit.getSizes().get(0).setPrice(new BigDecimal("99.99"));
        mealToEdit.getTranslations().get(0).setName("Renamed Schnitzel");
        mealRepository.saveAndFlush(mealToEdit);

        // The already-placed order's line item must be completely unaffected.
        Optional<Order> reloaded = orderRepository.findById(savedOrder.getId());
        assertTrue(reloaded.isPresent());
        OrderItem reloadedItem = reloaded.get().getItems().get(0);
        assertEquals("Wiener Schnitzel", reloadedItem.getName());
        assertEquals("200 g", reloadedItem.getSize());
        assertEquals(new BigDecimal("18.90"), reloadedItem.getUnitPrice());
        assertEquals(2, reloadedItem.getQuantity());
        assertEquals("Extra sauce", reloadedItem.getNote());
    }

    @Test
    void snapshotFallsBackToEnglishWhenRequestedLanguageIsMissing() {
        Category category = new Category();
        category.setSortOrder(1);
        category = categoryRepository.saveAndFlush(category);

        Meal meal = new Meal();
        meal.setCategory(category);
        meal.setAvailable(true);

        MealTranslation nameDe = new MealTranslation();
        nameDe.setMeal(meal);
        nameDe.setLanguage(Language.DE);
        nameDe.setName("Kaesespaetzle");
        meal.getTranslations().add(nameDe);

        MealTranslation nameEn = new MealTranslation();
        nameEn.setMeal(meal);
        nameEn.setLanguage(Language.EN);
        nameEn.setName("Cheese spaetzle");
        meal.getTranslations().add(nameEn);

        MealSize size = new MealSize();
        size.setMeal(meal);
        size.setPrice(new BigDecimal("12.00"));
        MealSizeTranslation sizeLabelDe = new MealSizeTranslation();
        sizeLabelDe.setMealSize(size);
        sizeLabelDe.setLanguage(Language.DE);
        sizeLabelDe.setLabel("Normal");
        size.getTranslations().add(sizeLabelDe);
        meal.getSizes().add(size);

        meal = mealRepository.saveAndFlush(meal);

        // No AR translation at all: the name falls back to EN, the size label
        // (no EN either) to whatever exists.
        OrderItem item = OrderItem.snapshotFrom(meal.getSizes().get(0), Language.AR, 1, null);
        assertEquals("Cheese spaetzle", item.getName());
        assertEquals("Normal", item.getSize());
    }

}
