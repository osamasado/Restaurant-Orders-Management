package org.restaurantordersmanagement.backend.settings.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.settings.model.Config;
import org.restaurantordersmanagement.backend.settings.model.PaymentMethod;
import org.restaurantordersmanagement.backend.settings.model.SymbolPosition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ConfigRepositoryTest {

    @Autowired
    private ConfigRepository configRepository;

    @Test
    void migrationSeedsSingleDefaultConfigRow() {
        List<Config> all = configRepository.findAll();

        assertEquals(1, all.size());
        Config config = all.get(0);
        assertEquals("EUR", config.getCurrencyCode());
        assertEquals("€", config.getCurrencySymbol());
        assertEquals(SymbolPosition.SUFFIX, config.getSymbolPosition());
        assertEquals(new BigDecimal("19.00"), config.getTaxRate());
        assertEquals(Language.DE, config.getDefaultLanguage());
        assertEquals(
                Set.of(PaymentMethod.CASH, PaymentMethod.CARD, PaymentMethod.PAYPAL, PaymentMethod.CASH_DESK),
                config.getEnabledPaymentMethods());
    }

    @Test
    void updatingEnabledPaymentMethodsPersists() {
        Config config = configRepository.findAll().get(0);
        config.getEnabledPaymentMethods().remove(PaymentMethod.PAYPAL);
        configRepository.saveAndFlush(config);

        Config reloaded = configRepository.findById(config.getId()).orElseThrow();
        assertTrue(reloaded.getEnabledPaymentMethods().contains(PaymentMethod.CASH));
        assertEquals(3, reloaded.getEnabledPaymentMethods().size());
    }

}
