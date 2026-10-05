package org.restaurantordersmanagement.backend.staff.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface StaffAccountRepository extends JpaRepository<StaffAccount, Long> {

    Optional<StaffAccount> findByName(String name);

    /**
     * Locks every account with this role until the transaction ends, so two
     * admins demoting or deleting each other at the same moment are handled one
     * after the other and cannot both see "there is still another admin".
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<StaffAccount> findByRole(Role role);

}
