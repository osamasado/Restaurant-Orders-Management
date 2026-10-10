package org.restaurantordersmanagement.backend.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderNumberCounter;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderNumberCounterRepository;
import org.restaurantordersmanagement.backend.order.repository.OrderNumberResetRepository;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * A reset that races simultaneous submissions. Like OrderNumberConcurrencyTest it is NOT @Transactional: the point is
 * real, separate transactions competing for the counter row's lock, so everything is committed and tearDown() puts
 * the database back (the orders, the audit rows, the account and the counter).
 *
 * Whatever the interleaving, two things must hold: no two of the submitted orders (all of them open) show the same
 * number, and the internal number stays unique. Either the reset wins (the series restarts at 1 before the first
 * submission commits) or it is refused because an order is open already; it can never land in the middle of a series
 * and hand out a number twice.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderNumberResetConcurrencyTest {

    private static final int SUBMISSIONS = 20;
    private static final int ROUNDS = 5;

    @Autowired
    private OrderStateMachineService stateMachine;

    @Autowired
    private OrderNumberResetService resetService;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long tableId;
    private Long adminId;
    private Integer originalNextDisplay;
    private final List<Long> orderIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long id : orderIds) {
            orderRepository.findById(id).ifPresent(orderRepository::delete);
        }
        if (adminId != null) {
            resetRepository.deleteAll(resetRepository.findAll().stream()
                    .filter(reset -> reset.getResetBy().getId().equals(adminId)).toList());
            staffAccountRepository.findById(adminId).ifPresent(staffAccountRepository::delete);
        }
        if (tableId != null) {
            tableRepository.findById(tableId).ifPresent(tableRepository::delete);
        }
        if (originalNextDisplay != null) {
            OrderNumberCounter counter = counterRepository.findById(OrderNumberCounter.ROW_ID).orElseThrow();
            counter.setNextDisplayValue(originalNextDisplay);
            counterRepository.saveAndFlush(counter);
        }
    }

    @Test
    void aResetRacingSubmissionsNeverHandsOutANumberTwiceAndNeverLandsInTheMiddleOfASeries() throws Exception {
        Table table = new Table();
        table.setTableNumber("reset-race-" + UUID.randomUUID().toString().substring(0, 6));
        table.setRoom("Front room");
        table.setSeats(4);
        tableId = tableRepository.saveAndFlush(table).getId();
        StaffAccount admin = new StaffAccount();
        admin.setName("Race Admin " + UUID.randomUUID());
        admin.setRole(Role.ADMIN);
        adminId = staffAccountRepository.saveAndFlush(admin).getId();
        originalNextDisplay = counterRepository.findById(OrderNumberCounter.ROW_ID).orElseThrow().getNextDisplayValue();

        int resetsThatWon = 0;
        int resetsRefused = 0;
        for (int round = 0; round < ROUNDS; round++) {
            // Every round starts a series part way through, with nothing open, so a reset has something to restart.
            setNextDisplay(7);
            List<Order> drafts = new ArrayList<>();
            for (int i = 0; i < SUBMISSIONS; i++) {
                Order order = new Order();
                order.setTable(tableRepository.findById(tableId).orElseThrow());
                Order saved = orderRepository.saveAndFlush(order);
                orderIds.add(saved.getId());
                drafts.add(saved);
            }

            CountDownLatch ready = new CountDownLatch(SUBMISSIONS + 1);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(SUBMISSIONS + 1);
            List<Future<Order>> submissions = new ArrayList<>();
            for (Order draft : drafts) {
                submissions.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return stateMachine.transition(draft, OrderStatus.SUBMITTED, null);
                }));
            }
            Future<Boolean> reset = executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    resetService.reset(adminId);
                    return true;
                } catch (ResponseStatusException refused) {
                    return false;
                }
            });
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();

            List<Integer> displayed = new ArrayList<>();
            List<Integer> internal = new ArrayList<>();
            try {
                for (Future<Order> submission : submissions) {
                    Order submitted = submission.get(20, TimeUnit.SECONDS);
                    displayed.add(submitted.getDisplayNumber());
                    internal.add(submitted.getOrderNumber());
                }
                if (reset.get(20, TimeUnit.SECONDS)) {
                    resetsThatWon++;
                } else {
                    resetsRefused++;
                }
            } catch (ExecutionException e) {
                fail("A submission or the reset failed under concurrency: " + e.getCause());
            } catch (TimeoutException e) {
                fail("A submission or the reset timed out (a lock was never released?)");
            } finally {
                executor.shutdown();
            }

            assertEquals(SUBMISSIONS, displayed.stream().distinct().count(), "no displayed number twice: " + displayed);
            assertEquals(SUBMISSIONS, internal.stream().distinct().count(), "no internal number twice: " + internal);
            int lowest = displayed.stream().min(Integer::compare).orElseThrow();
            assertTrue(lowest == 1 || lowest == 7,
                    "the series is whole: it starts at 1 (the reset won) or carries on at 7 (it was refused), not " + lowest);
            assertEquals(lowest + SUBMISSIONS - 1, displayed.stream().max(Integer::compare).orElseThrow(),
                    "and it is consecutive: " + displayed);

            // Close the series so the next round's reset has no open order to be refused by.
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                for (Order draft : drafts) {
                    Order current = orderRepository.findById(draft.getId()).orElseThrow();
                    for (OrderStatus step : List.of(OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.SERVED)) {
                        current = stateMachine.transition(current, step, null);
                    }
                }
            });
        }
        // Across the rounds both outcomes are legal; this only documents which happened (at least one must have).
        assertEquals(ROUNDS, resetsThatWon + resetsRefused);
    }

    private void setNextDisplay(int value) {
        OrderNumberCounter counter = counterRepository.findById(OrderNumberCounter.ROW_ID).orElseThrow();
        counter.setNextDisplayValue(value);
        counterRepository.saveAndFlush(counter);
    }

}
