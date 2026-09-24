package org.restaurantordersmanagement.backend.guest.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.restaurantordersmanagement.backend.guest.web.CartQuoteRequest;
import org.restaurantordersmanagement.backend.guest.web.CartQuoteResponse;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.repository.MealSizeRepository;
import org.restaurantordersmanagement.backend.order.model.OrderItem;
import org.restaurantordersmanagement.backend.order.service.OrderPricing;
import org.restaurantordersmanagement.backend.order.service.OrderPricingCalculator;
import org.restaurantordersmanagement.backend.settings.service.SettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Prices a guest's cart with the same OrderPricingCalculator an order goes
 * through on submission, so the cart preview can't drift from the real total.
 * Builds transient (never persisted) OrderItems just to feed the calculator.
 */
@Service
public class GuestCartQuoteService {

    /** Same bounds as the guest detail screen's quantity stepper. */
    static final int MIN_QUANTITY = 1;
    static final int MAX_QUANTITY = 20;

    private final MealSizeRepository mealSizeRepository;
    private final SettingsService settingsService;

    public GuestCartQuoteService(MealSizeRepository mealSizeRepository, SettingsService settingsService) {
        this.mealSizeRepository = mealSizeRepository;
        this.settingsService = settingsService;
    }

    @Transactional(readOnly = true)
    public CartQuoteResponse quote(CartQuoteRequest request) {
        if (request == null || request.items() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items is required");
        }

        List<OrderItem> items = new ArrayList<>();
        List<CartQuoteResponse.LineQuote> lines = new ArrayList<>();
        for (CartQuoteRequest.Line line : request.items()) {
            if (line == null || line.sizeId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sizeId is required");
            }
            if (line.quantity() < MIN_QUANTITY || line.quantity() > MAX_QUANTITY) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "quantity must be between " + MIN_QUANTITY + " and " + MAX_QUANTITY);
            }
            MealSize size = mealSizeRepository.findById(line.sizeId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meal size not found"));

            OrderItem item = new OrderItem();
            item.setUnitPrice(size.getPrice());
            item.setQuantity(line.quantity());
            items.add(item);

            BigDecimal lineTotal = size.getPrice().multiply(BigDecimal.valueOf(line.quantity()));
            lines.add(new CartQuoteResponse.LineQuote(
                    size.getId(), size.getPrice(), lineTotal, size.getMeal().isAvailable()));
        }

        BigDecimal taxRate = settingsService.getConfig().getTaxRate();
        OrderPricing pricing = OrderPricingCalculator.calculate(items, taxRate);
        return new CartQuoteResponse(pricing.subtotal(), taxRate, pricing.taxAmount(), pricing.total(), lines);
    }

}
