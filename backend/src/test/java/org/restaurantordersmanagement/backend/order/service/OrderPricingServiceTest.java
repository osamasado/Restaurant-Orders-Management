package org.restaurantordersmanagement.backend.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;
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
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderItem;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Config's tax rate is seeded at 19.00 (19%) by V9__config.sql - see
 * ConfigRepositoryTest. Prices below are chosen so the expected numbers come
 * out exact without any rounding, keeping this test focused on wiring rather
 * than rounding edge cases (OrderPricingCalculatorTest covers those).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class OrderPricingServiceTest {

    @Autowired
    private OrderStateMachineService orderStateMachineService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MealRepository mealRepository;

    private MealSize createMealSize(String price, String label) {
        Category category = new Category();
        category.setSortOrder(1);
        category = categoryRepository.saveAndFlush(category);

        Meal meal = new Meal();
        meal.setCategory(category);
        meal.setAvailable(true);
        MealTranslation name = new MealTranslation();
        name.setMeal(meal);
        name.setLanguage(Language.EN);
        name.setName("Test Meal");
        meal.getTranslations().add(name);

        MealSize size = new MealSize();
        size.setMeal(meal);
        size.setPrice(new BigDecimal(price));
        MealSizeTranslation sizeLabel = new MealSizeTranslation();
        sizeLabel.setMealSize(size);
        sizeLabel.setLanguage(Language.EN);
        sizeLabel.setLabel(label);
        size.getTranslations().add(sizeLabel);
        meal.getSizes().add(size);

        meal = mealRepository.saveAndFlush(meal);
        return meal.getSizes().get(0);
    }

    @Test
    void submittingAnOrderRecomputesAuthoritativePricingAndIgnoresAnyExistingTotal() {
        Table table = new Table();
        table.setTableNumber("9");
        table.setRoom("Main room");
        table.setSeats(2);
        table = tableRepository.saveAndFlush(table);

        MealSize sizeA = createMealSize("10.00", "Regular");
        MealSize sizeB = createMealSize("5.00", "Small");

        Order order = new Order();
        order.setTable(table);

        OrderItem itemA = OrderItem.snapshotFrom(sizeA, Language.EN, 2, null);
        itemA.setOrder(order);
        order.getItems().add(itemA);

        OrderItem itemB = OrderItem.snapshotFrom(sizeB, Language.EN, 1, null);
        itemB.setOrder(order);
        order.getItems().add(itemB);

        // Simulate a tampered/incorrect client-submitted total that must be ignored.
        order.setTotal(new BigDecimal("0.01"));

        order = orderRepository.saveAndFlush(order);

        Order submitted = orderStateMachineService.transition(order, OrderStatus.SUBMITTED, null);

        assertEquals(new BigDecimal("25.00"), submitted.getSubtotal());
        assertEquals(new BigDecimal("4.75"), submitted.getTaxAmount());
        assertEquals(new BigDecimal("29.75"), submitted.getTotal());
        assertNotEquals(new BigDecimal("0.01"), submitted.getTotal());
    }

}
