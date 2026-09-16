package org.restaurantordersmanagement.backend.staff.repository;

import org.restaurantordersmanagement.backend.staff.model.StaffAccount;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAccountRepository extends JpaRepository<StaffAccount, Long> {
}
