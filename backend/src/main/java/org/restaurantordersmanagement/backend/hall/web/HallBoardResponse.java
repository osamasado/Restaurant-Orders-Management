package org.restaurantordersmanagement.backend.hall.web;

import java.util.List;

/**
 * Everything the hall board may show: order numbers, nothing else. Two lists
 * of plain integers, so no name, price or table number can be added to this
 * screen by accident.
 */
public record HallBoardResponse(List<Integer> preparing, List<Integer> ready) {
}
