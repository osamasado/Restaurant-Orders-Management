package org.restaurantordersmanagement.backend.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.guest.web.CartQuoteRequest;
import org.restaurantordersmanagement.backend.guest.web.GuestOrderRequest;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderItem;

/**
 * Four of CLAUDE.md's non-negotiable rules hold only because nobody has written
 * the line that breaks them. These tests make that a build failure instead:
 *
 * - one state machine, no direct status writes
 * - order numbers come from the locked counter, nowhere else
 * - order lines snapshot what was ordered and never point back at the menu
 * - the server prices everything: a client request cannot carry a price or total
 *
 * Plain unit tests - they read the source tree and the compiled classes, so
 * they run in a moment and need no Spring context.
 */
class NonNegotiableRulesGuardTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final String STATE_MACHINE = "OrderStateMachineService.java";

    private static List<String> filesContaining(String text) throws IOException {
        assertTrue(Files.isDirectory(MAIN_SOURCES), "run from the backend module: " + MAIN_SOURCES.toAbsolutePath());
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains(text);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    @Test
    void onlyTheStateMachineMovesAnOrderBetweenStatuses() throws IOException {
        // recordTransition is public, so the compiler cannot stop another caller skipping the legality check.
        assertEquals(List.of(STATE_MACHINE), filesContaining(".recordTransition("),
                "Order.recordTransition must only be called by OrderStateMachineService");
        assertEquals(List.of("Order.java"), filesContaining("new OrderStatusHistory("),
                "audit rows are written only by Order.recordTransition");
    }

    @Test
    void anOrderHasNoSetterForItsStatus() {
        List<String> setters = Arrays.stream(Order.class.getMethods())
                .map(Method::getName)
                .filter(name -> name.equalsIgnoreCase("setStatus"))
                .toList();
        assertTrue(setters.isEmpty(), "Order must not expose a status setter, found " + setters);
    }

    @Test
    void orderNumbersOnlyComeFromTheLockedCounterViaTheStateMachine() throws IOException {
        assertEquals(List.of(STATE_MACHINE), filesContaining(".assignNextOrderNumber("),
                "OrderNumberService.assignNextOrderNumber must only be called by OrderStateMachineService");
    }

    @Test
    void anOrderLineSnapshotsTheMealAndNeverPointsBackAtTheMenu() {
        for (Field field : OrderItem.class.getDeclaredFields()) {
            String type = field.getType().getName();
            assertFalse(type.contains(".menu.model."),
                    "OrderItem." + field.getName() + " references the menu (" + type + "): a later menu edit would rewrite old orders");
        }
    }

    @Test
    void aGuestRequestCannotCarryAPriceTotalTaxOrTable() {
        Pattern forbidden = Pattern.compile("(?i)price|total|amount|tax|table");
        for (Class<?> request : List.of(
                GuestOrderRequest.class,
                GuestOrderRequest.Line.class,
                CartQuoteRequest.class,
                CartQuoteRequest.Line.class)) {
            for (RecordComponent component : request.getRecordComponents()) {
                assertFalse(forbidden.matcher(component.getName()).find(),
                        request.getSimpleName() + "." + component.getName() + ": the server prices every order and resolves the table itself");
            }
        }
    }

}
