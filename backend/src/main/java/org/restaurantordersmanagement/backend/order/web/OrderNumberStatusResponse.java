package org.restaurantordersmanagement.backend.order.web;

import java.time.Instant;

/**
 * Where the displayed order number stands, for the Settings page: the number the next order gets, how many orders
 * are still open (a reset is refused while there are any), and the last reset if there was one.
 */
public record OrderNumberStatusResponse(int nextDisplayNumber, long openOrders, LastReset lastReset) {

    public record LastReset(Instant resetAt, String staffName) {
    }

}
