package org.restaurantordersmanagement.backend.staff.service;

import java.util.List;
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

    public StaffAccount create(StaffAccountCreateRequest request) {
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
        StaffAccount staffAccount = findById(id);
        staffAccount.setPinHash(passwordEncoder.encode(newPin));
        return staffAccountRepository.saveAndFlush(staffAccount);
    }

}
