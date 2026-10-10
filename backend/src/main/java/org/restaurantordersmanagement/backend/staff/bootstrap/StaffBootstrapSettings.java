package org.restaurantordersmanagement.backend.staff.bootstrap;

/**
 * What the operator gave the deployment for the first staff accounts (environment variables BOOTSTRAP_ADMIN_NAME,
 * BOOTSTRAP_ADMIN_PIN, BOOTSTRAP_ADMIN_RESET, BOOTSTRAP_KITCHEN_NAME and BOOTSTRAP_KITCHEN_PIN). A blank value counts
 * as not given, because a compose file passes unset variables on as empty strings.
 *
 * Deliberately not a {@code toString} that prints the PINs: a record's default one would, and a PIN must never reach a
 * log line.
 */
public record StaffBootstrapSettings(
        String adminName, String adminPin, boolean adminReset, String kitchenName, String kitchenPin) {

    public static StaffBootstrapSettings of(
            String adminName, String adminPin, boolean adminReset, String kitchenName, String kitchenPin) {
        return new StaffBootstrapSettings(blankToNull(adminName), blankToNull(adminPin), adminReset,
                blankToNull(kitchenName), blankToNull(kitchenPin));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** True when the operator gave nothing at all. */
    public boolean isEmpty() {
        return adminName == null && adminPin == null && !adminReset && kitchenName == null && kitchenPin == null;
    }

    @Override
    public String toString() {
        return "StaffBootstrapSettings[adminName=" + adminName + ", adminReset=" + adminReset
                + ", kitchenName=" + kitchenName + ", PINs hidden]";
    }

}
