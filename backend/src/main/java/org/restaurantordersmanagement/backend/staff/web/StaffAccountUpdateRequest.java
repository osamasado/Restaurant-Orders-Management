package org.restaurantordersmanagement.backend.staff.web;

import org.restaurantordersmanagement.backend.staff.model.Role;

public record StaffAccountUpdateRequest(String name, Role role) {
}
