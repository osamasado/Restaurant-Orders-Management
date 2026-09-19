package org.restaurantordersmanagement.backend.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Transactional (unlike @DataJpaTest, plain @SpringBootTest doesn't add this
 * automatically) - needed so a fresh orderRepository.findById(...) fetch's
 * lazy collections (history/items) stay accessible for the rest of the test
 * method instead of throwing LazyInitializationException once the fetch's
 * own transaction has closed.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class OrderStateMachineServiceTest {

    @Autowired
    private OrderStateMachineService orderStateMachineService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    private Order newDraftOrder(String tableNumber) {
        Table table = new Table();
        table.setTableNumber(tableNumber);
        table.setRoom("Front room");
        table.setSeats(2);
        table = tableRepository.saveAndFlush(table);

        Order order = new Order();
        order.setTable(table);
        return orderRepository.saveAndFlush(order);
    }

    @Test
    void walksTheFullLifecycleRecordingEachTransition() {
        Order order = newDraftOrder("1");
        assertNull(order.getPlacedAt());

        StaffAccount kitchen = new StaffAccount();
        kitchen.setName("Lifecycle Kitchen");
        kitchen.setRole(Role.KITCHEN);
        kitchen = staffAccountRepository.saveAndFlush(kitchen);

        Order submitted = orderStateMachineService.transition(order, OrderStatus.SUBMITTED, null);
        assertEquals(OrderStatus.SUBMITTED, submitted.getStatus());
        assertNotNull(submitted.getPlacedAt());
        assertNotNull(submitted.getOrderNumber());
        assertEquals(1, submitted.getHistory().size());
        assertNull(submitted.getHistory().get(0).getChangedBy());

        Order preparing = orderStateMachineService.transition(submitted, OrderStatus.PREPARING, kitchen);
        assertEquals(OrderStatus.PREPARING, preparing.getStatus());
        assertEquals("Lifecycle Kitchen", preparing.getHistory().get(1).getChangedBy().getName());

        Order ready = orderStateMachineService.transition(preparing, OrderStatus.READY, kitchen);
        assertEquals(OrderStatus.READY, ready.getStatus());

        Order served = orderStateMachineService.transition(ready, OrderStatus.SERVED, kitchen);
        assertEquals(OrderStatus.SERVED, served.getStatus());
        assertEquals(4, served.getHistory().size());
    }

    @Test
    void illegalTransitionIsRejectedAndNothingChanges() {
        Order order = newDraftOrder("2");

        assertThrows(
                IllegalOrderTransitionException.class,
                () -> orderStateMachineService.transition(order, OrderStatus.PREPARING, null));

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.DRAFT, reloaded.getStatus());
        assertEquals(0, reloaded.getHistory().size());
        assertNull(reloaded.getPlacedAt());
    }

    @Test
    void cancellingAServedOrderIsRejected() {
        Order order = newDraftOrder("3");
        order = orderStateMachineService.transition(order, OrderStatus.SUBMITTED, null);
        order = orderStateMachineService.transition(order, OrderStatus.PREPARING, null);
        order = orderStateMachineService.transition(order, OrderStatus.READY, null);
        Order served = orderStateMachineService.transition(order, OrderStatus.SERVED, null);

        assertThrows(
                IllegalOrderTransitionException.class,
                () -> orderStateMachineService.transition(served, OrderStatus.CANCELLED, null));
    }

}
