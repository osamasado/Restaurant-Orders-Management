package org.restaurantordersmanagement.backend.order.service;

import org.restaurantordersmanagement.backend.order.model.Order;
import org.restaurantordersmanagement.backend.settings.model.Config;
import org.restaurantordersmanagement.backend.settings.repository.ConfigRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderPricingService {

    private final ConfigRepository configRepository;

    public OrderPricingService(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    /**
     * Recomputes subtotal/tax/total from the order's own (already snapshotted)
     * items and the current Config tax rate, overwriting whatever was there
     * before - so any client-supplied total is structurally ignored, never
     * trusted.
     */
    public void applyPricing(Order order) {
        Config config = configRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No Config row found"));

        OrderPricing pricing = OrderPricingCalculator.calculate(order.getItems(), config.getTaxRate());
        order.setSubtotal(pricing.subtotal());
        order.setTaxAmount(pricing.taxAmount());
        order.setTotal(pricing.total());
    }

}
