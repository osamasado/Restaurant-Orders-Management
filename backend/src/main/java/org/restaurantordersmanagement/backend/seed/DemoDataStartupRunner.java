package org.restaurantordersmanagement.backend.seed;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Runs the demo seed automatically on startup, dev profile only - nobody
 * wants random demo menu/tables/staff auto-created in a real deployment.
 * The actual seeding logic lives in {@link DemoDataSeeder} so it can be
 * tested directly without activating this profile.
 */
@Component
@Profile("dev")
public class DemoDataStartupRunner implements CommandLineRunner {

    private final DemoDataSeeder demoDataSeeder;

    public DemoDataStartupRunner(DemoDataSeeder demoDataSeeder) {
        this.demoDataSeeder = demoDataSeeder;
    }

    @Override
    public void run(String... args) {
        demoDataSeeder.seedAll();
    }

}
