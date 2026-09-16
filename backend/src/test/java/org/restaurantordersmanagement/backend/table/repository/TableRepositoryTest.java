package org.restaurantordersmanagement.backend.table.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TableRepositoryTest {

    @Autowired
    private TableRepository tableRepository;

    @Test
    void savesUnpairedTable() {
        Table table = new Table();
        table.setTableNumber("12");
        table.setRoom("Main hall");
        table.setSeats(4);

        Table saved = tableRepository.saveAndFlush(table);

        Optional<Table> found = tableRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("12", found.get().getTableNumber());
        assertEquals("Main hall", found.get().getRoom());
        assertEquals(4, found.get().getSeats());
        assertNull(found.get().getPairedDeviceId());
        assertNull(found.get().getLastSeenAt());
    }

    @Test
    void savesPairedTableWithLastSeenTimestamp() {
        Table table = new Table();
        table.setTableNumber("Patio 3");
        table.setRoom("Patio");
        table.setSeats(2);
        table.setPairedDeviceId("device-abc-123");
        Instant lastSeenAt = Instant.parse("2026-09-16T12:00:00Z");
        table.setLastSeenAt(lastSeenAt);

        Table saved = tableRepository.saveAndFlush(table);

        Optional<Table> found = tableRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("device-abc-123", found.get().getPairedDeviceId());
        assertEquals(lastSeenAt, found.get().getLastSeenAt());
    }

}
