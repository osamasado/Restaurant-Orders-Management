package org.restaurantordersmanagement.backend.guest.web;

import java.util.List;

public record GuestCategoryResponse(Long id, String name, List<GuestMealResponse> meals) {
}
