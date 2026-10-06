package org.restaurantordersmanagement.backend.e2e;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.CategoryTranslation;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.model.MealSizeTranslation;
import org.restaurantordersmanagement.backend.menu.model.MealTranslation;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.repository.MealRepository;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One order, walked across every surface through the real HTTP API with real
 * sign-in sessions - the check that the pieces hold together, not each piece
 * on its own:
 *
 *   guest (menu, quote, pair, submit)  ->  kitchen board  ->  hall board
 *   ->  guest status  ->  admin audit trail
 *
 * and, inside that flow, the rules CLAUDE.md calls non-negotiable: one state
 * machine that refuses illegal steps, server-side prices, order lines that
 * snapshot what was ordered, a table that only reads its own orders, and
 * roles per screen.
 *
 * Fixtures (menu, staff) are created directly: they are not what is under
 * test. Not @Transactional - see CategoryControllerTest's javadoc for why.
 * tearDown() deletes tracked ids: orders first (their history references
 * staff, and they reference tables), then menu, tables, staff.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderLifecycleEndToEndTest {

    private static final String PIN = "1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> orderIds = new ArrayList<>();
    private final List<Long> mealIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private final List<Long> tableIds = new ArrayList<>();
    private final List<Long> staffIds = new ArrayList<>();

    private String kitchenName;
    private String adminName;
    private MockHttpSession kitchen;
    private MockHttpSession admin;
    private Table table;
    private String mealName;
    private Long sizeId;
    private String deviceCode;

    @BeforeEach
    void setUp() throws Exception {
        kitchenName = "E2E Cook " + UUID.randomUUID();
        adminName = "E2E Boss " + UUID.randomUUID();
        createStaff(kitchenName, Role.KITCHEN);
        createStaff(adminName, Role.ADMIN);
        kitchen = signIn(kitchenName);
        admin = signIn(adminName);

        table = new Table();
        table.setTableNumber("E2E-" + UUID.randomUUID().toString().substring(0, 8));
        table.setRoom("Main room");
        table.setSeats(4);
        table = tableRepository.saveAndFlush(table);
        tableIds.add(table.getId());

        // The admin pairs the table's device, as in the Tables & devices screen.
        MvcResult paired = mockMvc.perform(post("/api/tables/" + table.getId() + "/pair").session(admin))
                .andExpect(status().isOk())
                .andReturn();
        deviceCode = JsonPath.read(paired.getResponse().getContentAsString(), "$.pairedDeviceId");

        mealName = "E2E Soup " + UUID.randomUUID().toString().substring(0, 8);
        sizeId = createMealSize(mealName, "10.00");
    }

    @AfterEach
    void tearDown() {
        orderIds.forEach(orderRepository::deleteById);
        mealIds.forEach(mealRepository::deleteById);
        categoryIds.forEach(categoryRepository::deleteById);
        tableIds.forEach(tableRepository::deleteById);
        staffIds.forEach(staffAccountRepository::deleteById);
    }

    // ------------------------------------------------------------------ the happy path

    @Test
    void anOrderTravelsFromTheGuestThroughKitchenAndHallToServedAndIsFullyAudited() throws Exception {
        // Guest: the meal is on the menu, and the cart preview gives a price.
        mockMvc.perform(get("/api/guest/menu").param("language", "EN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].meals[*].name").value(hasItem(mealName)));
        mockMvc.perform(post("/api/guest/device/claim").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + deviceCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableNumber").value(table.getTableNumber()));
        mockMvc.perform(post("/api/guest/cart/quote").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"sizeId\":" + sizeId + ",\"quantity\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(20.0))
                .andExpect(jsonPath("$.taxAmount").value(3.8))
                .andExpect(jsonPath("$.total").value(23.8));

        // Guest submits: the server's totals are the same as the preview.
        MvcResult submitted = submit(2, "no onions")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.subtotal").value(20.0))
                .andExpect(jsonPath("$.taxAmount").value(3.8))
                .andExpect(jsonPath("$.total").value(23.8))
                .andReturn();
        long orderId = ((Number) JsonPath.read(submitted.getResponse().getContentAsString(), "$.orderId")).longValue();
        int orderNumber = JsonPath.read(submitted.getResponse().getContentAsString(), "$.orderNumber");
        orderIds.add(orderId);

        // Kitchen: it is on the board in "New" with what the guest ordered, and the hall board does not show it yet.
        mockMvc.perform(get("/api/kitchen/orders").session(kitchen))
                .andExpect(status().isOk())
                .andExpect(jsonPath(onBoard(orderId) + ".status").value(contains("SUBMITTED")))
                .andExpect(jsonPath(onBoard(orderId) + ".items[0].name").value(contains(mealName)))
                .andExpect(jsonPath(onBoard(orderId) + ".items[0].quantity").value(contains(2)))
                .andExpect(jsonPath(onBoard(orderId) + ".items[0].note").value(contains("no onions")))
                .andExpect(jsonPath(onBoard(orderId) + ".nextStatus").value(contains("PREPARING")));
        hallShows(orderNumber, false, false);
        guestSees(orderId, "SUBMITTED");

        // Kitchen starts it: In preparation on the hall board, with its table.
        advance(orderId, "PREPARING").andExpect(status().isOk()).andExpect(jsonPath("$.nextStatus").value("READY"));
        hallShows(orderNumber, true, false);
        mockMvc.perform(get("/api/hall/orders"))
                .andExpect(jsonPath("$.preparing[?(@.orderNumber == " + orderNumber + ")].tableNumber")
                        .value(contains(table.getTableNumber())));
        guestSees(orderId, "PREPARING");

        // Ready: it moves across to Ready.
        advance(orderId, "READY").andExpect(status().isOk());
        hallShows(orderNumber, false, true);
        guestSees(orderId, "READY");

        // Picked up: it leaves the boards, and the guest sees it served with the whole trail.
        advance(orderId, "SERVED").andExpect(status().isOk()).andExpect(jsonPath("$.nextStatus").isEmpty());
        hallShows(orderNumber, false, false);
        mockMvc.perform(get("/api/kitchen/orders").session(kitchen))
                .andExpect(jsonPath(onBoard(orderId)).isEmpty());
        mockMvc.perform(get("/api/guest/orders/" + orderId).header("X-Device-Code", deviceCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SERVED"))
                .andExpect(jsonPath("$.history[*].status").value(contains("SUBMITTED", "PREPARING", "READY", "SERVED")))
                // A guest never learns which staff member did what.
                .andExpect(jsonPath("$.history[0].changedBy").doesNotExist());

        // Admin: the audit trail has every step, its time and who made it.
        mockMvc.perform(get("/api/admin/orders/history").param("orderNumber", String.valueOf(orderNumber)).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(1))
                .andExpect(jsonPath("$.orders[0].entries[*].stage")
                        .value(contains("SUBMITTED", "PREPARING", "READY", "SERVED")))
                .andExpect(jsonPath("$.orders[0].entries[0].actor").isEmpty())
                .andExpect(jsonPath("$.orders[0].entries[1].actor").value(kitchenName))
                .andExpect(jsonPath("$.orders[0].entries[2].actor").value(kitchenName))
                .andExpect(jsonPath("$.orders[0].entries[3].actor").value(kitchenName));
    }

    // ------------------------------------------------------------------ the cancel path

    @Test
    void anAdminCancellationReachesTheKitchenUntilAcknowledgedAndIsAudited() throws Exception {
        MvcResult submitted = submit(1, null).andExpect(status().isCreated()).andReturn();
        long orderId = ((Number) JsonPath.read(submitted.getResponse().getContentAsString(), "$.orderId")).longValue();
        int orderNumber = JsonPath.read(submitted.getResponse().getContentAsString(), "$.orderNumber");
        orderIds.add(orderId);
        advance(orderId, "PREPARING").andExpect(status().isOk());
        hallShows(orderNumber, true, false);

        mockMvc.perform(post("/api/admin/orders/" + orderId + "/cancel").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // It is off the hall board and the active board, and on the kitchen's cancelled banner.
        hallShows(orderNumber, false, false);
        mockMvc.perform(get("/api/kitchen/orders").session(kitchen)).andExpect(jsonPath(onBoard(orderId)).isEmpty());
        mockMvc.perform(get("/api/kitchen/orders/cancelled").session(kitchen))
                .andExpect(jsonPath("$[?(@.orderId == " + orderId + ")].tableNumber").value(contains(table.getTableNumber())));
        guestSees(orderId, "CANCELLED");

        // Acknowledging clears the banner; nothing about the order's status changes.
        mockMvc.perform(post("/api/kitchen/orders/" + orderId + "/acknowledge-cancellation").session(kitchen))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/kitchen/orders/cancelled").session(kitchen))
                .andExpect(jsonPath("$[?(@.orderId == " + orderId + ")]").isEmpty());
        guestSees(orderId, "CANCELLED");

        mockMvc.perform(get("/api/admin/orders/history").param("orderNumber", String.valueOf(orderNumber)).session(admin))
                .andExpect(jsonPath("$.orders[0].entries[*].stage").value(contains("SUBMITTED", "PREPARING", "CANCELLED")))
                .andExpect(jsonPath("$.orders[0].entries[2].actor").value(adminName))
                .andExpect(jsonPath("$.orders[0].cancellationAcknowledgement.by").value(kitchenName));
    }

    // ------------------------------------------------------------------ the rules, inside the flow

    @Test
    void theStateMachineRefusesIllegalStepsAndRolesAreEnforcedPerScreen() throws Exception {
        long orderId = submittedOrderId();

        // One state machine: a step cannot be skipped, and the refusal leaves the order where it was.
        advance(orderId, "READY").andExpect(status().isConflict());
        advance(orderId, "SERVED").andExpect(status().isConflict());
        mockMvc.perform(get("/api/kitchen/orders").session(kitchen))
                .andExpect(jsonPath(onBoard(orderId) + ".status").value(contains("SUBMITTED")));

        // Roles per screen: the kitchen cannot cancel, by either door; nobody anonymous reaches staff screens.
        advance(orderId, "CANCELLED").andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/cancel").session(kitchen)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/orders/history").session(kitchen)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/kitchen/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/orders/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/kitchen/orders").session(kitchen))
                .andExpect(jsonPath(onBoard(orderId) + ".status").value(contains("SUBMITTED")));
    }

    @Test
    void aTableOnlyReadsItsOwnOrdersAndAnUnpairedDeviceReadsNone() throws Exception {
        long orderId = submittedOrderId();

        Table other = new Table();
        other.setTableNumber("E2E-OTHER-" + UUID.randomUUID().toString().substring(0, 6));
        other.setRoom("Main room");
        other.setSeats(2);
        other = tableRepository.saveAndFlush(other);
        tableIds.add(other.getId());
        MvcResult pairedOther = mockMvc.perform(post("/api/tables/" + other.getId() + "/pair").session(admin))
                .andExpect(status().isOk())
                .andReturn();
        String otherCode = JsonPath.read(pairedOther.getResponse().getContentAsString(), "$.pairedDeviceId");

        // Another table's device sees "not found", exactly as for an order that does not exist.
        mockMvc.perform(get("/api/guest/orders/" + orderId).header("X-Device-Code", otherCode))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/guest/orders/987654321").header("X-Device-Code", otherCode))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/guest/orders/" + orderId).header("X-Device-Code", "NOPE00"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/guest/orders/" + orderId).header("X-Device-Code", deviceCode))
                .andExpect(status().isOk());
    }

    @Test
    void theServerPricesTheOrderItselfAndAnOrderKeepsWhatWasOrdered() throws Exception {
        // A client that tries to name its own price or total is ignored: only sizes and quantities count.
        MvcResult submitted = mockMvc.perform(post("/api/guest/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceCode\":\"" + deviceCode + "\",\"language\":\"EN\",\"paymentMethod\":\"CASH\","
                                + "\"total\":0.01,\"subtotal\":0.01,\"tableId\":1,"
                                + "\"items\":[{\"sizeId\":" + sizeId + ",\"quantity\":2,\"unitPrice\":0.01}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(23.8))
                .andReturn();
        long orderId = ((Number) JsonPath.read(submitted.getResponse().getContentAsString(), "$.orderId")).longValue();
        orderIds.add(orderId);

        // The menu changes afterwards: new price, new name.
        transactionTemplate.executeWithoutResult(tx -> {
            Meal meal = mealRepository.findById(mealIds.get(0)).orElseThrow();
            meal.getSizes().get(0).setPrice(new BigDecimal("99.00"));
            meal.getTranslations().get(0).setName("Renamed after the order");
        });

        // New quotes use the new price, but the placed order did not move.
        mockMvc.perform(post("/api/guest/cart/quote").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"sizeId\":" + sizeId + ",\"quantity\":2}]}"))
                .andExpect(jsonPath("$.subtotal").value(198.0));
        mockMvc.perform(get("/api/guest/orders/" + orderId).header("X-Device-Code", deviceCode))
                .andExpect(jsonPath("$.subtotal").value(20.0))
                .andExpect(jsonPath("$.total").value(23.8));
        mockMvc.perform(get("/api/kitchen/orders").session(kitchen))
                .andExpect(jsonPath(onBoard(orderId) + ".items[0].name").value(contains(mealName)))
                .andExpect(jsonPath(onBoard(orderId) + ".items[0].name").value(not(contains("Renamed after the order"))));
    }

    @Test
    void aRejectedSubmissionCreatesNoOrderAndConsumesNoOrderNumber() throws Exception {
        long first = submittedOrderId();
        int firstNumber = orderNumberOf(first);

        // An empty cart is refused; the next real order's number follows directly on from the last one.
        mockMvc.perform(post("/api/guest/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceCode\":\"" + deviceCode + "\",\"language\":\"EN\",\"paymentMethod\":\"CASH\",\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/guest/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceCode\":\"" + deviceCode + "\",\"language\":\"EN\",\"paymentMethod\":\"CASH\","
                                + "\"items\":[{\"sizeId\":987654321,\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());

        long second = submittedOrderId();
        assertEquals(firstNumber + 1, orderNumberOf(second), "a refused submission must not use up an order number");
    }

    // ------------------------------------------------------------------ helpers

    private StaffAccount createStaff(String name, Role role) {
        StaffAccount account = new StaffAccount();
        account.setName(name);
        account.setRole(role);
        account.setPinHash(passwordEncoder.encode(PIN));
        StaffAccount saved = staffAccountRepository.saveAndFlush(account);
        staffIds.add(saved.getId());
        return saved;
    }

    private MockHttpSession signIn(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"pin\":\"" + PIN + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Long createMealSize(String nameEn, String price) {
        // A category always has a name in at least one language (the admin API requires it).
        Category category = new Category();
        category.setSortOrder(1);
        CategoryTranslation categoryName = new CategoryTranslation();
        categoryName.setCategory(category);
        categoryName.setLanguage(Language.EN);
        categoryName.setName("E2E Category");
        category.getTranslations().add(categoryName);
        category = categoryRepository.saveAndFlush(category);
        categoryIds.add(category.getId());

        Meal meal = new Meal();
        meal.setCategory(category);
        meal.setAvailable(true);
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
        mealIds.add(meal.getId());
        return meal.getSizes().get(0).getId();
    }

    private ResultActions submit(int quantity, String note) throws Exception {
        String noteJson = note == null ? "" : ",\"note\":\"" + note + "\"";
        return mockMvc.perform(post("/api/guest/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceCode\":\"" + deviceCode + "\",\"language\":\"EN\",\"paymentMethod\":\"CASH\","
                        + "\"items\":[{\"sizeId\":" + sizeId + ",\"quantity\":" + quantity + noteJson + "}]}"));
    }

    private long submittedOrderId() throws Exception {
        MvcResult result = submit(1, null).andExpect(status().isCreated()).andReturn();
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.orderId")).longValue();
        orderIds.add(id);
        return id;
    }

    private int orderNumberOf(long orderId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/guest/orders/" + orderId).header("X-Device-Code", deviceCode))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.orderNumber");
    }

    private ResultActions advance(long orderId, String target) throws Exception {
        return mockMvc.perform(post("/api/kitchen/orders/" + orderId + "/transition").session(kitchen)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + target + "\"}"));
    }

    private void guestSees(long orderId, String expectedStatus) throws Exception {
        mockMvc.perform(get("/api/guest/orders/" + orderId).header("X-Device-Code", deviceCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    private void hallShows(int orderNumber, boolean inPreparation, boolean ready) throws Exception {
        mockMvc.perform(get("/api/hall/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preparing[*].orderNumber")
                        .value(inPreparation ? hasItem(orderNumber) : not(hasItem(orderNumber))))
                .andExpect(jsonPath("$.ready[*].orderNumber").value(ready ? hasItem(orderNumber) : not(hasItem(orderNumber))));
    }

    private static String onBoard(long orderId) {
        return "$[?(@.orderId == " + orderId + ")]";
    }

}
