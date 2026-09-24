package org.restaurantordersmanagement.backend.guest.web;

import org.restaurantordersmanagement.backend.table.model.Table;

/** Deliberately omits pairedDeviceId - the device already has its code. */
public record GuestTableResponse(Long tableId, String tableNumber, String room) {

    public static GuestTableResponse from(Table table) {
        return new GuestTableResponse(table.getId(), table.getTableNumber(), table.getRoom());
    }

}
