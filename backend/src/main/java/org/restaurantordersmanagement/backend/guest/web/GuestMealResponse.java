package org.restaurantordersmanagement.backend.guest.web;

import java.util.List;

public record GuestMealResponse(
        Long id,
        Long categoryId,
        String name,
        String description,
        String preparationMethod,
        List<String> ingredients,
        String imageUrl,
        List<GuestMealSizeResponse> sizes) {
}
