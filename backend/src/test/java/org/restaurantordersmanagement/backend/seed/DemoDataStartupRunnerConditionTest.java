package org.restaurantordersmanagement.backend.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The demo seed on startup is a switch, not a profile: it runs only when {@code app.seed.demo} is {@code true}
 * (the dev profile sets it, a container sets it with APP_SEED_DEMO), and a deployment that sets nothing never seeds.
 * No database is needed: only whether the runner is registered is checked.
 */
class DemoDataStartupRunnerConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DemoDataStartupRunner.class)
            .withBean(DemoDataSeeder.class, () -> mock(DemoDataSeeder.class));

    @Test
    void doesNotSeedWhenTheSwitchIsNotSet() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DemoDataStartupRunner.class));
    }

    @Test
    void doesNotSeedWhenTheSwitchIsOff() {
        runner.withPropertyValues("app.seed.demo=false")
                .run(context -> assertThat(context).doesNotHaveBean(DemoDataStartupRunner.class));
    }

    @Test
    void seedsWhenTheSwitchIsOn() {
        runner.withPropertyValues("app.seed.demo=true")
                .run(context -> assertThat(context).hasSingleBean(DemoDataStartupRunner.class));
    }

}
