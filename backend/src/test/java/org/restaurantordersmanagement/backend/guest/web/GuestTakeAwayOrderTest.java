package org.restaurantordersmanagement.backend.guest.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.ArrayList;
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
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.staff.security.StaffPrincipal;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Take-away orders and the order token, over HTTP. Not @Transactional, like GuestOrderControllerTest: the orders are
 * real commits, so tearDown() deletes what was created (orders first, they reference the tables).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GuestTakeAwayOrderTest {

    private static final String DEVICE_CODE = "TK4WAY";

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
    private StaffAccountRepository staffAccountRepository;

    private final List<Long> createdOrderIds = new ArrayList<>();
    private final List<Long> createdMealIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();
    private final List<Long> createdTableIds = new ArrayList<>();
    private final List<Long> createdStaffIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        createdOrderIds.forEach(orderRepository::deleteById);
        createdMealIds.forEach(mealRepository::deleteById);
        createdCategoryIds.forEach(categoryRepository::deleteById);
        createdTableIds.forEach(tableRepository::deleteById);
        createdStaffIds.forEach(staffAccountRepository::deleteById);
    }

    private Table pairedTable(String code) {
        Table table = new Table();
        table.setTableNumber("TK-" + UUID.randomUUID());
        table.setRoom("Main room");
        table.setSeats(4);
        table.setPairedDeviceId(code);
        table = tableRepository.saveAndFlush(table);
        createdTableIds.add(table.getId());
        return table;
    }

    private Long mealSize(String price) {
        Category category = new Category();
        category.setSortOrder(1);
        category = categoryRepository.saveAndFlush(category);
        createdCategoryIds.add(category.getId());
        Meal meal = new Meal();
        meal.setCategory(category);
        meal.setAvailable(true);
        MealTranslation name = new MealTranslation();
        name.setMeal(meal);
        name.setLanguage(Language.EN);
        name.setName("Take-away soup");
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

    private ResultActions place(String extraFields, Long sizeId) throws Exception {
        String body = "{" + extraFields + "\"language\":\"EN\",\"paymentMethod\":\"CASH\",\"items\":[{\"sizeId\":" + sizeId
                + ",\"quantity\":2}]}";
        return mockMvc.perform(post("/api/guest/orders").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String placeTakeAway(Long sizeId) throws Exception {
        ResultActions result = place("\"takeAway\":true,", sizeId).andExpect(status().isCreated());
        String json = result.andReturn().getResponse().getContentAsString();
        createdOrderIds.add(((Number) JsonPath.read(json, "$.orderId")).longValue());
        return json;
    }

    private ResultActions readStatus(long orderId, String header, String value) throws Exception {
        return mockMvc.perform(get("/api/guest/orders/" + orderId).header(header, value));
    }

    @Test
    void aTakeAwayOrderNeedsNoDeviceCodeAndIsPlacedAgainstTheReservedTable() throws Exception {
        String json = placeTakeAway(mealSize("4.00"));

        long orderId = ((Number) JsonPath.read(json, "$.orderId")).longValue();
        assertNotNull(JsonPath.read(json, "$.orderNumber"));
        assertEquals("SUBMITTED", JsonPath.read(json, "$.status"));
        // Priced by the server (2 x 4.00 plus the configured tax), never by anything the client sent.
        assertEquals(0, new BigDecimal("8.00").compareTo(new BigDecimal(JsonPath.read(json, "$.subtotal").toString())));
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertTrue(order.getTable().isTakeAway());
        assertFalse(json.contains("tableId"), "the response names no table");
    }

    @Test
    void aTakeAwayOrderThatAlsoCarriesADeviceCodeIsRejected() throws Exception {
        place("\"takeAway\":true,\"deviceCode\":\"" + DEVICE_CODE + "\",", mealSize("4.00")).andExpect(status().isBadRequest());
    }

    @Test
    void anOrderWithoutTakeAwayStillNeedsAPairedDevice() throws Exception {
        place("", mealSize("4.00")).andExpect(status().isForbidden());
        place("\"takeAway\":false,\"deviceCode\":\"NOPE00\",", mealSize("4.00")).andExpect(status().isForbidden());
    }

    @Test
    void theTokenFollowsTheOrderAndNothingElseDoes() throws Exception {
        Long sizeId = mealSize("4.00");
        String json = placeTakeAway(sizeId);
        long orderId = ((Number) JsonPath.read(json, "$.orderId")).longValue();
        String token = JsonPath.read(json, "$.accessToken");
        String otherToken = JsonPath.read(placeTakeAway(sizeId), "$.accessToken");
        assertTrue(token.length() >= 40, "a long random secret");
        assertFalse(token.equals(otherToken));

        readStatus(orderId, "X-Order-Token", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        // Another take-away guest's secret opens nothing here, and neither does a made-up one: the same 404.
        readStatus(orderId, "X-Order-Token", otherToken).andExpect(status().isNotFound());
        readStatus(orderId, "X-Order-Token", "not-the-token").andExpect(status().isNotFound());
        // No proof at all is a 403, not a hint that the id exists.
        mockMvc.perform(get("/api/guest/orders/" + orderId)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/guest/orders/987654321")).andExpect(status().isForbidden());
    }

    @Test
    void aDeviceCodeOfARealTableCannotReadATakeAwayOrder() throws Exception {
        pairedTable(DEVICE_CODE);
        long orderId = ((Number) JsonPath.read(placeTakeAway(mealSize("4.00")), "$.orderId")).longValue();

        readStatus(orderId, "X-Device-Code", DEVICE_CODE).andExpect(status().isNotFound());
    }

    @Test
    void aDineInOrderIsFollowedWithTheTokenOrStillWithItsDeviceCode() throws Exception {
        pairedTable(DEVICE_CODE);
        String json = place("\"deviceCode\":\"" + DEVICE_CODE + "\",", mealSize("5.00")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = ((Number) JsonPath.read(json, "$.orderId")).longValue();
        createdOrderIds.add(orderId);
        String token = JsonPath.read(json, "$.accessToken");

        readStatus(orderId, "X-Order-Token", token).andExpect(status().isOk());
        readStatus(orderId, "X-Device-Code", DEVICE_CODE).andExpect(status().isOk());
        // Another table's device is still refused.
        pairedTable("OTH3R9");
        readStatus(orderId, "X-Device-Code", "OTH3R9").andExpect(status().isNotFound());
    }

    @Test
    void anOrderPlacedBeforeTheTokenExistedKeepsFollowingTheDeviceCodeAndNoTokenOpensIt() throws Exception {
        pairedTable(DEVICE_CODE);
        String json = place("\"deviceCode\":\"" + DEVICE_CODE + "\",", mealSize("5.00")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = ((Number) JsonPath.read(json, "$.orderId")).longValue();
        createdOrderIds.add(orderId);
        Order legacy = orderRepository.findById(orderId).orElseThrow();
        legacy.setAccessTokenHash(null);
        orderRepository.saveAndFlush(legacy);

        readStatus(orderId, "X-Device-Code", DEVICE_CODE).andExpect(status().isOk());
        readStatus(orderId, "X-Order-Token", JsonPath.read(json, "$.accessToken")).andExpect(status().isNotFound());
        assertNull(orderRepository.findById(orderId).orElseThrow().getAccessTokenHash());
    }

    @Test
    void theTokenIsStoredOnlyAsAHashAndNeverShownAgain() throws Exception {
        String json = placeTakeAway(mealSize("4.00"));
        long orderId = ((Number) JsonPath.read(json, "$.orderId")).longValue();
        String token = JsonPath.read(json, "$.accessToken");

        String stored = orderRepository.findById(orderId).orElseThrow().getAccessTokenHash();
        assertEquals(64, stored.length(), "a SHA-256 in hex");
        assertFalse(stored.contains(token));
        String statusJson = readStatus(orderId, "X-Order-Token", token).andReturn().getResponse().getContentAsString();
        assertFalse(statusJson.contains(token), "the status never repeats the secret");
        assertFalse(statusJson.toLowerCase().contains("token"));
    }

    @Test
    void theKitchenAndTheHallBoardSeeATakeAwayFlagAndTheStaffListsDoToo() throws Exception {
        long orderId = ((Number) JsonPath.read(placeTakeAway(mealSize("4.00")), "$.orderId")).longValue();
        StaffAccount kitchen = new StaffAccount();
        kitchen.setName("Take-away cook " + UUID.randomUUID());
        kitchen.setRole(Role.KITCHEN);
        kitchen = staffAccountRepository.saveAndFlush(kitchen);
        createdStaffIds.add(kitchen.getId());

        mockMvc.perform(get("/api/kitchen/orders").with(user(new StaffPrincipal(kitchen))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.orderId == " + orderId + ")].takeAway").value(org.hamcrest.Matchers.contains(true)));

        StaffAccount admin = new StaffAccount();
        admin.setName("Take-away admin " + UUID.randomUUID());
        admin.setRole(Role.ADMIN);
        admin = staffAccountRepository.saveAndFlush(admin);
        createdStaffIds.add(admin.getId());
        mockMvc.perform(get("/api/admin/orders?size=100").with(user(new StaffPrincipal(admin))))
                .andExpect(jsonPath("$.orders[?(@.orderId == " + orderId + ")].takeAway").value(org.hamcrest.Matchers.contains(true)));
        mockMvc.perform(get("/api/admin/orders/history?size=100").with(user(new StaffPrincipal(admin))))
                .andExpect(jsonPath("$.orders[?(@.orderId == " + orderId + ")].takeAway").value(org.hamcrest.Matchers.contains(true)));
    }

}
