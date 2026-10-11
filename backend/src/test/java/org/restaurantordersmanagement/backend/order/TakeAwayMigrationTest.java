package org.restaurantordersmanagement.backend.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
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
 * Migration V17 (the reserved take-away table and the order token) on an installation that already has tables and
 * orders: they are untouched, the take-away table appears once, and old orders have no token. Runs in its own schema
 * of the test database, which is dropped afterwards.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TakeAwayMigrationTest {

    private static final String SCHEMA = "v17_migration_test";

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
    void existingTablesAndOrdersAreUntouchedAndTheTakeAwayTableAppearsOnce() throws Exception {
        flyway("16").migrate();
        java.sql.Connection connection = dataSource.getConnection();
        try (java.sql.Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + SCHEMA);
        }
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        jdbc.update("INSERT INTO restaurant_table (table_number, room, seats, paired_device_id) VALUES ('7', 'Front', 4, 'ABC234')");
        Long tableId = jdbc.queryForObject("SELECT id FROM restaurant_table WHERE table_number = '7'", Long.class);
        jdbc.update("INSERT INTO restaurant_order (order_number, display_number, table_id, status, placed_at) VALUES (1, 1, ?, 'SERVED', now())", tableId);

        flyway("latest").migrate();

        assertEquals(List.of("7"), jdbc.queryForList("SELECT table_number FROM restaurant_table WHERE NOT take_away", String.class),
                "the existing table is untouched and not a take-away table");
        assertEquals("ABC234", jdbc.queryForObject("SELECT paired_device_id FROM restaurant_table WHERE table_number = '7'", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM restaurant_table WHERE take_away", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT seats FROM restaurant_table WHERE take_away", Integer.class));
        assertNull(jdbc.queryForObject("SELECT paired_device_id FROM restaurant_table WHERE take_away", String.class));
        assertNull(jdbc.queryForObject("SELECT access_token_hash FROM restaurant_order", String.class),
                "an order placed before the migration has no token");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM restaurant_order", Integer.class));
    }

}
