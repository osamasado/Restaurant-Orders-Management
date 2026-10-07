package org.restaurantordersmanagement.backend.order.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderItem;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.order.service.OrderStateMachineService;
import org.restaurantordersmanagement.backend.settings.model.PaymentMethod;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.persistence.EntityManagerFactory;

/**
 * Not @Transactional - see CategoryControllerTest's javadoc for why.
 * Other tests may leave orders behind, so assertions look only at the orders
 * this test created (found again by id), never at the list as a whole.
 * Hibernate statistics are on so one test can count the statements a request makes.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminOrderListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Autowired
    private OrderStateMachineService orderStateMachineService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final List<Long> createdOrderIds = new ArrayList<>();
    private final List<Long> createdTableIds = new ArrayList<>();
    private final List<Long> createdStaffIds = new ArrayList<>();

    private StaffAccount cook;

    @BeforeEach
    void setUp() {
        StaffAccount account = new StaffAccount();
        account.setName("List Cook-" + UUID.randomUUID());
        account.setRole(Role.KITCHEN);
        cook = staffAccountRepository.saveAndFlush(account);
        createdStaffIds.add(cook.getId());
    }

    @AfterEach
    void tearDown() {
        createdOrderIds.forEach(orderRepository::deleteById);
        createdTableIds.forEach(tableRepository::deleteById);
        createdStaffIds.forEach(staffAccountRepository::deleteById);
    }

    private Order draftOrder(int itemCount) {
        Table table = new Table();
        table.setTableNumber("AL-" + UUID.randomUUID());
        table.setRoom("Main Room");
        table.setSeats(2);
        table = tableRepository.saveAndFlush(table);
        createdTableIds.add(table.getId());

        Order order = new Order();
        order.setTable(table);
        order.setPaymentMethod(PaymentMethod.PAYPAL);
        for (int i = 1; i <= itemCount; i++) {
            OrderItem item = new OrderItem();
            item.setName("Dish " + i);
            item.setSize("regular");
            item.setQuantity(i);
            item.setUnitPrice(new BigDecimal("4.30"));
            item.setNote(i == 1 ? "no onions" : null);
            item.setOrder(order);
            order.getItems().add(item);
        }
        return order;
    }

    private Order submittedOrder(int itemCount) {
        Order submitted = orderStateMachineService.transition(draftOrder(itemCount), OrderStatus.SUBMITTED, null);
        createdOrderIds.add(submitted.getId());
        return submitted;
    }

    private ResultActions list(String query) throws Exception {
        return mockMvc.perform(get("/api/admin/orders" + query).with(user("admin").roles("ADMIN")));
    }

    private Map<String, Object> rowOf(String json, Long orderId) {
        List<Map<String, Object>> rows = JsonPath.read(json, "$.orders[?(@.orderId == " + orderId + ")]");
        assertEquals(1, rows.size(), "order " + orderId + " should be in the list exactly once");
        return rows.getFirst();
    }

    private List<Long> idsInListOrder(String json, List<Long> mine) {
        List<Number> all = JsonPath.read(json, "$.orders[*].orderId");
        return all.stream().map(Number::longValue).filter(mine::contains).toList();
    }

    @Test
    void onlyAdminCanListOrders() throws Exception {
        mockMvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
        for (String role : List.of("KITCHEN", "WAITER", "CASHIER")) {
            mockMvc.perform(get("/api/admin/orders").with(user("someone").roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void listsPlacedOrdersNewestFirstWithItemsPaymentAndTotal() throws Exception {
        Order first = submittedOrder(1);
        Order second = submittedOrder(2);
        Order third = submittedOrder(1);

        String json = list("?size=100").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertEquals(List.of(third.getId(), second.getId(), first.getId()),
                idsInListOrder(json, List.of(first.getId(), second.getId(), third.getId())));

        Map<String, Object> row = rowOf(json, second.getId());
        assertEquals("SUBMITTED", row.get("status"));
        assertEquals("PAYPAL", row.get("paymentMethod"));
        assertEquals(second.getTable().getTableNumber(), row.get("tableNumber"));
        assertEquals(second.getOrderNumber(), row.get("orderNumber"));
        assertEquals(0, second.getTotal().compareTo(new BigDecimal(row.get("total").toString())));
        assertTrue(row.get("placedAt") != null);
        List<Map<String, Object>> items = JsonPath.read(row, "$.items");
        assertEquals(2, items.size());
        assertEquals("Dish 1", items.get(0).get("name"));
        assertEquals("no onions", items.get(0).get("note"));
        assertEquals(1, items.get(0).get("quantity"));
        assertEquals("Dish 2", items.get(1).get("name"));
        assertEquals(2, items.get(1).get("quantity"));
    }

    @Test
    void theServerSaysWhichStatusesAreLegalNext() throws Exception {
        Order submitted = submittedOrder(1);
        Order preparing = orderStateMachineService.transition(submittedOrder(1), OrderStatus.PREPARING, cook);
        Order ready = orderStateMachineService.transition(
                orderStateMachineService.transition(submittedOrder(1), OrderStatus.PREPARING, cook),
                OrderStatus.READY, cook);
        Order served = orderStateMachineService.transition(
                orderStateMachineService.transition(
                        orderStateMachineService.transition(submittedOrder(1), OrderStatus.PREPARING, cook),
                        OrderStatus.READY, cook),
                OrderStatus.SERVED, cook);
        Order cancelled = orderStateMachineService.transition(submittedOrder(1), OrderStatus.CANCELLED, cook);

        String json = list("?size=100").andReturn().getResponse().getContentAsString();

        assertEquals(List.of("PREPARING"), rowOf(json, submitted.getId()).get("nextStatuses"));
        assertEquals(List.of("READY"), rowOf(json, preparing.getId()).get("nextStatuses"));
        assertEquals(List.of("SERVED"), rowOf(json, ready.getId()).get("nextStatuses"));
        assertEquals(List.of(), rowOf(json, served.getId()).get("nextStatuses"));
        assertEquals(List.of(), rowOf(json, cancelled.getId()).get("nextStatuses"));
        assertEquals("CANCELLED", rowOf(json, cancelled.getId()).get("status"));
    }

    @Test
    void draftsAreNotListed() throws Exception {
        Order draft = orderRepository.saveAndFlush(draftOrder(1));
        createdOrderIds.add(draft.getId());
        Order neverSubmitted = orderRepository.saveAndFlush(draftOrder(1));
        createdOrderIds.add(neverSubmitted.getId());
        orderStateMachineService.transition(neverSubmitted, OrderStatus.CANCELLED, cook);

        String json = list("?size=100").andReturn().getResponse().getContentAsString();

        List<Number> ids = JsonPath.read(json, "$.orders[*].orderId");
        assertFalse(ids.stream().map(Number::longValue).anyMatch(draft.getId()::equals));
        assertFalse(ids.stream().map(Number::longValue).anyMatch(neverSubmitted.getId()::equals));
    }

    @Test
    void theRowCarriesNothingBeyondWhatTheScreenShows() throws Exception {
        Order order = orderStateMachineService.transition(submittedOrder(1), OrderStatus.PREPARING, cook);

        String json = list("?size=100").andReturn().getResponse().getContentAsString();

        assertEquals(
                Set.of("orderId", "orderNumber", "tableNumber", "status", "placedAt", "paymentMethod", "total",
                        "items", "nextStatuses"),
                rowOf(json, order.getId()).keySet());
        // No staff names, ids or PIN data anywhere in the response.
        assertFalse(json.toLowerCase().contains("pin"));
        assertFalse(json.contains(cook.getName()));
    }

    @Test
    void pagesAreCutByTheDatabaseAndCarryTheEnvelope() throws Exception {
        submittedOrder(1);
        submittedOrder(1);
        submittedOrder(1);

        String firstPage = list("?page=0&size=2").andReturn().getResponse().getContentAsString();
        assertEquals(2, ((List<?>) JsonPath.read(firstPage, "$.orders")).size());
        assertEquals(0, (int) JsonPath.read(firstPage, "$.page"));
        assertTrue(((Number) JsonPath.read(firstPage, "$.totalOrders")).longValue() >= 3);
        assertTrue((int) JsonPath.read(firstPage, "$.totalPages") >= 2);

        String secondPage = list("?page=1&size=2").andReturn().getResponse().getContentAsString();
        List<Number> firstIds = JsonPath.read(firstPage, "$.orders[*].orderId");
        List<Number> secondIds = JsonPath.read(secondPage, "$.orders[*].orderId");
        assertTrue(secondIds.stream().noneMatch(firstIds::contains));
    }

    @Test
    void anInvalidPageOrSizeIsABadRequest() throws Exception {
        list("?page=-1").andExpect(status().isBadRequest());
        list("?size=0").andExpect(status().isBadRequest());
        list("?size=101").andExpect(status().isBadRequest());
    }

    @Test
    void theNumberOfStatementsDoesNotGrowWithTheNumberOfOrders() throws Exception {
        for (int i = 0; i < 6; i++) {
            submittedOrder(3);
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        list("?size=100").andExpect(status().isOk());

        // The orders with their table, the count, and the lines of every order together.
        long statements = statistics.getPrepareStatementCount();
        assertTrue(statements <= 3, "expected at most 3 statements for the page, got " + statements);
    }

    @Test
    void filtersByStatus() throws Exception {
        Order submitted = submittedOrder(1);
        Order preparing = orderStateMachineService.transition(submittedOrder(1), OrderStatus.PREPARING, cook);
        Order cancelled = orderStateMachineService.transition(submittedOrder(1), OrderStatus.CANCELLED, cook);
        List<Long> mine = List.of(submitted.getId(), preparing.getId(), cancelled.getId());

        String json = list("?size=100&status=PREPARING").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertEquals(List.of(preparing.getId()), idsInListOrder(json, mine));
        List<String> statuses = JsonPath.read(json, "$.orders[*].status");
        assertTrue(statuses.stream().allMatch("PREPARING"::equals), "only PREPARING rows expected, got " + statuses);

        String cancelledOnly = list("?size=100&status=CANCELLED").andReturn().getResponse().getContentAsString();
        assertEquals(List.of(cancelled.getId()), idsInListOrder(cancelledOnly, mine));
    }

    @Test
    void aStatusFilterNoOrderHasGivesAnEmptyList() throws Exception {
        submittedOrder(1);

        String json = list("?size=100&status=DRAFT").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertEquals(0, ((List<?>) JsonPath.read(json, "$.orders")).size());
        assertEquals(0, ((Number) JsonPath.read(json, "$.totalOrders")).intValue());
    }

    @Test
    void anUnknownStatusIsABadRequest() throws Exception {
        list("?status=TELEPORTED").andExpect(status().isBadRequest());
    }

    @Test
    void filtersByTimeRangeWithFromIncludedAndToExcluded() throws Exception {
        Order first = submittedOrder(1);
        Order second = submittedOrder(1);
        Order third = submittedOrder(1);
        List<Long> mine = List.of(first.getId(), second.getId(), third.getId());
        // The database keeps microseconds, so read the placed times back instead of using the in-memory ones.
        Instant secondPlaced = orderRepository.findById(second.getId()).orElseThrow().getPlacedAt();
        Instant thirdPlaced = orderRepository.findById(third.getId()).orElseThrow().getPlacedAt();

        String onlySecond = list("?size=100&from=" + secondPlaced + "&to=" + thirdPlaced)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(List.of(second.getId()), idsInListOrder(onlySecond, mine));

        String fromSecond = list("?size=100&from=" + secondPlaced).andReturn().getResponse().getContentAsString();
        assertEquals(List.of(third.getId(), second.getId()), idsInListOrder(fromSecond, mine));

        String beforeThird = list("?size=100&to=" + thirdPlaced).andReturn().getResponse().getContentAsString();
        assertEquals(List.of(second.getId(), first.getId()), idsInListOrder(beforeThird, mine));
    }

    @Test
    void aRangeThatEndsBeforeItStartsIsABadRequest() throws Exception {
        Instant now = Instant.now();

        list("?from=" + now + "&to=" + now.minusSeconds(60)).andExpect(status().isBadRequest());
        list("?from=" + now + "&to=" + now).andExpect(status().isBadRequest());
    }

    @Test
    void statusAndRangeCombineAndTheCountFollowsTheFilter() throws Exception {
        Order preparing = orderStateMachineService.transition(submittedOrder(1), OrderStatus.PREPARING, cook);
        submittedOrder(1);
        Instant placed = orderRepository.findById(preparing.getId()).orElseThrow().getPlacedAt();

        String json = list("?size=100&status=PREPARING&from=" + placed + "&to=" + placed.plusSeconds(60))
                .andReturn().getResponse().getContentAsString();

        assertEquals(List.of(preparing.getId()), idsInListOrder(json, List.of(preparing.getId())));
        List<String> statuses = JsonPath.read(json, "$.orders[*].status");
        assertTrue(statuses.stream().allMatch("PREPARING"::equals));
        assertEquals(statuses.size(), ((Number) JsonPath.read(json, "$.totalOrders")).intValue());
    }

}
