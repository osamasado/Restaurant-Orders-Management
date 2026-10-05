package org.restaurantordersmanagement.backend.staff.web;

import java.util.List;
import java.util.regex.Pattern;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.security.StaffPrincipal;
import org.restaurantordersmanagement.backend.staff.service.StaffAccountService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/staff/accounts")
public class StaffAccountController {

    private static final Pattern PIN_FORMAT = Pattern.compile("^[0-9]{4,8}$");

    private final StaffAccountService staffAccountService;

    public StaffAccountController(StaffAccountService staffAccountService) {
        this.staffAccountService = staffAccountService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<StaffResponse> list() {
        return staffAccountService.findAll().stream().map(StaffResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public StaffResponse create(@RequestBody StaffAccountCreateRequest request) {
        validateCreate(request);
        try {
            return StaffResponse.from(staffAccountService.create(request));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A staff account with this name already exists");
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public StaffResponse update(
            @PathVariable Long id,
            @RequestBody StaffAccountUpdateRequest request,
            @AuthenticationPrincipal StaffPrincipal principal) {
        validateUpdate(request);
        try {
            return StaffResponse.from(staffAccountService.update(id, request, principal.getStaffAccount().getId()));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A staff account with this name already exists");
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal StaffPrincipal principal) {
        if (principal.getStaffAccount().getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete your own account");
        }
        try {
            staffAccountService.delete(id);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Staff account is referenced elsewhere");
        }
    }

    @PostMapping("/{id}/reset-pin")
    @PreAuthorize("hasRole('ADMIN')")
    public StaffResponse resetPin(@PathVariable Long id, @RequestBody ResetPinRequest request) {
        validatePin(request.pin());
        return StaffResponse.from(staffAccountService.resetPin(id, request.pin()));
    }

    private void validateCreate(StaffAccountCreateRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        validateRole(request.role());
        validatePin(request.pin());
    }

    /** 4 to 8 digits: short enough to type on a tablet, long enough that the sign-in throttle means something. */
    private void validatePin(String pin) {
        if (pin == null || !PIN_FORMAT.matcher(pin).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pin must be 4 to 8 digits");
        }
    }

    private void validateUpdate(StaffAccountUpdateRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        validateRole(request.role());
    }

    /** Role.USER is reserved for a future guest-login feature, not assignable to a staff account here. */
    private void validateRole(Role role) {
        if (role == null || role == Role.USER) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "role must be one of ADMIN, KITCHEN, WAITER, CASHIER");
        }
    }

}
