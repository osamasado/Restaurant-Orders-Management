package org.restaurantordersmanagement.backend.staff.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs the staff bootstrap on every start, before the demo seed (which then only adds what is missing). All the
 * decisions are in {@link StaffBootstrapService}; this only reads the settings.
 */
@Component
@Order(1)
public class StaffBootstrapRunner implements CommandLineRunner {

    private final StaffBootstrapService staffBootstrapService;
    private final StaffBootstrapSettings settings;

    public StaffBootstrapRunner(
            StaffBootstrapService staffBootstrapService,
            @Value("${bootstrap.admin.name:}") String adminName,
            @Value("${bootstrap.admin.pin:}") String adminPin,
            @Value("${bootstrap.admin.reset:false}") boolean adminReset,
            @Value("${bootstrap.kitchen.name:}") String kitchenName,
            @Value("${bootstrap.kitchen.pin:}") String kitchenPin) {
        this.staffBootstrapService = staffBootstrapService;
        this.settings = StaffBootstrapSettings.of(adminName, adminPin, adminReset, kitchenName, kitchenPin);
    }

    @Override
    public void run(String... args) {
        staffBootstrapService.bootstrap(settings);
    }

}
