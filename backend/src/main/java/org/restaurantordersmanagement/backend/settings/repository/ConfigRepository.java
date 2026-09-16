package org.restaurantordersmanagement.backend.settings.repository;

import org.restaurantordersmanagement.backend.settings.model.Config;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigRepository extends JpaRepository<Config, Long> {
}
