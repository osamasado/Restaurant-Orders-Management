package org.restaurantordersmanagement.backend.table.repository;

import java.util.List;
import java.util.Optional;
import org.restaurantordersmanagement.backend.table.model.Table;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TableRepository extends JpaRepository<Table, Long> {

    Optional<Table> findByTableNumber(String tableNumber);

    /** The tables an admin manages: everything but the reserved take-away table. */
    List<Table> findByTakeAwayFalse();

    Optional<Table> findByTakeAwayTrue();

    /** The admin-issued pairing code a guest device identifies itself with. */
    Optional<Table> findByPairedDeviceId(String pairedDeviceId);

}
