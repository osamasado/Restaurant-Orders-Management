package org.restaurantordersmanagement.backend.menu.repository;

import org.restaurantordersmanagement.backend.menu.model.Recipe;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
}
