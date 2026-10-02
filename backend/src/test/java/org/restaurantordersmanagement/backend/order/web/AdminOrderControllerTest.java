package org.restaurantordersmanagement.backend.order.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
class AdminOrderControllerTest {

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

    private StaffAccount adminStaff;

    @BeforeEach
    void setUp() {
        StaffAccount staff = new StaffAccount();
        staff.setName("Admin-" + UUID.randomUUID());
        staff.setRole(Role.ADMIN);
        adminStaff = staffAccountRepository.saveAndFlush(staff);
        createdStaffIds.add(adminStaff.getId());
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
        table.setTableNumber("A-" + UUID.randomUUID());
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

        Order submittedOrder = orderStateMachineService.transition(order, OrderStatus.SUBMITTED, null);
        createdOrderIds.add(submittedOrder.getId());
        return submittedOrder;
    }

    /** Logged in as the real admin account - a StaffPrincipal, like after /api/staff/login. */
    private RequestPostProcessor asAdmin() {
        return user(new StaffPrincipal(adminStaff));
    }

    private ResultActions cancel(Long orderId, RequestPostProcessor login) throws Exception {
        return mockMvc.perform(post("/api/admin/orders/" + orderId + "/cancel").with(login));
    }

    @Test
    void anonymousCannotCancel() throws Exception {
        Order order = submittedOrder();

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void kitchenCannotCancel() throws Exception {
        Order order = submittedOrder();

        cancel(order.getId(), user("kitchen").roles("KITCHEN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCancelRecordsTheAdminAsTheActor() throws Exception {
        Order order = submittedOrder();

        cancel(order.getId(), asAdmin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(order.getId()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        transactionTemplate.executeWithoutResult(tx -> {
            Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
            OrderStatusHistory last = reloaded.getHistory().getLast();
            assertEquals(OrderStatus.CANCELLED, last.getStatus());
            assertEquals(adminStaff.getId(), last.getChangedBy().getId());
        });
    }

    @Test
    void cancellingAnAlreadyCancelledOrderIsAConflict() throws Exception {
        Order order = submittedOrder();

        cancel(order.getId(), asAdmin()).andExpect(status().isOk());
        cancel(order.getId(), asAdmin()).andExpect(status().isConflict());
    }

    @Test
    void cancellingAServedOrderIsAConflict() throws Exception {
        Order order = submittedOrder();
        Order preparing = orderStateMachineService.transition(order, OrderStatus.PREPARING, adminStaff);
        Order ready = orderStateMachineService.transition(preparing, OrderStatus.READY, adminStaff);
        orderStateMachineService.transition(ready, OrderStatus.SERVED, adminStaff);

        cancel(order.getId(), asAdmin()).andExpect(status().isConflict());
    }

    @Test
    void unknownOrderIsNotFound() throws Exception {
        cancel(9999999L, asAdmin()).andExpect(status().isNotFound());
    }

}
