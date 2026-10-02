package org.restaurantordersmanagement.backend.hall.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderItem;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.order.service.OrderStateMachineService;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Not @Transactional - see CategoryControllerTest's javadoc for why.
 * tearDown() deletes tracked ids instead: orders first (they reference
 * tables), then tables. Every request is anonymous on purpose - the board
 * has no login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HallBoardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private OrderStateMachineService orderStateMachineService;

    private final List<Long> createdOrderIds = new ArrayList<>();
    private final List<Long> createdTableIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        createdOrderIds.forEach(orderRepository::deleteById);
        createdTableIds.forEach(tableRepository::deleteById);
    }

    /** A SUBMITTED order with one item, the way a guest's submit leaves it. */
    private Order submittedOrder() {
        Table table = new Table();
        table.setTableNumber("H-" + UUID.randomUUID());
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

    private Order advance(Order order, OrderStatus target) {
        return orderStateMachineService.transition(order, target, null);
    }

    @Test
    void boardIsReachableWithoutLoginAndHasExactlyTwoLists() throws Exception {
        mockMvc.perform(get("/api/hall/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.preparing").isArray())
                .andExpect(jsonPath("$.ready").isArray());
    }

    @Test
    void numberMovesFromPreparingToReadyAndLeavesOnceServed() throws Exception {
        Order order = submittedOrder();
        Integer number = order.getOrderNumber();

        Order preparing = advance(order, OrderStatus.PREPARING);
        mockMvc.perform(get("/api/hall/orders"))
                .andExpect(jsonPath("$.preparing").value(hasItem(number)))
                .andExpect(jsonPath("$.ready").value(not(hasItem(number))));

        Order ready = advance(preparing, OrderStatus.READY);
        mockMvc.perform(get("/api/hall/orders"))
                .andExpect(jsonPath("$.preparing").value(not(hasItem(number))))
                .andExpect(jsonPath("$.ready").value(hasItem(number)));

        advance(ready, OrderStatus.SERVED);
        mockMvc.perform(get("/api/hall/orders"))
                .andExpect(jsonPath("$.preparing").value(not(hasItem(number))))
                .andExpect(jsonPath("$.ready").value(not(hasItem(number))));
    }

    @Test
    void submittedAndCancelledOrdersAreNotOnTheBoard() throws Exception {
        Order notStarted = submittedOrder();
        Order cancelled = advance(submittedOrder(), OrderStatus.CANCELLED);

        mockMvc.perform(get("/api/hall/orders"))
                .andExpect(jsonPath("$.preparing").value(not(hasItem(notStarted.getOrderNumber()))))
                .andExpect(jsonPath("$.ready").value(not(hasItem(notStarted.getOrderNumber()))))
                .andExpect(jsonPath("$.preparing").value(not(hasItem(cancelled.getOrderNumber()))))
                .andExpect(jsonPath("$.ready").value(not(hasItem(cancelled.getOrderNumber()))));
    }

    @Test
    void boardExposesNothingButOrderNumbers() throws Exception {
        advance(submittedOrder(), OrderStatus.PREPARING);

        mockMvc.perform(get("/api/hall/orders"))
                .andExpect(jsonPath("$.preparing[0]").isNumber())
                .andExpect(jsonPath("$..tableNumber").doesNotExist())
                .andExpect(jsonPath("$..items").doesNotExist())
                .andExpect(jsonPath("$..total").doesNotExist());
    }

}
