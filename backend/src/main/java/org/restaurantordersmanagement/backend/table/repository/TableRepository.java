package org.restaurantordersmanagement.backend.table.repository;

import java.util.Optional;
import org.restaurantordersmanagement.backend.table.model.Table;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TableRepository extends JpaRepository<Table, Long> {

    Optional<Table> findByTableNumber(String tableNumber);

    /** The admin-issued pairing code a guest device identifies itself with. */
    Optional<Table> findByPairedDeviceId(String pairedDeviceId);

}
