package org.restaurantordersmanagement.backend.order.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
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
import org.springframework.transaction.annotation.Transactional;

/** The reset of the displayed order number over HTTP: who may call it, and what it answers. Rolled back after each test. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class OrderNumberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderStateMachineService stateMachine;

    private StaffAccount admin;

    @BeforeEach
    void setUp() {
        admin = account(Role.ADMIN);
        // Other tests may have left open orders; they would refuse the reset, so they are served here.
        for (Order open : orderRepository.findAll()) {
            OrderStatus status = open.getStatus();
            if (status == OrderStatus.SUBMITTED) {
                open = stateMachine.transition(open, OrderStatus.PREPARING, admin);
                status = OrderStatus.PREPARING;
            }
            if (status == OrderStatus.PREPARING) {
                open = stateMachine.transition(open, OrderStatus.READY, admin);
                status = OrderStatus.READY;
            }
            if (status == OrderStatus.READY) {
                stateMachine.transition(open, OrderStatus.SERVED, admin);
            }
        }
    }

    private StaffAccount account(Role role) {
        StaffAccount account = new StaffAccount();
        account.setName(role + "-" + UUID.randomUUID());
        account.setRole(role);
        return staffAccountRepository.saveAndFlush(account);
    }

    private Order submittedOrder() {
        Table table = new Table();
        table.setTableNumber("on-" + UUID.randomUUID().toString().substring(0, 8));
        table.setRoom("Front room");
        table.setSeats(2);
        table = tableRepository.saveAndFlush(table);
        Order order = new Order();
        order.setTable(table);
        return stateMachine.transition(orderRepository.saveAndFlush(order), OrderStatus.SUBMITTED, null);
    }

    @Test
    void anAdminSeesTheNextNumberAndHowManyOrdersAreOpen() throws Exception {
        submittedOrder();

        mockMvc.perform(get("/api/settings/order-number").with(user(new StaffPrincipal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextDisplayNumber").isNumber())
                .andExpect(jsonPath("$.openOrders").value(1));
    }

    @Test
    void anAdminCanResetWhenNothingIsOpenAndTheAnswerIsTheNewState() throws Exception {
        Order order = submittedOrder();
        stateMachine.transition(order, OrderStatus.CANCELLED, admin);

        mockMvc.perform(post("/api/settings/order-number/reset").with(user(new StaffPrincipal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextDisplayNumber").value(1))
                .andExpect(jsonPath("$.openOrders").value(0))
                .andExpect(jsonPath("$.lastReset.staffName").value(admin.getName()));
    }

    @Test
    void theResetIsAConflictWhileAnOrderIsOpen() throws Exception {
        submittedOrder();

        mockMvc.perform(post("/api/settings/order-number/reset").with(user(new StaffPrincipal(admin))))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyAnAdminMayAskOrReset() throws Exception {
        for (Role role : new Role[] {Role.KITCHEN, Role.WAITER, Role.CASHIER}) {
            StaffAccount other = account(role);
            mockMvc.perform(get("/api/settings/order-number").with(user(new StaffPrincipal(other))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/settings/order-number/reset").with(user(new StaffPrincipal(other))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/settings/order-number")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/settings/order-number/reset")).andExpect(status().isUnauthorized());
    }

}
