package org.restaurantordersmanagement.backend.hall.web;

import java.util.List;

/**
 * Everything the hall board may show: each order's number and the table it
 * is for. Nothing else - no names, prices or items - so none can be added to
 * this public screen by accident.
 */
public record HallBoardResponse(List<Entry> preparing, List<Entry> ready) {

    public record Entry(Integer orderNumber, String tableNumber) {
    }

}
