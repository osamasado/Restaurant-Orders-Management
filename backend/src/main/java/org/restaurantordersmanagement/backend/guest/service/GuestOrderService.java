package org.restaurantordersmanagement.backend.guest.service;

import java.util.ArrayList;
import java.util.List;
import org.restaurantordersmanagement.backend.guest.web.GuestOrderRequest;
import org.restaurantordersmanagement.backend.guest.web.GuestOrderStatusResponse;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.repository.MealSizeRepository;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderItem;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
import org.restaurantordersmanagement.backend.order.repository.OrderRepository;
import org.restaurantordersmanagement.backend.order.service.OrderStateMachineService;
import org.restaurantordersmanagement.backend.settings.service.SettingsService;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns a guest's cart into a submitted order. Everything is validated and
 * re-read from the DB before the order exists; the DRAFT -> SUBMITTED
 * transition then assigns the locked order number and the authoritative
 * pricing (OrderStateMachineService), all in this one transaction - a
 * rejected line means no order and no consumed order number.
 * Also serves a guest the live status of their own order (#22), proven by the order's secret.
 */
@Service
public class GuestOrderService {

    static final int MAX_NOTE_LENGTH = 200;

    /** The submitted order and the secret its guest follows it with (the secret is not stored, only its hash). */
    public record PlacedOrder(Order order, String accessToken) {
    }

    private final GuestDeviceService guestDeviceService;
    private final SettingsService settingsService;
    private final MealSizeRepository mealSizeRepository;
    private final OrderStateMachineService orderStateMachineService;
    private final OrderRepository orderRepository;

    public GuestOrderService(
            GuestDeviceService guestDeviceService,
            SettingsService settingsService,
            MealSizeRepository mealSizeRepository,
            OrderStateMachineService orderStateMachineService,
            OrderRepository orderRepository) {
        this.guestDeviceService = guestDeviceService;
        this.settingsService = settingsService;
        this.mealSizeRepository = mealSizeRepository;
        this.orderStateMachineService = orderStateMachineService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public PlacedOrder submit(GuestOrderRequest request) {
        if (request == null) {
            throw badRequest("Request body is required");
        }
        Table table = tableFor(request);

        if (request.paymentMethod() == null
                || !settingsService.getConfig().getEnabledPaymentMethods().contains(request.paymentMethod())) {
            throw badRequest("Payment method is not enabled");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw badRequest("At least one item is required");
        }
        Language language = request.language() != null ? request.language() : Language.EN;

        Order order = new Order();
        order.setTable(table);
        order.setPaymentMethod(request.paymentMethod());
        String accessToken = OrderAccessTokens.newToken();
        order.setAccessTokenHash(OrderAccessTokens.hash(accessToken));

        List<Long> unavailableSizeIds = new ArrayList<>();
        for (GuestOrderRequest.Line line : request.items()) {
            MealSize size = resolveSize(line);
            if (!size.getMeal().isAvailable()) {
                unavailableSizeIds.add(size.getId());
                continue;
            }
            OrderItem item = OrderItem.snapshotFrom(size, language, line.quantity(), normalizeNote(line.note()));
            item.setOrder(order);
            order.getItems().add(item);
        }
        if (!unavailableSizeIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Meal sizes no longer available: " + unavailableSizeIds);
        }

        // null actor: a guest submit has no staff account (OrderStatusHistory.changedBy is nullable for this).
        Order submitted = orderStateMachineService.transition(order, OrderStatus.SUBMITTED, null);
        return new PlacedOrder(submitted, accessToken);
    }

    /**
     * A take-away order carries no device code and is placed against the reserved take-away table; any other order
     * is for the table the device code is paired with. Sending both is a mistake, not a choice.
     */
    private Table tableFor(GuestOrderRequest request) {
        if (!Boolean.TRUE.equals(request.takeAway())) {
            return guestDeviceService.requirePairedTable(request.deviceCode());
        }
        if (request.deviceCode() != null && !request.deviceCode().isBlank()) {
            throw badRequest("A take-away order carries no device code");
        }
        return guestDeviceService.takeAwayTable();
    }

    /**
     * The order's own secret always works. A dine-in order can also be followed with the code of the device its table
     * is paired with (the rule before the secret existed, which keeps a screen that was opened before an update
     * working); a take-away order has no device, so only its secret opens it. Nothing at all is a 403; a wrong secret
     * and another table's order are the same 404, so an id tells nothing.
     */
    @Transactional(readOnly = true)
    public GuestOrderStatusResponse getStatus(Long orderId, String orderToken, String deviceCode) {
        boolean hasToken = orderToken != null && !orderToken.isBlank();
        boolean hasDeviceCode = deviceCode != null && !deviceCode.isBlank();
        if (!hasToken && !hasDeviceCode) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An order token or a device code is required");
        }

        if (hasToken) {
            Order order = orderRepository.findById(orderId).orElseThrow(GuestOrderService::orderNotFound);
            if (!OrderAccessTokens.matches(orderToken, order.getAccessTokenHash())) {
                throw orderNotFound();
            }
            return GuestOrderStatusResponse.from(order);
        }

        Table deviceTable = guestDeviceService.requirePairedTable(deviceCode);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(GuestOrderService::orderNotFound);

        if (!deviceTable.getId().equals(order.getTable().getId())) {
            throw orderNotFound();
        }

        return GuestOrderStatusResponse.from(order);
    }

    private MealSize resolveSize(GuestOrderRequest.Line line) {
        if (line == null || line.sizeId() == null) {
            throw badRequest("sizeId is required");
        }
        if (line.quantity() < GuestCartQuoteService.MIN_QUANTITY || line.quantity() > GuestCartQuoteService.MAX_QUANTITY) {
            throw badRequest("quantity must be between " + GuestCartQuoteService.MIN_QUANTITY
                    + " and " + GuestCartQuoteService.MAX_QUANTITY);
        }
        if (line.note() != null && line.note().length() > MAX_NOTE_LENGTH) {
            throw badRequest("note must be at most " + MAX_NOTE_LENGTH + " characters");
        }
        return mealSizeRepository.findById(line.sizeId()).orElseThrow(() -> badRequest("Meal size not found"));
    }

    private static String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException orderNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
    }

}
