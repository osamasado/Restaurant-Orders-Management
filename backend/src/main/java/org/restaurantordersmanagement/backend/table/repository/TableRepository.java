package org.restaurantordersmanagement.backend.table.repository;

import org.restaurantordersmanagement.backend.table.model.Table;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TableRepository extends JpaRepository<Table, Long> {
}
