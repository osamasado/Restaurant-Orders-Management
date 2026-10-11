package org.restaurantordersmanagement.backend.kitchen.web;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderItem;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.model.OrderStatusHistory;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.order.service.OrderStateMachineService;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Not @Transactional - see CategoryControllerTest's javadoc for why.
 * tearDown() deletes tracked ids instead: orders first (they reference
 * tables and, via history, staff), then tables, then staff.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class KitchenOrderControllerTest {

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
    private TransactionTemplate transactionTemplate;

    private final List<Long> createdOrderIds = new ArrayList<>();
    private final List<Long> createdTableIds = new ArrayList<>();
    private final List<Long> createdStaffIds = new ArrayList<>();

    private StaffAccount kitchenStaff;

    @BeforeEach
    void setUp() {
        StaffAccount staff = new StaffAccount();
        staff.setName("Kitchen-" + UUID.randomUUID());
        staff.setRole(Role.KITCHEN);
        kitchenStaff = staffAccountRepository.saveAndFlush(staff);
        createdStaffIds.add(kitchenStaff.getId());
    }

    @AfterEach
    void tearDown() {
        createdOrderIds.forEach(orderRepository::deleteById);
        createdTableIds.forEach(tableRepository::deleteById);
        createdStaffIds.forEach(staffAccountRepository::deleteById);
    }

    /** A SUBMITTED order with one item, the way a guest's submit leaves it. */
    private Order submittedOrder() {
        Table table = new Table();
        table.setTableNumber("K-" + UUID.randomUUID());
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
        item.setNote("No croutons");
        item.setOrder(order);
        order.getItems().add(item);

        Order submittedOrder = orderStateMachineService.transition(order, OrderStatus.SUBMITTED, null);
        createdOrderIds.add(submittedOrder.getId());
        return submittedOrder;
    }

    /** Logged in as the real kitchen account - a StaffPrincipal, like after /api/staff/login. */
    private RequestPostProcessor asKitchen() {
        return user(new StaffPrincipal(kitchenStaff));
    }

    private ResultActions advance(Long orderId, String status) throws Exception {
        return mockMvc.perform(post("/api/kitchen/orders/" + orderId + "/transition")
                .with(asKitchen())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }

    @Test
    void anonymousCannotSeeTheBoard() throws Exception {
        mockMvc.perform(get("/api/kitchen/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startRecordsTheKitchenStaffMemberAsTheActor() throws Exception {
        Order order = submittedOrder();

        advance(order.getId(), "PREPARING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.nextStatus").value("READY"));

        transactionTemplate.executeWithoutResult(tx -> {
            Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
            OrderStatusHistory last = reloaded.getHistory().getLast();
            assertEquals(OrderStatus.PREPARING, last.getStatus());
            assertEquals(kitchenStaff.getId(), last.getChangedBy().getId());
        });
    }

    @Test
    void waiterCannotSeeTheBoard() throws Exception {
        mockMvc.perform(get("/api/kitchen/orders")
                .with(user("waiter").roles("WAITER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void boardListsOnlyActiveOrdersWithTheirNextStep() throws Exception {
        Order firstOrder = submittedOrder();
        Order secondOrder = submittedOrder();

        advance(firstOrder.getId(), "PREPARING").andExpect(status().isOk());
        advance(firstOrder.getId(), "READY").andExpect(status().isOk());
        advance(firstOrder.getId(), "SERVED").andExpect(status().isOk());

        mockMvc.perform(get("/api/kitchen/orders").with(asKitchen()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.orderId == " + secondOrder.getId() + ")].nextStatus")
                        .value(contains("PREPARING")))
                .andExpect(jsonPath("$[?(@.orderId == " + firstOrder.getId() + ")]").isEmpty());
    }

    @Test
    void skippingAStepIsAConflict() throws Exception {
        Order order = submittedOrder();
        advance(order.getId(), "READY").andExpect(status().isConflict());
    }

    @Test
    void kitchenCannotCancel() throws Exception {
        Order order = submittedOrder();
        advance(order.getId(), "CANCELLED").andExpect(status().isForbidden());
    }

    @Test
    void unknownOrderIsNotFound() throws Exception {
        advance(9999999L, "PREPARING").andExpect(status().isNotFound());
    }

    private ResultActions acknowledge(Long orderId) throws Exception {
        return mockMvc.perform(post("/api/kitchen/orders/" + orderId + "/acknowledge-cancellation")
                .with(asKitchen()));
    }

    private Order cancelledOrder() {
        return orderStateMachineService.transition(submittedOrder(), OrderStatus.CANCELLED, null);
    }

    @Test
    void anonymousAndWaiterCannotSeeCancellations() throws Exception {
        mockMvc.perform(get("/api/kitchen/orders/cancelled"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/kitchen/orders/cancelled").with(user("waiter").roles("WAITER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void waiterCannotAcknowledge() throws Exception {
        Order order = cancelledOrder();

        mockMvc.perform(post("/api/kitchen/orders/" + order.getId() + "/acknowledge-cancellation")
                .with(user("waiter").roles("WAITER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelledOrderShowsUntilAcknowledgedAndRecordsWho() throws Exception {
        Order order = cancelledOrder();
        String thisOrder = "$[?(@.orderId == " + order.getId() + ")]";

        mockMvc.perform(get("/api/kitchen/orders/cancelled").with(asKitchen()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(thisOrder + ".orderNumber").value(contains(order.getDisplayNumber())))
                .andExpect(jsonPath(thisOrder + ".tableNumber")
                        .value(contains(order.getTable().getTableNumber())));

        acknowledge(order.getId()).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/kitchen/orders/cancelled").with(asKitchen()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(thisOrder).isEmpty());

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertNotNull(reloaded.getCancellationAcknowledgedAt());
        assertEquals(kitchenStaff.getId(), reloaded.getCancellationAcknowledgedBy().getId());
        assertEquals(OrderStatus.CANCELLED, reloaded.getStatus());
    }

    @Test
    void acknowledgingTwiceKeepsTheFirstReceipt() throws Exception {
        Order order = cancelledOrder();

        acknowledge(order.getId()).andExpect(status().isNoContent());
        Instant firstReceipt = orderRepository.findById(order.getId()).orElseThrow()
                .getCancellationAcknowledgedAt();

        acknowledge(order.getId()).andExpect(status().isNoContent());

        assertEquals(firstReceipt,
                orderRepository.findById(order.getId()).orElseThrow().getCancellationAcknowledgedAt());
    }

    @Test
    void onlyCancelledOrdersCanBeAcknowledged() throws Exception {
        Order order = submittedOrder();

        acknowledge(order.getId()).andExpect(status().isConflict());
    }

    @Test
    void acknowledgingAnUnknownOrderIsNotFound() throws Exception {
        acknowledge(9999999L).andExpect(status().isNotFound());
    }

}
