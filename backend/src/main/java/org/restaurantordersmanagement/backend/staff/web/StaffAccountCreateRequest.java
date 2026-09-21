package org.restaurantordersmanagement.backend.staff.web;

import org.restaurantordersmanagement.backend.staff.model.Role;

public record StaffAccountCreateRequest(String name, Role role, String pin) {
}
