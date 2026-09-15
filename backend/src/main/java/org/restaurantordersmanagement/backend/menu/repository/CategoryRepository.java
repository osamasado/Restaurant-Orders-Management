package org.restaurantordersmanagement.backend.menu.repository;

import org.restaurantordersmanagement.backend.menu.model.Category;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
