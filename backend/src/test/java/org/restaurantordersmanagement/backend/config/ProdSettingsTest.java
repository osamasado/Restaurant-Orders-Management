package org.restaurantordersmanagement.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * The prod profile's database and port settings, resolved the way the application resolves them: from the two
 * property files plus the environment variables a host sets.
 */
class ProdSettingsTest {

    private static StandardEnvironment environmentWith(Map<String, Object> variables) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new ResourcePropertySource("classpath:application-prod.properties"));
        environment.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
        environment.getPropertySources().addFirst(new MapPropertySource("variables", variables));
        return environment;
    }

    @Test
    void theWholeJdbcUrlIsUsedAsIs() throws IOException {
        StandardEnvironment environment = environmentWith(Map.of("DATABASE_URL", "jdbc:postgresql://db:5432/orders"));

        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db:5432/orders");
    }

    @Test
    void theUrlIsBuiltFromHostPortAndName() throws IOException {
        StandardEnvironment environment = environmentWith(Map.of(
                "DATABASE_HOST", "dpg-abc-a", "DATABASE_PORT", "5433", "DATABASE_NAME", "orders"));

        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://dpg-abc-a:5433/orders");
    }

    @Test
    void thePortDefaultsTo5432() throws IOException {
        StandardEnvironment environment = environmentWith(Map.of("DATABASE_HOST", "dpg-abc-a", "DATABASE_NAME", "orders"));

        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://dpg-abc-a:5432/orders");
    }

    @Test
    void theUrlWinsWhenBothAreGiven() throws IOException {
        StandardEnvironment environment = environmentWith(Map.of(
                "DATABASE_URL", "jdbc:postgresql://db:5432/orders", "DATABASE_HOST", "other", "DATABASE_NAME", "x"));

        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db:5432/orders");
    }

    @Test
    void withNeitherTheStartFailsInsteadOfUsingAGuess() throws IOException {
        StandardEnvironment environment = environmentWith(Map.of());

        assertThatThrownBy(() -> environment.getProperty("spring.datasource.url"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DATABASE_HOST");
    }

    @Test
    void theApplicationListensOnPortWhenAHostSetsItAndOn8080Otherwise() throws IOException {
        assertThat(environmentWith(Map.of("PORT", "10000")).getProperty("server.port")).isEqualTo("10000");
        assertThat(environmentWith(Map.of()).getProperty("server.port")).isEqualTo("8080");
    }

}
