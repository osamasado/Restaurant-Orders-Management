package org.restaurantordersmanagement.backend.order.repository;

import org.restaurantordersmanagement.backend.order.model.Order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
