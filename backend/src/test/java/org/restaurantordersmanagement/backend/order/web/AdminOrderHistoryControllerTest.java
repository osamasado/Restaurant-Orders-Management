package org.restaurantordersmanagement.backend.order.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.kitchen.service.KitchenOrderService;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderItem;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.order.service.OrderStateMachineService;
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

/**
 * The admin audit view reads what the state machine wrote, so the orders here
 * are moved through the real OrderStateMachineService by real staff accounts.
 *
 * Not @Transactional - see CategoryControllerTest's javadoc for why.
 * tearDown() deletes tracked ids instead: orders first (their history goes
 * with them and references staff), then tables, then staff.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminOrderHistoryControllerTest {

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
    private KitchenOrderService kitchenOrderService;

    private final List<Long> createdOrderIds = new ArrayList<>();
    private final List<Long> createdTableIds = new ArrayList<>();
    private final List<Long> createdStaffIds = new ArrayList<>();

    private StaffAccount cook;
    private StaffAccount boss;

    @BeforeEach
    void setUp() {
        cook = staff("History Cook-" + UUID.randomUUID(), Role.KITCHEN);
        boss = staff("History Boss-" + UUID.randomUUID(), Role.ADMIN);
    }

    @AfterEach
    void tearDown() {
        createdOrderIds.forEach(orderRepository::deleteById);
        createdTableIds.forEach(tableRepository::deleteById);
        createdStaffIds.forEach(staffAccountRepository::deleteById);
    }

    private StaffAccount staff(String name, Role role) {
        StaffAccount account = new StaffAccount();
        account.setName(name);
        account.setRole(role);
        StaffAccount saved = staffAccountRepository.saveAndFlush(account);
        createdStaffIds.add(saved.getId());
        return saved;
    }

    /** A SUBMITTED order, the way a guest's submit leaves it: no staff member behind that first change. */
    private Order submittedOrder() {
        Table table = new Table();
        table.setTableNumber("AH-" + UUID.randomUUID());
        table.setRoom("Main Room");
        table.setSeats(2);
        table = tableRepository.saveAndFlush(table);
        createdTableIds.add(table.getId());

        Order order = new Order();
        order.setTable(table);
        OrderItem item = new OrderItem();
        item.setName("Pumpkin Soup");
        item.setSize("small");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("4.30"));
        item.setOrder(order);
        order.getItems().add(item);

        Order submitted = orderStateMachineService.transition(order, OrderStatus.SUBMITTED, null);
        createdOrderIds.add(submitted.getId());
        return submitted;
    }

    private ResultActions history(String query) throws Exception {
        return mockMvc.perform(get("/api/admin/orders/history" + query).with(user("admin").roles("ADMIN")));
    }

    @Test
    void anonymousAndNonAdminRolesCannotReadTheHistory() throws Exception {
        mockMvc.perform(get("/api/admin/orders/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/orders/history").with(user("cook").roles("KITCHEN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void showsEveryStageWithItsTimeAndWhoMadeTheChange() throws Exception {
        Order order = submittedOrder();
        Order preparing = orderStateMachineService.transition(order, OrderStatus.PREPARING, cook);
        Order ready = orderStateMachineService.transition(preparing, OrderStatus.READY, cook);
        orderStateMachineService.transition(ready, OrderStatus.SERVED, boss);

        String body = history("?orderNumber=" + order.getDisplayNumber())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(1))
                .andExpect(jsonPath("$.orders[0].orderId").value(order.getId()))
                .andExpect(jsonPath("$.orders[0].orderNumber").value(order.getDisplayNumber()))
                .andExpect(jsonPath("$.orders[0].tableNumber").value(order.getTable().getTableNumber()))
                .andExpect(jsonPath("$.orders[0].status").value("SERVED"))
                .andExpect(jsonPath("$.orders[0].entries[*].stage")
                        .value(contains("SUBMITTED", "PREPARING", "READY", "SERVED")))
                // A guest's own submit has no staff member behind it.
                .andExpect(jsonPath("$.orders[0].entries[0].actor").isEmpty())
                .andExpect(jsonPath("$.orders[0].entries[1].actor").value(cook.getName()))
                .andExpect(jsonPath("$.orders[0].entries[2].actor").value(cook.getName()))
                .andExpect(jsonPath("$.orders[0].entries[3].actor").value(boss.getName()))
                .andExpect(jsonPath("$.orders[0].cancellationAcknowledgement").isEmpty())
                .andReturn().getResponse().getContentAsString();

        List<String> times = JsonPath.read(body, "$.orders[0].entries[*].changedAt");
        assertEquals(4, times.size());
        for (int i = 1; i < times.size(); i++) {
            assertFalse(Instant.parse(times.get(i)).isBefore(Instant.parse(times.get(i - 1))), "entries are oldest first");
        }
    }

    private static void assertEquals(int expected, int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    @Test
    void aCancelledOrderShowsWhoCancelledItAndWhoSawTheCancellation() throws Exception {
        Order order = orderStateMachineService.transition(submittedOrder(), OrderStatus.CANCELLED, boss);
        kitchenOrderService.acknowledgeCancellation(order.getId(), cook.getId());

        history("?orderNumber=" + order.getDisplayNumber())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[0].entries[*].stage").value(contains("SUBMITTED", "CANCELLED")))
                .andExpect(jsonPath("$.orders[0].entries[1].actor").value(boss.getName()))
                .andExpect(jsonPath("$.orders[0].cancellationAcknowledgement.by").value(cook.getName()))
                .andExpect(jsonPath("$.orders[0].cancellationAcknowledgement.at").isNotEmpty());
    }

    @Test
    void aCancellationNobodyHasAcknowledgedYetHasNoAcknowledgement() throws Exception {
        Order order = orderStateMachineService.transition(submittedOrder(), OrderStatus.CANCELLED, boss);

        history("?orderNumber=" + order.getDisplayNumber())
                .andExpect(jsonPath("$.orders[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.orders[0].cancellationAcknowledgement").isEmpty());
    }

    @Test
    void neverExposesAStaffAccountBeyondItsName() throws Exception {
        Order order = orderStateMachineService.transition(submittedOrder(), OrderStatus.PREPARING, cook);

        String body = history("?orderNumber=" + order.getDisplayNumber())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.toLowerCase().contains("pin"), "no PIN data in: " + body);
        assertFalse(body.contains("role"), "no staff role in: " + body);
        assertFalse(body.contains("\"id\""), "no staff id in: " + body);
        assertTrue(body.contains(cook.getName()));
    }

    @Test
    void searchingByOrderNumberFindsOnlyThatOrderAndAnUnknownNumberFindsNone() throws Exception {
        Order wanted = submittedOrder();
        submittedOrder();

        history("?orderNumber=" + wanted.getDisplayNumber())
                .andExpect(jsonPath("$.orders.length()").value(1))
                .andExpect(jsonPath("$.totalOrders").value(1))
                .andExpect(jsonPath("$.orders[0].orderNumber").value(wanted.getDisplayNumber()));

        history("?orderNumber=99999999")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(0))
                .andExpect(jsonPath("$.totalOrders").value(0));
    }

    @Test
    void newestOrdersComeFirstAndThePageSizeIsHonoured() throws Exception {
        Order first = submittedOrder();
        Order second = submittedOrder();
        Order third = submittedOrder();

        history("?page=0&size=2")
                .andExpect(jsonPath("$.orders.length()").value(2))
                .andExpect(jsonPath("$.orders[0].orderNumber").value(third.getDisplayNumber()))
                .andExpect(jsonPath("$.orders[1].orderNumber").value(second.getDisplayNumber()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalPages").value(greaterThanOrEqualTo(2)));

        history("?page=1&size=2")
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.orders[0].orderNumber").value(first.getDisplayNumber()));
    }

    @Test
    void rejectsAnInvalidPageOrSize() throws Exception {
        history("?page=-1").andExpect(status().isBadRequest());
        history("?size=0").andExpect(status().isBadRequest());
        history("?size=101").andExpect(status().isBadRequest());
    }

}
