package org.restaurantordersmanagement.backend.guest.service;

import java.util.ArrayList;
import java.util.List;
import org.restaurantordersmanagement.backend.guest.web.GuestOrderRequest;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.repository.MealSizeRepository;
import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.order.model.OrderItem;
import org.restaurantordersmanagement.backend.order.model.OrderStatus;
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
 */
@Service
public class GuestOrderService {

    static final int MAX_NOTE_LENGTH = 200;

    private final GuestDeviceService guestDeviceService;
    private final SettingsService settingsService;
    private final MealSizeRepository mealSizeRepository;
    private final OrderStateMachineService orderStateMachineService;

    public GuestOrderService(
            GuestDeviceService guestDeviceService,
            SettingsService settingsService,
            MealSizeRepository mealSizeRepository,
            OrderStateMachineService orderStateMachineService) {
        this.guestDeviceService = guestDeviceService;
        this.settingsService = settingsService;
        this.mealSizeRepository = mealSizeRepository;
        this.orderStateMachineService = orderStateMachineService;
    }

    @Transactional
    public Order submit(GuestOrderRequest request) {
        if (request == null) {
            throw badRequest("Request body is required");
        }
        Table table = guestDeviceService.requirePairedTable(request.deviceCode());

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
        return orderStateMachineService.transition(order, OrderStatus.SUBMITTED, null);
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

}
