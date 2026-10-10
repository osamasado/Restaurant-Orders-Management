package org.restaurantordersmanagement.backend.seed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Runs the demo seed on startup, only when {@code app.seed.demo} is {@code true}. The dev profile switches it on;
 * a container (or any other deployment) switches it on with {@code APP_SEED_DEMO=true} to get the demo menu, tables
 * and staff on an empty database. It is off everywhere else: nobody wants random demo menu, tables and staff
 * (every account with PIN 1234) auto-created in a real deployment. The seed is idempotent, so leaving the switch
 * on only tops up what is missing and never overwrites an admin's changes. The actual seeding logic lives in
 * {@link DemoDataSeeder} so it can be tested directly without this switch.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.seed.demo", havingValue = "true")
public class DemoDataStartupRunner implements CommandLineRunner {

    private final DemoDataSeeder demoDataSeeder;
    private final Environment environment;

    public DemoDataStartupRunner(DemoDataSeeder demoDataSeeder, Environment environment) {
        this.demoDataSeeder = demoDataSeeder;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            log.warn("The demo data is switched on (APP_SEED_DEMO) in the prod profile: demo staff accounts with the "
                    + "public PIN 1234 exist. That is for demos only; use BOOTSTRAP_ADMIN_NAME and BOOTSTRAP_ADMIN_PIN "
                    + "for a real deployment and turn the demo data off.");
        }
        demoDataSeeder.seedAll();
    }

}
