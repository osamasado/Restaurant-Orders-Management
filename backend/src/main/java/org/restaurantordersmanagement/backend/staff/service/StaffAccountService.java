package org.restaurantordersmanagement.backend.staff.service;

import java.util.List;
import java.util.regex.Pattern;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.staff.web.StaffAccountCreateRequest;
import org.restaurantordersmanagement.backend.staff.web.StaffAccountUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Keeps at least one administrator: the last one cannot be demoted or deleted,
 * and nobody can change their own role. Only update and delete are
 * @Transactional, so the admin rows they lock stay locked until the change is saved.
 */
@Service
public class StaffAccountService {

    private static final Pattern PIN_FORMAT = Pattern.compile("^[0-9]{4,8}$");

    private final StaffAccountRepository staffAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public StaffAccountService(StaffAccountRepository staffAccountRepository, PasswordEncoder passwordEncoder) {
        this.staffAccountRepository = staffAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<StaffAccount> findAll() {
        return staffAccountRepository.findAll();
    }

    public StaffAccount findById(Long id) {
        return staffAccountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff account not found"));
    }

    /**
     * 4 to 8 digits: short enough to type on a tablet, long enough that the sign-in throttle means something. The one
     * place this rule lives: the admin screen's API and the start-up bootstrap both go through it.
     */
    public static boolean isValidPin(String pin) {
        return pin != null && PIN_FORMAT.matcher(pin).matches();
    }

    /** @throws ResponseStatusException 400 when the PIN does not follow the rule above */
    public static void requireValidPin(String pin) {
        if (!isValidPin(pin)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pin must be 4 to 8 digits");
        }
    }

    public StaffAccount create(StaffAccountCreateRequest request) {
        requireValidPin(request.pin());
        StaffAccount staffAccount = new StaffAccount();
        staffAccount.setName(request.name());
        staffAccount.setRole(request.role());
        staffAccount.setPinHash(passwordEncoder.encode(request.pin()));
        return staffAccountRepository.saveAndFlush(staffAccount);
    }

    /** @param actorId the signed-in admin making the change, who may not change their own role */
    @Transactional
    public StaffAccount update(Long id, StaffAccountUpdateRequest request, Long actorId) {
        List<StaffAccount> admins = staffAccountRepository.findByRole(Role.ADMIN);
        StaffAccount staffAccount = findById(id);
        if (staffAccount.getRole() != request.role()) {
            if (id.equals(actorId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot change your own role");
            }
            if (staffAccount.getRole() == Role.ADMIN) {
                requireAnotherAdmin(admins, id, "demoted");
            }
        }
        staffAccount.setName(request.name());
        staffAccount.setRole(request.role());
        return staffAccountRepository.saveAndFlush(staffAccount);
    }

    /** Flushes so an account still referenced elsewhere (e.g. order audit history) throws here, caught by the controller as 409. */
    @Transactional
    public void delete(Long id) {
        List<StaffAccount> admins = staffAccountRepository.findByRole(Role.ADMIN);
        if (admins.stream().anyMatch(admin -> admin.getId().equals(id))) {
            requireAnotherAdmin(admins, id, "deleted");
        }
        staffAccountRepository.deleteById(id);
        staffAccountRepository.flush();
    }

    private static void requireAnotherAdmin(List<StaffAccount> admins, Long id, String what) {
        boolean anotherAdmin = admins.stream().anyMatch(admin -> !admin.getId().equals(id));
        if (!anotherAdmin) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The last administrator cannot be " + what);
        }
    }

    public StaffAccount resetPin(Long id, String newPin) {
        requireValidPin(newPin);
        StaffAccount staffAccount = findById(id);
        staffAccount.setPinHash(passwordEncoder.encode(newPin));
        return staffAccountRepository.saveAndFlush(staffAccount);
    }

}
