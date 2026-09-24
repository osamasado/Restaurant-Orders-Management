package org.restaurantordersmanagement.backend.guest.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * A guest device's identity is the pairing code an admin generated for its
 * table (TableService.pair). The device keeps the code and sends it back;
 * the server always resolves the table itself, never trusting a table id
 * from the client.
 */
@Service
public class GuestDeviceService {

    private final TableRepository tableRepository;

    public GuestDeviceService(TableRepository tableRepository) {
        this.tableRepository = tableRepository;
    }

    /** Also refreshes lastSeenAt, so the admin tables view shows the device online. */
    public Table claim(String code) {
        Table table = findPairedTable(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown pairing code"));
        table.setLastSeenAt(Instant.now());
        return tableRepository.save(table);
    }

    /** For order submission: an unknown or unpaired code is a 403, not a 404. */
    public Table requirePairedTable(String code) {
        return findPairedTable(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Device not paired"));
    }

    private Optional<Table> findPairedTable(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return tableRepository.findByPairedDeviceId(code.trim().toUpperCase(Locale.ROOT));
    }

}
