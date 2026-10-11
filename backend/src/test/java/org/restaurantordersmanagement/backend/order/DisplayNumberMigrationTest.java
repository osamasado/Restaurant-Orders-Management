package org.restaurantordersmanagement.backend.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * The migration that adds the displayed number (V16), run on an installation that already has orders: every existing
 * order is shown with the number it always had, and the series goes on where it was. It runs in its own schema of the
 * test database, so nothing else is touched, and the schema is dropped afterwards.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DisplayNumberMigrationTest {

    private static final String SCHEMA = "v16_migration_test";

    @Autowired
    private DataSource dataSource;

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .cleanDisabled(false)
                .target(target)
                .load();
    }

    @AfterEach
    void dropTheSchema() {
        flyway("latest").clean();
    }

    @Test
    void existingOrdersKeepTheirNumbersAndTheSeriesGoesOnWhereItWas() {
        flyway("15").migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(
                connectionIn(SCHEMA), true));
        jdbc.update("INSERT INTO restaurant_table (table_number, room, seats) VALUES ('m1', 'Front', 2)");
        Long tableId = jdbc.queryForObject("SELECT id FROM restaurant_table WHERE table_number = 'm1'", Long.class);
        jdbc.update("INSERT INTO restaurant_order (order_number, table_id, status, placed_at) VALUES (1, ?, 'SERVED', now())", tableId);
        jdbc.update("INSERT INTO restaurant_order (order_number, table_id, status, placed_at) VALUES (2, ?, 'SERVED', now())", tableId);
        jdbc.update("INSERT INTO restaurant_order (order_number, table_id, status, placed_at) VALUES (3, ?, 'PREPARING', now())", tableId);
        jdbc.update("INSERT INTO restaurant_order (order_number, table_id, status) VALUES (NULL, ?, 'DRAFT')", tableId);
        jdbc.update("UPDATE order_number_counter SET next_value = 4");

        flyway("latest").migrate();

        assertEquals(java.util.List.of(1, 2, 3),
                jdbc.queryForList("SELECT display_number FROM restaurant_order WHERE order_number IS NOT NULL ORDER BY order_number", Integer.class),
                "every existing order is shown with the number it had");
        assertNull(jdbc.queryForObject("SELECT display_number FROM restaurant_order WHERE order_number IS NULL", Integer.class),
                "a draft has no number");
        assertEquals(4, jdbc.queryForObject("SELECT next_display_value FROM order_number_counter", Integer.class),
                "the series goes on where it was");
        assertEquals(4, jdbc.queryForObject("SELECT next_value FROM order_number_counter", Integer.class),
                "the internal counter is untouched");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM order_number_reset", Integer.class));
    }

    private java.sql.Connection connectionIn(String schema) {
        try {
            java.sql.Connection connection = dataSource.getConnection();
            try (java.sql.Statement statement = connection.createStatement()) {
                statement.execute("SET search_path TO " + schema);
            }
            return connection;
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

}
