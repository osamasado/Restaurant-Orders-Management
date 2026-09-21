package org.restaurantordersmanagement.backend.settings.web;

import java.math.BigDecimal;
import java.util.Set;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.settings.model.Config;
import org.restaurantordersmanagement.backend.settings.model.PaymentMethod;
import org.restaurantordersmanagement.backend.settings.model.SymbolPosition;

public record ConfigResponse(
        Long id,
        String currencyCode,
        String currencySymbol,
        SymbolPosition symbolPosition,
        BigDecimal taxRate,
        Language defaultLanguage,
        Set<PaymentMethod> enabledPaymentMethods) {

    public static ConfigResponse from(Config config) {
        return new ConfigResponse(
                config.getId(),
                config.getCurrencyCode(),
                config.getCurrencySymbol(),
                config.getSymbolPosition(),
                config.getTaxRate(),
                config.getDefaultLanguage(),
                Set.copyOf(config.getEnabledPaymentMethods()));
    }

}
