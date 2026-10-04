package org.restaurantordersmanagement.backend.staff.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.staff.security.LoginThrottle;
import org.restaurantordersmanagement.backend.staff.security.StaffPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/staff")
public class StaffAuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final LoginThrottle loginThrottle;

    public StaffAuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            StaffAccountRepository staffAccountRepository,
            LoginThrottle loginThrottle) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.loginThrottle = loginThrottle;
    }

    @PostMapping("/login")
    public ResponseEntity<StaffResponse> login(
            @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Duration locked = loginThrottle.lockedFor(request.name());
        if (!locked.isZero()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(locked.toSeconds() + 1))
                    .build();
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.name(), request.pin()));
        } catch (AuthenticationException e) {
            loginThrottle.recordFailure(request.name());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        loginThrottle.recordSuccess(request.name());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        StaffAccount staffAccount = ((StaffPrincipal) authentication.getPrincipal()).getStaffAccount();
        staffAccount.setLastSeenAt(Instant.now());
        staffAccountRepository.save(staffAccount);

        return ResponseEntity.ok(StaffResponse.from(staffAccount));
    }

    @GetMapping("/me")
    public StaffResponse me(@AuthenticationPrincipal StaffPrincipal principal) {
        return StaffResponse.from(principal.getStaffAccount());
    }

}
