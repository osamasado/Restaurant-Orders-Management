package org.restaurantordersmanagement.backend.table.web;

import java.time.Duration;
import java.time.Instant;
import org.restaurantordersmanagement.backend.table.model.Table;

public record TableResponse(
        Long id,
        String tableNumber,
        String room,
        int seats,
        String pairedDeviceId,
        DeviceStatus deviceStatus,
        Instant lastSeenAt) {

    /** A paired device that hasn't checked in within this window reads as OFFLINE rather than ONLINE. */
    private static final Duration ONLINE_THRESHOLD = Duration.ofMinutes(5);

    public static TableResponse from(Table table) {
        return new TableResponse(
                table.getId(),
                table.getTableNumber(),
                table.getRoom(),
                table.getSeats(),
                table.getPairedDeviceId(),
                deviceStatus(table),
                table.getLastSeenAt());
    }

    private static DeviceStatus deviceStatus(Table table) {
        if (table.getPairedDeviceId() == null) {
            return DeviceStatus.UNPAIRED;
        }
        Instant lastSeenAt = table.getLastSeenAt();
        if (lastSeenAt != null && lastSeenAt.isAfter(Instant.now().minus(ONLINE_THRESHOLD))) {
            return DeviceStatus.ONLINE;
        }
        return DeviceStatus.OFFLINE;
    }

}
