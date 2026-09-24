package org.restaurantordersmanagement.backend.guest.web;

import java.util.List;

/** Only size ids and quantities - prices always come from the DB, never from the client. */
public record CartQuoteRequest(List<Line> items) {

    public record Line(Long sizeId, int quantity) {
    }

}
