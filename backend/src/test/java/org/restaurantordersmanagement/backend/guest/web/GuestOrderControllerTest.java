package org.restaurantordersmanagement.backend.guest.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
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
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderItem;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.model.OrderStatusHistory;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.order.service.OrderStateMachineService;
import org.restaurantordersmanagement.backend.settings.model.Config;
import org.restaurantordersmanagement.backend.settings.model.PaymentMethod;
import org.restaurantordersmanagement.backend.settings.service.SettingsService;
import org.restaurantordersmanagement.backend.settings.web.ConfigRequest;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Config's tax rate is seeded at 19.00 and all four payment methods are
 * enabled by V9__config.sql. Not @Transactional - see CategoryControllerTest's
 * javadoc for why. tearDown() deletes tracked ids instead (orders first, since
 * they reference the table).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GuestOrderControllerTest {

    private static final String DEVICE_CODE = "GU3ST7";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private OrderStateMachineService orderStateMachineService;

    private final List<Long> createdOrderIds = new ArrayList<>();
    private final List<Long> createdMealIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();
    private final List<Long> createdTableIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        createdOrderIds.forEach(orderRepository::deleteById);
        createdMealIds.forEach(mealRepository::deleteById);
        createdCategoryIds.forEach(categoryRepository::deleteById);
        createdTableIds.forEach(tableRepository::deleteById);
    }

    private Table createPairedTable(String deviceCode) {
        Table table = new Table();
        table.setTableNumber("G-" + UUID.randomUUID());
        table.setRoom("Main room");
        table.setSeats(4);
        table.setPairedDeviceId(deviceCode);
        table = tableRepository.saveAndFlush(table);
        createdTableIds.add(table.getId());
        return table;
    }

    private Long createMealSize(String nameEn, String price, boolean available) {
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
        name.setName(nameEn);
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

    private ResultActions submit(String deviceCode, String paymentMethod, String items) throws Exception {
        String body = "{\"deviceCode\":\"" + deviceCode + "\",\"language\":\"EN\",\"paymentMethod\":\""
                + paymentMethod + "\",\"items\":[" + items + "]}";
        return mockMvc.perform(post("/api/guest/orders").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Long trackOrder(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.orderId");
        createdOrderIds.add(id.longValue());
        return id.longValue();
    }

    private ResultActions getStatus(Long orderId, String deviceCode) throws Exception {
        return mockMvc.perform(get("/api/guest/orders/" + orderId).header("X-Device-Code", deviceCode));
    }

    private Long submitSoupOrder() throws Exception {
        Long size = createMealSize("Soup", "4.30", true);
        return trackOrder(submit(DEVICE_CODE, "CASH", "{\"sizeId\":" + size + ",\"quantity\":1}")
                .andExpect(status().isCreated()));
    }

    @Test
    void submitCreatesASubmittedOrderWithServerPricingAndSnapshots() throws Exception {
        Table table = createPairedTable(DEVICE_CODE);
        Long sizeA = createMealSize("Schnitzel", "8.40", true);
        Long sizeB = createMealSize("Soup", "4.30", true);

        ResultActions result = submit(DEVICE_CODE, "CARD",
                "{\"sizeId\":" + sizeA + ",\"quantity\":2,\"note\":\"  no onions \"},"
                        + "{\"sizeId\":" + sizeB + ",\"quantity\":1}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.orderNumber").isNumber())
                .andExpect(jsonPath("$.placedAt").isNotEmpty())
                .andExpect(jsonPath("$.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.subtotal").value(21.10))
                .andExpect(jsonPath("$.taxAmount").value(4.01))
                .andExpect(jsonPath("$.total").value(25.11));
        Long orderId = trackOrder(result);

        transactionTemplate.executeWithoutResult(tx -> {
            Order order = orderRepository.findById(orderId).orElseThrow();
            assertEquals(table.getId(), order.getTable().getId());
            assertEquals(2, order.getItems().size());
            OrderItem first = order.getItems().getFirst();
            assertEquals("Schnitzel", first.getName());
            assertEquals("Regular", first.getSize());
            assertEquals(new BigDecimal("8.40"), first.getUnitPrice());
            assertEquals("no onions", first.getNote());
            assertNull(order.getItems().get(1).getNote());

            assertEquals(1, order.getHistory().size());
            OrderStatusHistory entry = order.getHistory().getFirst();
            assertEquals(OrderStatus.SUBMITTED, entry.getStatus());
            assertNull(entry.getChangedBy());
        });
    }

    @Test
    void consecutiveSubmitsGetConsecutiveOrderNumbers() throws Exception {
        createPairedTable(DEVICE_CODE);
        Long size = createMealSize("Soup", "4.30", true);
        String item = "{\"sizeId\":" + size + ",\"quantity\":1}";

        ResultActions first = submit(DEVICE_CODE, "CASH", item).andExpect(status().isCreated());
        ResultActions second = submit(DEVICE_CODE, "CASH", item).andExpect(status().isCreated());
        trackOrder(first);
        trackOrder(second);

        int firstNumber = JsonPath.read(first.andReturn().getResponse().getContentAsString(), "$.orderNumber");
        int secondNumber = JsonPath.read(second.andReturn().getResponse().getContentAsString(), "$.orderNumber");
        assertEquals(firstNumber + 1, secondNumber);
    }

    @Test
    void unavailableMealIsRejectedAndNoOrderIsCreated() throws Exception {
        createPairedTable(DEVICE_CODE);
        Long available = createMealSize("Soup", "4.30", true);
        Long soldOut = createMealSize("Sold out", "9.00", false);
        long ordersBefore = orderRepository.count();

        submit(DEVICE_CODE, "CASH",
                "{\"sizeId\":" + available + ",\"quantity\":1},{\"sizeId\":" + soldOut + ",\"quantity\":1}")
                .andExpect(status().isConflict());

        assertEquals(ordersBefore, orderRepository.count());
    }

    @Test
    void disabledPaymentMethodIsRejected() throws Exception {
        createPairedTable(DEVICE_CODE);
        Long size = createMealSize("Soup", "4.30", true);
        Config original = settingsService.getConfig();
        ConfigRequest restore = new ConfigRequest(original.getCurrencyCode(), original.getCurrencySymbol(),
                original.getSymbolPosition(), original.getTaxRate(), original.getDefaultLanguage(),
                EnumSet.copyOf(original.getEnabledPaymentMethods()));
        settingsService.updateConfig(new ConfigRequest(original.getCurrencyCode(), original.getCurrencySymbol(),
                original.getSymbolPosition(), original.getTaxRate(), original.getDefaultLanguage(),
                EnumSet.of(PaymentMethod.CASH)));
        try {
            submit(DEVICE_CODE, "PAYPAL", "{\"sizeId\":" + size + ",\"quantity\":1}")
                    .andExpect(status().isBadRequest());
        } finally {
            settingsService.updateConfig(restore);
        }
    }

    @Test
    void unpairedDeviceIsForbidden() throws Exception {
        Long size = createMealSize("Soup", "4.30", true);

        submit("NOTPAIRED", "CASH", "{\"sizeId\":" + size + ",\"quantity\":1}")
                .andExpect(status().isForbidden());
    }

    @Test
    void emptyCartIsRejected() throws Exception {
        createPairedTable(DEVICE_CODE);

        submit(DEVICE_CODE, "CASH", "").andExpect(status().isBadRequest());
    }

    @Test
    void outOfRangeQuantityAndUnknownSizeAreRejected() throws Exception {
        createPairedTable(DEVICE_CODE);
        Long size = createMealSize("Soup", "4.30", true);

        submit(DEVICE_CODE, "CASH", "{\"sizeId\":" + size + ",\"quantity\":21}")
                .andExpect(status().isBadRequest());
        submit(DEVICE_CODE, "CASH", "{\"sizeId\":999999999,\"quantity\":1}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusOfAFreshOrderShowsSubmittedWithOneHistoryEntry() throws Exception {
        createPairedTable(DEVICE_CODE);
        Long orderId = submitSoupOrder();

        getStatus(orderId, DEVICE_CODE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.orderNumber").isNumber())
                .andExpect(jsonPath("$.total").value(5.12)) // 4.30 + 19% = 5.117 -> 5.12
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.history[0].changedBy").doesNotExist());
    }

    @Test
    void statusReflectsLaterTransition() throws Exception {
        createPairedTable(DEVICE_CODE);
        Long orderId = submitSoupOrder();

        transactionTemplate.executeWithoutResult(tx -> {
            Order order = orderRepository.findById(orderId).orElseThrow();
            orderStateMachineService.transition(order, OrderStatus.PREPARING, null);
        });

        getStatus(orderId, DEVICE_CODE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.history[1].status").value("PREPARING"));
    }

    @Test
    void otherTablesDeviceCannotReadTheOrder() throws Exception {
        createPairedTable(DEVICE_CODE);
        createPairedTable("OTH3R9");
        Long orderId = submitSoupOrder();

        getStatus(orderId, "OTH3R9").andExpect(status().isNotFound());
    }

    @Test
    void unpairedDeviceCannotReadStatus() throws Exception {
        createPairedTable(DEVICE_CODE);
        Long orderId = submitSoupOrder();

        getStatus(orderId, "NOTPAIRED").andExpect(status().isForbidden());
    }

    @Test
    void unknownOrderIsNotFound() throws Exception {
        createPairedTable(DEVICE_CODE);

        getStatus(999999999L, DEVICE_CODE).andExpect(status().isNotFound());
    }

    @Test
    void missingDeviceHeaderIsRejected() throws Exception {
        mockMvc.perform(get("/api/guest/orders/1")).andExpect(status().isBadRequest());
    }

}
