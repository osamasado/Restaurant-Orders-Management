package org.restaurantordersmanagement.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SpaConfigConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(SpaConfig.class);

    @Test
    void isOffWhenTheAppBuildIsNotInTheJar() {
        runner.run(context -> assertThat(context).doesNotHaveBean(SpaConfig.class));
    }

    @Test
    void isOnWhenTheAppBuildIsThere() {
        runner.withPropertyValues("app.spa.index=classpath:/spa-test/index.html")
                .run(context -> assertThat(context).hasSingleBean(SpaConfig.class));
    }

}
