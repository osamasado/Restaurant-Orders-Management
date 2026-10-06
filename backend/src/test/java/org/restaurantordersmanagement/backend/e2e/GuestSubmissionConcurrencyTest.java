package org.restaurantordersmanagement.backend.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.CategoryTranslation;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.model.MealSizeTranslation;
import org.restaurantordersmanagement.backend.menu.model.MealTranslation;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.repository.MealRepository;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Many tables ordering at the same moment, through the real guest endpoint on
 * a real HTTP server - pairing check, validation, pricing, the order-number
 * lock and the save all in one request, not the state machine called on its own
 * (OrderNumberConcurrencyTest does that). Every accepted order must get its own
 * number, the numbers must be consecutive (nothing skipped), and refused
 * submissions mixed in must not use any up.
 *
 * Not @Transactional: each request has to commit in its own transaction for the
 * counter row's lock to be exercised. tearDown() deletes what it created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class GuestSubmissionConcurrencyTest {

    private static final int VALID_SUBMISSIONS = 30;
    private static final int REFUSED_SUBMISSIONS = 12;

    @LocalServerPort
    private int port;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Long> orderIds = new ArrayList<>();
    private Long tableId;
    private Long mealId;
    private Long categoryId;
    private String deviceCode;
    private Long sizeId;

    @BeforeEach
    void setUp() {
        Table table = new Table();
        table.setTableNumber("CC-" + UUID.randomUUID().toString().substring(0, 8));
        table.setRoom("Main room");
        table.setSeats(4);
        deviceCode = ("C" + UUID.randomUUID().toString().replace("-", "").substring(0, 5)).toUpperCase();
        table.setPairedDeviceId(deviceCode);
        tableId = tableRepository.saveAndFlush(table).getId();

        Category category = new Category();
        category.setSortOrder(1);
        CategoryTranslation categoryName = new CategoryTranslation();
        categoryName.setCategory(category);
        categoryName.setLanguage(Language.EN);
        categoryName.setName("Concurrency");
        category.getTranslations().add(categoryName);
        category = categoryRepository.saveAndFlush(category);
        categoryId = category.getId();

        Meal meal = new Meal();
        meal.setCategory(category);
        meal.setAvailable(true);
        MealTranslation name = new MealTranslation();
        name.setMeal(meal);
        name.setLanguage(Language.EN);
        name.setName("Concurrency Soup");
        meal.getTranslations().add(name);
        MealSize size = new MealSize();
        size.setMeal(meal);
        size.setPrice(new BigDecimal("10.00"));
        MealSizeTranslation label = new MealSizeTranslation();
        label.setMealSize(size);
        label.setLanguage(Language.EN);
        label.setLabel("Regular");
        size.getTranslations().add(label);
        meal.getSizes().add(size);
        meal = mealRepository.saveAndFlush(meal);
        mealId = meal.getId();
        sizeId = meal.getSizes().get(0).getId();
    }

    @AfterEach
    void tearDown() {
        orderIds.forEach(orderRepository::deleteById);
        mealRepository.deleteById(mealId);
        categoryRepository.deleteById(categoryId);
        tableRepository.deleteById(tableId);
    }

    @Test
    void simultaneousGuestSubmissionsGetDistinctConsecutiveNumbersAndRefusedOnesUseNoneUp() throws Exception {
        String valid = "{\"deviceCode\":\"" + deviceCode + "\",\"language\":\"EN\",\"paymentMethod\":\"CASH\","
                + "\"items\":[{\"sizeId\":" + sizeId + ",\"quantity\":1}]}";
        String emptyCart = "{\"deviceCode\":\"" + deviceCode + "\",\"language\":\"EN\",\"paymentMethod\":\"CASH\",\"items\":[]}";
        String unknownMeal = "{\"deviceCode\":\"" + deviceCode + "\",\"language\":\"EN\",\"paymentMethod\":\"CASH\","
                + "\"items\":[{\"sizeId\":987654321,\"quantity\":1}]}";

        int total = VALID_SUBMISSIONS + REFUSED_SUBMISSIONS;
        CountDownLatch ready = new CountDownLatch(total);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(total);
        List<Future<HttpResponse<String>>> accepted = new ArrayList<>();
        List<Future<HttpResponse<String>>> refused = new ArrayList<>();

        // Valid and refused submissions interleaved, all released together.
        for (int i = 0; i < total; i++) {
            String body = i % 4 == 3 && refused.size() < REFUSED_SUBMISSIONS ? (refused.size() % 2 == 0 ? emptyCart : unknownMeal) : valid;
            boolean isRefused = body != valid;
            if (!isRefused && accepted.size() == VALID_SUBMISSIONS) {
                body = emptyCart;
                isRefused = true;
            }
            String requestBody = body;
            Callable<HttpResponse<String>> submit = () -> {
                ready.countDown();
                go.await();
                return client.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/guest/orders"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
            };
            (isRefused ? refused : accepted).add(executor.submit(submit));
        }
        assertTrue(ready.await(20, TimeUnit.SECONDS), "all submitters should be waiting at the start line");
        go.countDown();

        TreeSet<Integer> numbers = new TreeSet<>();
        for (Future<HttpResponse<String>> future : accepted) {
            HttpResponse<String> response = future.get(60, TimeUnit.SECONDS);
            assertEquals(201, response.statusCode(), "accepted submission: " + response.body());
            numbers.add(JsonPath.read(response.body(), "$.orderNumber"));
            orderIds.add(((Number) JsonPath.read(response.body(), "$.orderId")).longValue());
            assertEquals(0, new BigDecimal("11.90").compareTo(new BigDecimal(JsonPath.read(response.body(), "$.total").toString())),
                    "each order is priced on the server: 10.00 + 19% tax");
        }
        for (Future<HttpResponse<String>> future : refused) {
            assertEquals(400, future.get(60, TimeUnit.SECONDS).statusCode());
        }
        executor.shutdown();

        assertEquals(accepted.size(), numbers.size(), "every accepted order must get its own number, got " + numbers);
        assertEquals(numbers.size() - 1, numbers.last() - numbers.first(),
                "numbers must be consecutive - a gap means a refused submission used one up: " + numbers);

        // Every accepted order was saved, with its line.
        transactionTemplate.executeWithoutResult(tx -> {
            List<Order> saved = orderRepository.findAllById(orderIds);
            assertEquals(VALID_SUBMISSIONS, saved.size());
            saved.forEach(order -> assertEquals(1, order.getItems().size()));
        });
    }

}
