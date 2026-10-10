package org.restaurantordersmanagement.backend.staff.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.staff.service.StaffAccountService;
import org.restaurantordersmanagement.backend.staff.web.StaffAccountCreateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first staff accounts of a fresh installation from deployment settings, so it can be used without the
 * demo seed (whose accounts all have the public PIN 1234).
 *
 * <ul>
 *   <li>No staff account exists and an administrator is given: that administrator is created, and the kitchen account
 *       too if one is given, together or not at all.</li>
 *   <li>Accounts exist: nothing is created and nothing is changed, whatever the settings say. A restart never adds an
 *       account.</li>
 *   <li>{@code BOOTSTRAP_ADMIN_RESET=true}: the one deliberate exception. It sets a new PIN for the named
 *       administrator, for recovery when the last administrator forgot theirs. It refuses a missing account, an
 *       account that is not an administrator, and never creates or promotes anyone.</li>
 *   <li>A setting that is given but wrong stops the application with a message that names the setting. No message and
 *       no log line ever contains a PIN.</li>
 * </ul>
 */
@Slf4j
@Service
public class StaffBootstrapService {

    private final StaffAccountRepository staffAccountRepository;
    private final StaffAccountService staffAccountService;

    public StaffBootstrapService(StaffAccountRepository staffAccountRepository, StaffAccountService staffAccountService) {
        this.staffAccountRepository = staffAccountRepository;
        this.staffAccountService = staffAccountService;
    }

    /** @throws IllegalStateException with a message that names the setting at fault (and never shows a PIN) */
    @Transactional
    public void bootstrap(StaffBootstrapSettings settings) {
        validate(settings);

        if (settings.adminReset()) {
            resetAdminPin(settings);
            return;
        }

        if (staffAccountRepository.count() > 0) {
            if (!settings.isEmpty()) {
                log.info("The BOOTSTRAP_* settings are ignored: staff accounts exist already. Remove them from the environment.");
            }
            return;
        }

        if (settings.adminName() == null) {
            log.warn("No staff account exists, so nobody can sign in. Set BOOTSTRAP_ADMIN_NAME and BOOTSTRAP_ADMIN_PIN "
                    + "(and optionally BOOTSTRAP_KITCHEN_NAME and BOOTSTRAP_KITCHEN_PIN) and restart, "
                    + "or set APP_SEED_DEMO=true for demo data.");
            return;
        }

        create(settings.adminName(), settings.adminPin(), Role.ADMIN);
        log.info("Created the administrator '{}' from BOOTSTRAP_ADMIN_NAME. Remove BOOTSTRAP_ADMIN_PIN from the environment now.",
                settings.adminName());
        if (settings.kitchenName() != null) {
            create(settings.kitchenName(), settings.kitchenPin(), Role.KITCHEN);
            log.info("Created the kitchen account '{}' from BOOTSTRAP_KITCHEN_NAME. Remove BOOTSTRAP_KITCHEN_PIN from the environment now.",
                    settings.kitchenName());
        }
    }

    private void resetAdminPin(StaffBootstrapSettings settings) {
        StaffAccount account = staffAccountRepository.findByName(settings.adminName()).orElseThrow(() ->
                new IllegalStateException("BOOTSTRAP_ADMIN_RESET is on, but there is no staff account named '"
                        + settings.adminName() + "' (BOOTSTRAP_ADMIN_NAME). Nothing was changed."));
        if (account.getRole() != Role.ADMIN) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_RESET is on, but '" + settings.adminName()
                    + "' is not an administrator. It only resets an administrator's PIN. Nothing was changed.");
        }
        staffAccountService.resetPin(account.getId(), settings.adminPin());
        log.warn("BOOTSTRAP_ADMIN_RESET is on: the PIN of the administrator '{}' was reset. Remove BOOTSTRAP_ADMIN_RESET "
                + "and BOOTSTRAP_ADMIN_PIN from the environment now, or every restart resets it again.", settings.adminName());
    }

    private void create(String name, String pin, Role role) {
        staffAccountService.create(new StaffAccountCreateRequest(name, role, pin));
    }

    private static void validate(StaffBootstrapSettings settings) {
        if (settings.isEmpty()) {
            return;
        }
        boolean admin = pair("BOOTSTRAP_ADMIN_NAME", settings.adminName(), "BOOTSTRAP_ADMIN_PIN", settings.adminPin());
        boolean kitchen = pair("BOOTSTRAP_KITCHEN_NAME", settings.kitchenName(), "BOOTSTRAP_KITCHEN_PIN", settings.kitchenPin());
        if (admin) {
            requirePin("BOOTSTRAP_ADMIN_PIN", settings.adminPin());
        }
        if (kitchen) {
            requirePin("BOOTSTRAP_KITCHEN_PIN", settings.kitchenPin());
            if (!admin) {
                throw new IllegalStateException(
                        "BOOTSTRAP_KITCHEN_NAME and BOOTSTRAP_KITCHEN_PIN need BOOTSTRAP_ADMIN_NAME and BOOTSTRAP_ADMIN_PIN too.");
            }
            if (settings.kitchenName().equalsIgnoreCase(settings.adminName())) {
                throw new IllegalStateException("BOOTSTRAP_KITCHEN_NAME must differ from BOOTSTRAP_ADMIN_NAME.");
            }
        }
        if (settings.adminReset() && !admin) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_RESET needs BOOTSTRAP_ADMIN_NAME and BOOTSTRAP_ADMIN_PIN.");
        }
    }

    /** Both or neither; true when both are given. */
    private static boolean pair(String nameSetting, String name, String pinSetting, String pin) {
        if ((name == null) != (pin == null)) {
            throw new IllegalStateException(nameSetting + " and " + pinSetting + " must be set together.");
        }
        return name != null;
    }

    private static void requirePin(String setting, String pin) {
        if (!StaffAccountService.isValidPin(pin)) {
            throw new IllegalStateException(setting + " must be 4 to 8 digits.");
        }
    }

}
