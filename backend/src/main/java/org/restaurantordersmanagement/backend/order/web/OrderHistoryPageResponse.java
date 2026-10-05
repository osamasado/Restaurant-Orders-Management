package org.restaurantordersmanagement.backend.order.web;

import java.util.List;

/** Our own paging envelope rather than Spring's Page, whose JSON shape is not a stable contract. */
public record OrderHistoryPageResponse(List<OrderHistoryResponse> orders, int page, int totalPages, long totalOrders) {
}
