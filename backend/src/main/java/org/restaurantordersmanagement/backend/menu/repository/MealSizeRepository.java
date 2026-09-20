package org.restaurantordersmanagement.backend.menu.repository;

import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealSizeRepository extends JpaRepository<MealSize, Long> {
}
