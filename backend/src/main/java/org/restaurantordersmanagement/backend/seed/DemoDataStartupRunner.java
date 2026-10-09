package org.restaurantordersmanagement.backend.seed;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the demo seed on startup, only when {@code app.seed.demo} is {@code true}. The dev profile switches it on;
 * a container (or any other deployment) switches it on with {@code APP_SEED_DEMO=true} to get the demo menu, tables
 * and staff on an empty database. It is off everywhere else: nobody wants random demo menu, tables and staff
 * (every account with PIN 1234) auto-created in a real deployment. The seed is idempotent, so leaving the switch
 * on only tops up what is missing and never overwrites an admin's changes. The actual seeding logic lives in
 * {@link DemoDataSeeder} so it can be tested directly without this switch.
 */
@Component
@ConditionalOnProperty(name = "app.seed.demo", havingValue = "true")
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
