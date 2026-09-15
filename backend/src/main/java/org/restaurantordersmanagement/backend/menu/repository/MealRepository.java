package org.restaurantordersmanagement.backend.menu.repository;

import org.restaurantordersmanagement.backend.menu.model.Meal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MealRepository extends JpaRepository<Meal, Long> {
}
