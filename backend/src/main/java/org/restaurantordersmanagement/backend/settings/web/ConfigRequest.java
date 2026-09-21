package org.restaurantordersmanagement.backend.settings.web;

import java.math.BigDecimal;
import java.util.Set;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.settings.model.PaymentMethod;
import org.restaurantordersmanagement.backend.settings.model.SymbolPosition;

public record ConfigRequest(
        String currencyCode,
        String currencySymbol,
        SymbolPosition symbolPosition,
        BigDecimal taxRate,
        Language defaultLanguage,
        Set<PaymentMethod> enabledPaymentMethods) {
}
