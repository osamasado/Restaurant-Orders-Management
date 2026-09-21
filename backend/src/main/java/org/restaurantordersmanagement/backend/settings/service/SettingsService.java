package org.restaurantordersmanagement.backend.settings.service;

import org.hibernate.Hibernate;
import org.restaurantordersmanagement.backend.settings.model.Config;
import org.restaurantordersmanagement.backend.settings.repository.ConfigRepository;
import org.restaurantordersmanagement.backend.settings.web.ConfigRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Config is a singleton row (seeded by V9__config.sql, never created or
 * deleted) - this service only ever reads/updates that one row.
 */
@Service
public class SettingsService {

    private final ConfigRepository configRepository;

    public SettingsService(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    /**
     * @Transactional(readOnly = true) so Hibernate.initialize() below runs in
     * the same session as the fetch - enabledPaymentMethods is an
     * @ElementCollection (LAZY by default, same as Meal/Category's
     * @OneToMany collections), and with spring.jpa.open-in-view=false there's
     * no session left by the time a controller would otherwise read it.
     */
    @Transactional(readOnly = true)
    public Config getConfig() {
        Config config = findRow();
        Hibernate.initialize(config.getEnabledPaymentMethods());
        return config;
    }

    /**
     * clear()+addAll() on a Set-valued element collection is safe here (unlike
     * Meal/Category's translation replace in #13): Hibernate diffs a plain
     * value-typed Set by element - only genuinely removed/added payment
     * methods get a DELETE/INSERT, elements present before and after are left
     * untouched - so there's no entity-identity insert-before-delete ordering
     * to collide on.
     */
    @Transactional
    public Config updateConfig(ConfigRequest request) {
        Config config = findRow();
        config.setCurrencyCode(request.currencyCode());
        config.setCurrencySymbol(request.currencySymbol());
        config.setSymbolPosition(request.symbolPosition());
        config.setTaxRate(request.taxRate());
        config.setDefaultLanguage(request.defaultLanguage());
        config.getEnabledPaymentMethods().clear();
        config.getEnabledPaymentMethods().addAll(request.enabledPaymentMethods());
        return configRepository.saveAndFlush(config);
    }

    private Config findRow() {
        return configRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No Config row found"));
    }

}
