package org.restaurantordersmanagement.backend.guest.web;

import java.util.List;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.settings.model.PaymentMethod;

/**
 * No prices, totals or table id - the server resolves the table from
 * deviceCode and prices every line from the DB. language picks which
 * translation gets snapshotted onto the order lines.
 *
 * <p>takeAway true means a take-away order: no device code (the order is placed against the one reserved take-away
 * table, which the server finds itself); false or absent means an order for the table the device code is paired with.
 */
public record GuestOrderRequest(
        String deviceCode, Language language, PaymentMethod paymentMethod, List<Line> items, Boolean takeAway) {

    public record Line(Long sizeId, int quantity, String note) {
    }

}
