package org.restaurantordersmanagement.backend.order.web;

import java.util.List;

/** Our own paging envelope rather than Spring's Page, whose JSON shape is not a stable contract. */
public record AdminOrderPageResponse(List<AdminOrderRowResponse> orders, int page, int totalPages, long totalOrders) {
}
