package org.restaurantordersmanagement.backend.table.service;

import java.security.SecureRandom;
import java.util.List;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.restaurantordersmanagement.backend.table.web.TableRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** No @Transactional needed anywhere - Table has only scalar fields, same as RawMaterialService. */
@Service
public class TableService {

    /** Excludes easily-confused characters (0/O, 1/I) since this gets read off a screen and typed on a device. */
    private static final String PAIRING_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int PAIRING_CODE_LENGTH = 6;

    private final TableRepository tableRepository;
    private final SecureRandom random = new SecureRandom();

    public TableService(TableRepository tableRepository) {
        this.tableRepository = tableRepository;
    }

    public List<Table> findAll() {
        return tableRepository.findAll();
    }

    public Table findById(Long id) {
        return tableRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found"));
    }

    public Table create(TableRequest request) {
        Table table = new Table();
        applyRequest(table, request);
        return tableRepository.saveAndFlush(table);
    }

    public Table update(Long id, TableRequest request) {
        Table table = findById(id);
        applyRequest(table, request);
        return tableRepository.saveAndFlush(table);
    }

    /** Flushes so a table still referenced by an order (FK NOT NULL) throws here, caught by the controller as 409. */
    public void delete(Long id) {
        tableRepository.deleteById(id);
        tableRepository.flush();
    }

    /**
     * Generates a short pairing code and stores it as the paired device id.
     * lastSeenAt stays null until the device itself checks in - no such
     * check-in endpoint exists yet (that's the guest-ordering flow's job,
     * issues #17+), so a freshly paired table reads as OFFLINE until then.
     */
    public Table pair(Long id) {
        Table table = findById(id);
        table.setPairedDeviceId(generatePairingCode());
        table.setLastSeenAt(null);
        return tableRepository.saveAndFlush(table);
    }

    public Table unpair(Long id) {
        Table table = findById(id);
        table.setPairedDeviceId(null);
        table.setLastSeenAt(null);
        return tableRepository.saveAndFlush(table);
    }

    private void applyRequest(Table table, TableRequest request) {
        table.setTableNumber(request.tableNumber());
        table.setRoom(request.room());
        table.setSeats(request.seats());
    }

    private String generatePairingCode() {
        StringBuilder code = new StringBuilder(PAIRING_CODE_LENGTH);
        for (int i = 0; i < PAIRING_CODE_LENGTH; i++) {
            code.append(PAIRING_CODE_ALPHABET.charAt(random.nextInt(PAIRING_CODE_ALPHABET.length())));
        }
        return code.toString();
    }

}
