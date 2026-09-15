package org.restaurantordersmanagement.backend.menu.repository;

import org.restaurantordersmanagement.backend.menu.model.RawMaterial;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RawMaterialRepository extends JpaRepository<RawMaterial, Long> {
}
