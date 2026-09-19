package org.restaurantordersmanagement.backend.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Deliberately NOT @Transactional (unlike OrderStateMachineServiceTest):
 * a test-level transaction would collapse every thread's work into one
 * transaction and defeat the whole point of exercising the counter row's
 * lock across real, separate, concurrent transactions.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderNumberConcurrencyTest {

    private static final int CONCURRENT_SUBMISSIONS = 50;

    @Autowired
    private OrderStateMachineService orderStateMachineService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TableRepository tableRepository;

    @Test
    void simultaneousSubmissionsNeverProduceDuplicateOrderNumbers() throws InterruptedException {
        Table table = new Table();
        table.setTableNumber("concurrency-test");
        table.setRoom("Front room");
        table.setSeats(4);
        table = tableRepository.saveAndFlush(table);

        List<Order> draftOrders = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_SUBMISSIONS; i++) {
            Order order = new Order();
            order.setTable(table);
            draftOrders.add(orderRepository.saveAndFlush(order));
        }

        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_SUBMISSIONS);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_SUBMISSIONS);
        List<Future<Integer>> futures = new ArrayList<>();

        for (Order draftOrder : draftOrders) {
            Callable<Integer> submitOrder = () -> {
                readyLatch.countDown();
                startLatch.await();
                Order submitted = orderStateMachineService.transition(draftOrder, OrderStatus.SUBMITTED, null);
                return submitted.getOrderNumber();
            };
            futures.add(executor.submit(submitOrder));
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();

        Set<Integer> orderNumbers = ConcurrentHashMap.newKeySet();
        for (Future<Integer> future : futures) {
            try {
                orderNumbers.add(future.get(10, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                fail("Order submission failed under concurrency: " + e.getCause());
            } catch (java.util.concurrent.TimeoutException e) {
                fail("Order submission timed out under concurrency");
            }
        }
        executor.shutdown();

        assertEquals(CONCURRENT_SUBMISSIONS, orderNumbers.size(),
                "Expected " + CONCURRENT_SUBMISSIONS + " distinct order numbers, got duplicates: " + orderNumbers);
    }

}
