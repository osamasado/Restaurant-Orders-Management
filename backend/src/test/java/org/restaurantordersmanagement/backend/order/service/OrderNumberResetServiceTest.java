package org.restaurantordersmanagement.backend.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderNumberCounter;
import org.restaurantordersmanagement.backend.order.model.OrderNumberReset;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderNumberCounterRepository;
import org.restaurantordersmanagement.backend.order.repository.OrderNumberResetRepository;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.order.web.OrderHistoryPageResponse;
import org.restaurantordersmanagement.backend.order.web.OrderNumberStatusResponse;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Restarting the displayed order number. @Transactional, so the counter, the orders and the audit rows are rolled back
 * and the shared test database is left as it was. The race with simultaneous submissions is in
 * OrderNumberResetConcurrencyTest, which needs real, separate transactions.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class OrderNumberResetServiceTest {

    @Autowired
    private OrderNumberResetService resetService;

    @Autowired
    private OrderStateMachineService stateMachine;

    @Autowired
    private OrderHistoryService historyService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderNumberCounterRepository counterRepository;

    @Autowired
    private OrderNumberResetRepository resetRepository;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    private Table table;
    private StaffAccount admin;

    private void givenATableAndAnAdmin() {
        table = new Table();
        table.setTableNumber("reset-" + UUID.randomUUID().toString().substring(0, 8));
        table.setRoom("Front room");
        table.setSeats(2);
        table = tableRepository.saveAndFlush(table);

        admin = new StaffAccount();
        admin.setName("Reset Admin " + UUID.randomUUID());
        admin.setRole(Role.ADMIN);
        admin = staffAccountRepository.saveAndFlush(admin);
    }

    private Order submit() {
        Order order = new Order();
        order.setTable(table);
        order = orderRepository.saveAndFlush(order);
        return stateMachine.transition(order, OrderStatus.SUBMITTED, null);
    }

    private Order walkTo(Order order, OrderStatus... steps) {
        for (OrderStatus step : steps) {
            order = stateMachine.transition(order, step, admin);
        }
        return order;
    }

    private Order served(Order order) {
        return walkTo(order, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.SERVED);
    }

    /** The database may hold open orders from elsewhere; these tests need none, so they are served first. */
    private void closeEveryOpenOrder() {
        for (Order open : orderRepository.findAll()) {
            switch (open.getStatus()) {
                case SUBMITTED -> served(open);
                case PREPARING -> walkTo(open, OrderStatus.READY, OrderStatus.SERVED);
                case READY -> walkTo(open, OrderStatus.SERVED);
                default -> { }
            }
        }
    }

    private void startAtDisplayNumber(int next) {
        OrderNumberCounter counter = counterRepository.findById(OrderNumberCounter.ROW_ID).orElseThrow();
        counter.setNextDisplayValue(next);
        counterRepository.saveAndFlush(counter);
    }

    @Test
    void newOrdersAreNumberedConsecutivelyAndTheDisplayedNumberStartsLikeTheInternalOne() {
        givenATableAndAnAdmin();
        closeEveryOpenOrder();
        OrderNumberCounter counter = counterRepository.findById(OrderNumberCounter.ROW_ID).orElseThrow();
        int display = counter.getNextDisplayValue();
        int internal = counter.getNextValue();

        Order first = submit();
        Order second = submit();

        assertEquals(display, first.getDisplayNumber());
        assertEquals(display + 1, second.getDisplayNumber());
        assertEquals(internal, first.getOrderNumber());
        assertEquals(internal + 1, second.getOrderNumber());
    }

    @Test
    void theResetRestartsTheDisplayedNumberAtOneButNeverTheInternalNumber() {
        givenATableAndAnAdmin();
        closeEveryOpenOrder();
        Order before = served(submit());
        assertTrue(before.getDisplayNumber() >= 1);

        OrderNumberStatusResponse afterReset = resetService.reset(admin.getId());
        Order next = submit();

        assertEquals(1, next.getDisplayNumber(), "the next order after a reset is 001");
        assertEquals(2, submit().getDisplayNumber(), "and the series goes on");
        assertEquals(before.getOrderNumber() + 1, next.getOrderNumber(), "the internal number carries on, it is never reset");
        assertNotEquals(before.getOrderNumber(), next.getOrderNumber());
        assertEquals(1, afterReset.nextDisplayNumber());
    }

    @Test
    void anOrderKeepsItsNumberWhenTheSeriesIsResetAfterIt() {
        givenATableAndAnAdmin();
        closeEveryOpenOrder();
        Order old = served(submit());
        int shown = old.getDisplayNumber();

        resetService.reset(admin.getId());

        assertEquals(shown, orderRepository.findById(old.getId()).orElseThrow().getDisplayNumber());
    }

    @Test
    void theResetIsRefusedWhileAnOrderIsSubmittedInPreparationOrReady() {
        givenATableAndAnAdmin();
        closeEveryOpenOrder();
        Order order = submit();
        int next = counterRepository.findById(OrderNumberCounter.ROW_ID).orElseThrow().getNextDisplayValue();

        ResponseStatusException submitted = assertThrows(ResponseStatusException.class, () -> resetService.reset(admin.getId()));
        assertEquals(409, submitted.getStatusCode().value());
        assertTrue(submitted.getReason().contains("1 order"), "the message says how many: " + submitted.getReason());

        order = walkTo(order, OrderStatus.PREPARING);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> resetService.reset(admin.getId())).getStatusCode().value());

        walkTo(order, OrderStatus.READY);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> resetService.reset(admin.getId())).getStatusCode().value());

        assertEquals(next, counterRepository.findById(OrderNumberCounter.ROW_ID).orElseThrow().getNextDisplayValue(),
                "a refused reset changes nothing");
        assertTrue(resetRepository.findAll().stream().noneMatch(reset -> reset.getResetBy().getId().equals(admin.getId())),
                "and records nothing");
    }

    @Test
    void servedAndCancelledOrdersDoNotBlockTheReset() {
        givenATableAndAnAdmin();
        closeEveryOpenOrder();
        served(submit());
        stateMachine.transition(submit(), OrderStatus.CANCELLED, admin);

        OrderNumberStatusResponse status = resetService.reset(admin.getId());

        assertEquals(1, status.nextDisplayNumber());
        assertEquals(0, status.openOrders());
    }

    @Test
    void aDraftDoesNotBlockTheResetAndTakesNoNumber() {
        givenATableAndAnAdmin();
        closeEveryOpenOrder();
        served(submit());
        Order draft = new Order();
        draft.setTable(table);
        draft = orderRepository.saveAndFlush(draft);

        resetService.reset(admin.getId());

        assertNull(orderRepository.findById(draft.getId()).orElseThrow().getDisplayNumber());
    }

    @Test
    void everyResetIsRecordedWithTheAdminTheTimeAndWhereTheSeriesStood() {
        givenATableAndAnAdmin();
        closeEveryOpenOrder();
        startAtDisplayNumber(1);
        served(submit());
        served(submit());
        served(submit());
        Instant before = Instant.now();

        OrderNumberStatusResponse status = resetService.reset(admin.getId());

        OrderNumberReset recorded = resetRepository.findFirstByOrderByResetAtDescIdDesc().orElseThrow();
        assertEquals(admin.getId(), recorded.getResetBy().getId());
        assertEquals(4, recorded.getPreviousNextDisplay(), "three orders were numbered, the next would have been 4");
        assertTrue(!recorded.getResetAt().isBefore(before.minusSeconds(1)));
        assertEquals(admin.getName(), status.lastReset().staffName());
        assertEquals(recorded.getResetAt(), status.lastReset().resetAt());
    }

    @Test
    void aResetWhenNothingWasNumberedSinceTheLastOneChangesAndRecordsNothing() {
        givenATableAndAnAdmin();
        closeEveryOpenOrder();
        startAtDisplayNumber(1);
        long recorded = resetRepository.count();

        OrderNumberStatusResponse status = resetService.reset(admin.getId());

        assertEquals(1, status.nextDisplayNumber());
        assertEquals(recorded, resetRepository.count());
    }

    @Test
    void theStatusSaysWhatTheNextNumberIsAndHowManyOrdersAreOpen() {
        givenATableAndAnAdmin();
        closeEveryOpenOrder();
        startAtDisplayNumber(5);
        submit();
        walkTo(submit(), OrderStatus.PREPARING);
        served(submit());

        OrderNumberStatusResponse status = resetService.status();

        assertEquals(8, status.nextDisplayNumber());
        assertEquals(2, status.openOrders());
    }

    @Test
    void theHistorySearchByNumberFindsSeveralOrdersAfterAResetNewestFirst() {
        givenATableAndAnAdmin();
        closeEveryOpenOrder();
        startAtDisplayNumber(1);
        Order oldSeries = served(submit());
        assertEquals(1, oldSeries.getDisplayNumber());
        resetService.reset(admin.getId());
        Order newSeries = submit();
        assertEquals(1, newSeries.getDisplayNumber());

        OrderHistoryPageResponse page = historyService.page(0, 20, 1);

        List<Long> ids = page.orders().stream().map(order -> order.orderId()).toList();
        assertTrue(ids.indexOf(newSeries.getId()) >= 0 && ids.indexOf(oldSeries.getId()) >= 0, "both are found: " + ids);
        assertTrue(ids.indexOf(newSeries.getId()) < ids.indexOf(oldSeries.getId()), "the newer one first");
        assertNotNull(page.orders().get(0).orderNumber());
    }

}
