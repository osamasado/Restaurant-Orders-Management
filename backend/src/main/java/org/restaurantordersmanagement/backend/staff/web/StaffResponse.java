package org.restaurantordersmanagement.backend.staff.web;

import java.time.Instant;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;

public record StaffResponse(Long id, String name, Role role, Instant lastSeenAt) {

    public static StaffResponse from(StaffAccount staffAccount) {
        return new StaffResponse(
                staffAccount.getId(),
                staffAccount.getName(),
                staffAccount.getRole(),
                staffAccount.getLastSeenAt());
    }

}
