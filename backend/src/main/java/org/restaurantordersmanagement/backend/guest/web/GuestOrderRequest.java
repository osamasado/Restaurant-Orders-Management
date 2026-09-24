package org.restaurantordersmanagement.backend.guest.web;

import java.util.List;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.settings.model.PaymentMethod;

/**
 * No prices, totals or table id - the server resolves the table from
 * deviceCode and prices every line from the DB. language picks which
 * translation gets snapshotted onto the order lines.
 */
public record GuestOrderRequest(String deviceCode, Language language, PaymentMethod paymentMethod, List<Line> items) {

    public record Line(Long sizeId, int quantity, String note) {
    }

}
