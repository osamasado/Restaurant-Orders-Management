package org.restaurantordersmanagement.backend.staff.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.staff.service.StaffAccountService;
import org.restaurantordersmanagement.backend.staff.web.StaffAccountCreateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The first-administrator bootstrap, against the real database. Rolled back after each test, because the test
 * database is shared and other tests may already have staff accounts in it: the cases that need an EMPTY installation
 * get a repository whose only difference is that {@code count()} says 0, everything else (creating, hashing, signing
 * in) is real. Names are unique per test for the same reason.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class StaffBootstrapServiceTest {

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Autowired
    private StaffAccountService staffAccountService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    private StaffBootstrapService onEmptyInstallation() {
        StaffAccountRepository empty = mock(StaffAccountRepository.class, AdditionalAnswers.delegatesTo(staffAccountRepository));
        doReturn(0L).when(empty).count();
        return new StaffBootstrapService(empty, staffAccountService);
    }

    private StaffBootstrapService onExistingInstallation() {
        return new StaffBootstrapService(staffAccountRepository, staffAccountService);
    }

    private static String unique(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private boolean canSignIn(String name, String pin) {
        try {
            return authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(name, pin)).isAuthenticated();
        } catch (BadCredentialsException e) {
            return false;
        }
    }

    private StaffAccount existingAccount(String name, Role role, String pin) {
        return staffAccountService.create(new StaffAccountCreateRequest(name, role, pin));
    }

    // ---- creating the first accounts

    @Test
    void createsTheAdministratorOnAnEmptyInstallationAndTheyCanSignIn() {
        String name = unique("Boot Admin");

        onEmptyInstallation().bootstrap(StaffBootstrapSettings.of(name, "4711", false, null, null));

        StaffAccount admin = staffAccountRepository.findByName(name).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getPinHash()).isNotEqualTo("4711");
        assertThat(passwordEncoder.matches("4711", admin.getPinHash())).isTrue();
        assertThat(canSignIn(name, "4711")).isTrue();
        assertThat(canSignIn(name, "0000")).isFalse();
    }

    @Test
    void createsTheKitchenAccountToo() {
        String admin = unique("Boot Admin");
        String kitchen = unique("Boot Kitchen");

        onEmptyInstallation().bootstrap(StaffBootstrapSettings.of(admin, "4711", false, kitchen, "2468"));

        assertThat(staffAccountRepository.findByName(kitchen).orElseThrow().getRole()).isEqualTo(Role.KITCHEN);
        assertThat(canSignIn(kitchen, "2468")).isTrue();
        assertThat(canSignIn(admin, "4711")).isTrue();
    }

    @Test
    void createsNothingWhenNoSettingsAreGiven() {
        long before = staffAccountRepository.count();

        onEmptyInstallation().bootstrap(StaffBootstrapSettings.of(null, null, false, null, null));

        assertThat(staffAccountRepository.count()).isEqualTo(before);
    }

    // ---- never touching an installation that has accounts

    @Test
    void createsNothingAndChangesNothingWhenAccountsExist() {
        String existing = unique("Existing Admin");
        existingAccount(existing, Role.ADMIN, "1111");
        long before = staffAccountRepository.count();
        String newcomer = unique("Boot Admin");

        onExistingInstallation().bootstrap(StaffBootstrapSettings.of(newcomer, "4711", false, unique("Kitchen"), "2468"));
        onExistingInstallation().bootstrap(StaffBootstrapSettings.of(existing, "9999", false, null, null));

        assertThat(staffAccountRepository.count()).isEqualTo(before);
        assertThat(staffAccountRepository.findByName(newcomer)).isEmpty();
        assertThat(canSignIn(existing, "1111")).as("an existing PIN is never changed by the settings").isTrue();
        assertThat(canSignIn(existing, "9999")).isFalse();
    }

    // ---- wrong settings stop the start, and never show a PIN

    @Test
    void anInvalidPinStopsTheStartWithAMessageThatNamesTheSettingButNotThePin() {
        for (String pin : new String[] {"123", "123456789", "12ab", "12 34"}) {
            assertThatThrownBy(() -> onEmptyInstallation()
                    .bootstrap(StaffBootstrapSettings.of(unique("Boot Admin"), pin, false, null, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BOOTSTRAP_ADMIN_PIN")
                    .hasMessageNotContaining(pin);
        }
    }

    @Test
    void anInvalidKitchenPinNamesTheKitchenSetting() {
        assertThatThrownBy(() -> onEmptyInstallation()
                .bootstrap(StaffBootstrapSettings.of(unique("Boot Admin"), "4711", false, unique("Kitchen"), "12")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_KITCHEN_PIN")
                .hasMessageNotContaining("4711");
    }

    @Test
    void aNameWithoutAPinOrAPinWithoutANameStopsTheStart() {
        assertThatThrownBy(() -> onEmptyInstallation().bootstrap(StaffBootstrapSettings.of("Someone", null, false, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_NAME")
                .hasMessageContaining("together");
        assertThatThrownBy(() -> onEmptyInstallation().bootstrap(StaffBootstrapSettings.of("  ", "4711", false, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("together")
                .hasMessageNotContaining("4711");
    }

    @Test
    void theKitchenAccountNeedsTheAdministratorAndAnotherName() {
        assertThatThrownBy(() -> onEmptyInstallation().bootstrap(StaffBootstrapSettings.of(null, null, false, "Kitchen", "2468")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_NAME");
        assertThatThrownBy(() -> onEmptyInstallation().bootstrap(StaffBootstrapSettings.of("Same", "4711", false, "same", "2468")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must differ");
    }

    @Test
    void invalidSettingsStopTheStartEvenWhenAccountsExist() {
        assertThatThrownBy(() -> onExistingInstallation().bootstrap(StaffBootstrapSettings.of("Someone", "12", false, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_PIN");
    }

    @Test
    void nothingIsCreatedWhenTheKitchenPartFails() {
        long before = staffAccountRepository.count();

        assertThatThrownBy(() -> onEmptyInstallation()
                .bootstrap(StaffBootstrapSettings.of(unique("Boot Admin"), "4711", false, unique("Kitchen"), "bad")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(staffAccountRepository.count()).as("validated before anything is created").isEqualTo(before);
    }

    // ---- the deliberate reset

    @Test
    void theResetSwitchSetsANewPinForTheNamedAdministrator() {
        String name = unique("Forgetful Admin");
        existingAccount(name, Role.ADMIN, "1111");
        long before = staffAccountRepository.count();

        onExistingInstallation().bootstrap(StaffBootstrapSettings.of(name, "2222", true, null, null));

        assertThat(canSignIn(name, "2222")).isTrue();
        assertThat(canSignIn(name, "1111")).isFalse();
        assertThat(staffAccountRepository.count()).as("a reset never creates an account").isEqualTo(before);
    }

    @Test
    void theResetRefusesAnAccountThatDoesNotExistAndChangesNothing() {
        long before = staffAccountRepository.count();

        assertThatThrownBy(() -> onExistingInstallation()
                .bootstrap(StaffBootstrapSettings.of(unique("Nobody"), "2222", true, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_RESET")
                .hasMessageContaining("no staff account")
                .hasMessageNotContaining("2222");

        assertThat(staffAccountRepository.count()).isEqualTo(before);
    }

    @Test
    void theResetRefusesAnAccountThatIsNotAnAdministrator() {
        String name = unique("Kitchen Cook");
        existingAccount(name, Role.KITCHEN, "1111");

        assertThatThrownBy(() -> onExistingInstallation().bootstrap(StaffBootstrapSettings.of(name, "2222", true, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an administrator")
                .hasMessageNotContaining("2222");

        assertThat(canSignIn(name, "1111")).as("nobody is promoted or changed through the reset").isTrue();
        assertThat(staffAccountRepository.findByName(name).orElseThrow().getRole()).isEqualTo(Role.KITCHEN);
    }

    @Test
    void theResetNeedsTheNameAndThePin() {
        assertThatThrownBy(() -> onExistingInstallation().bootstrap(StaffBootstrapSettings.of(null, null, true, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_RESET");
    }

    // ---- the PIN never leaks

    @Test
    void theSettingsNeverPrintTheirPins() {
        String text = StaffBootstrapSettings.of("Admin", "4711", false, "Cook", "2468").toString();

        assertThat(text).contains("Admin").contains("Cook").doesNotContain("4711").doesNotContain("2468");
    }

    // ---- the one PIN rule, in the service

    @Test
    void theServiceEnforcesThePinRuleForEveryCaller() {
        assertThat(StaffAccountService.isValidPin("1234")).isTrue();
        assertThat(StaffAccountService.isValidPin("12345678")).isTrue();
        for (String bad : new String[] {null, "", "123", "123456789", "12a4", " 1234"}) {
            assertThat(StaffAccountService.isValidPin(bad)).as("'%s'", bad).isFalse();
        }
        assertThatThrownBy(() -> staffAccountService.create(new StaffAccountCreateRequest(unique("X"), Role.KITCHEN, "12")))
                .isInstanceOf(ResponseStatusException.class);
        StaffAccount account = existingAccount(unique("Y"), Role.KITCHEN, "1234");
        assertThatThrownBy(() -> staffAccountService.resetPin(account.getId(), "abcd"))
                .isInstanceOf(ResponseStatusException.class);
    }

}
