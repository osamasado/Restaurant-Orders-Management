package org.restaurantordersmanagement.backend.order.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderItem;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.model.OrderStatusHistory;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Test
    void savesOrderWithCascadedItemsAndChronologicalHistory() {
        Table table = new Table();
        table.setTableNumber("42");
        table.setRoom("Front room");
        table.setSeats(4);
        table = tableRepository.saveAndFlush(table);

        StaffAccount kitchen = new StaffAccount();
        kitchen.setName("Test Kitchen");
        kitchen.setRole(Role.KITCHEN);
        kitchen = staffAccountRepository.saveAndFlush(kitchen);

        Order order = new Order();
        order.setTable(table);
        order.setStatus(OrderStatus.PREPARING);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setName("Wiener Schnitzel");
        item.setSize("300 g");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("22.50"));
        order.getItems().add(item);

        Instant submittedAt = Instant.parse("2026-09-18T10:00:00Z");
        Instant preparingAt = Instant.parse("2026-09-18T10:05:00Z");

        OrderStatusHistory submitted = new OrderStatusHistory();
        submitted.setOrder(order);
        submitted.setStatus(OrderStatus.SUBMITTED);
        submitted.setChangedAt(submittedAt);
        order.getHistory().add(submitted);

        OrderStatusHistory preparing = new OrderStatusHistory();
        preparing.setOrder(order);
        preparing.setStatus(OrderStatus.PREPARING);
        preparing.setChangedAt(preparingAt);
        preparing.setChangedBy(kitchen);
        order.getHistory().add(preparing);

        Order saved = orderRepository.saveAndFlush(order);

        Optional<Order> found = orderRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(1, found.get().getItems().size());
        assertEquals("Wiener Schnitzel", found.get().getItems().get(0).getName());

        assertEquals(2, found.get().getHistory().size());
        assertEquals(OrderStatus.SUBMITTED, found.get().getHistory().get(0).getStatus());
        assertEquals(submittedAt, found.get().getHistory().get(0).getChangedAt());
        assertEquals(OrderStatus.PREPARING, found.get().getHistory().get(1).getStatus());
        assertEquals("Test Kitchen", found.get().getHistory().get(1).getChangedBy().getName());
    }

}
