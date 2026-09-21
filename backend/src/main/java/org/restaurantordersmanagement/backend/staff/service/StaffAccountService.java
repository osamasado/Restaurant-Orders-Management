package org.restaurantordersmanagement.backend.staff.service;

import java.util.List;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.staff.web.StaffAccountCreateRequest;
import org.restaurantordersmanagement.backend.staff.web.StaffAccountUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** No @Transactional needed anywhere - StaffAccount has only scalar fields, same as RawMaterialService/TableService. */
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

    public StaffAccount update(Long id, StaffAccountUpdateRequest request) {
        StaffAccount staffAccount = findById(id);
        staffAccount.setName(request.name());
        staffAccount.setRole(request.role());
        return staffAccountRepository.saveAndFlush(staffAccount);
    }

    /** Flushes so an account still referenced elsewhere (e.g. order audit history) throws here, caught by the controller as 409. */
    public void delete(Long id) {
        staffAccountRepository.deleteById(id);
        staffAccountRepository.flush();
    }

    public StaffAccount resetPin(Long id, String newPin) {
        StaffAccount staffAccount = findById(id);
        staffAccount.setPinHash(passwordEncoder.encode(newPin));
        return staffAccountRepository.saveAndFlush(staffAccount);
    }

}
