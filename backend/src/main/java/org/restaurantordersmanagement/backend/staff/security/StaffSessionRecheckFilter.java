package org.restaurantordersmanagement.backend.staff.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Makes an open session follow the account it belongs to. The session holds a
 * copy of the account taken at sign-in; without this, deleting the account,
 * demoting it or resetting its PIN would change nothing until the session
 * happened to expire - and a busy kitchen screen polling every few seconds
 * never lets it expire.
 *
 * On every request from a signed-in staff member the account is read again:
 * - gone, or its PIN was reset: the session is ended and the request is anonymous;
 * - role or name changed: the session carries on with the fresh values, so a
 *   demotion takes the old access away on the very next request.
 *
 * Costs one small lookup by primary key per signed-in request, fine at the size
 * of a restaurant. Not a Spring bean on purpose: it is added to the security
 * filter chain, and a bean would also be registered as a second servlet filter.
 */
public class StaffSessionRecheckFilter extends OncePerRequestFilter {

    private final StaffAccountRepository staffAccountRepository;
    private final SecurityContextRepository securityContextRepository;

    public StaffSessionRecheckFilter(
            StaffAccountRepository staffAccountRepository, SecurityContextRepository securityContextRepository) {
        this.staffAccountRepository = staffAccountRepository;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof StaffPrincipal principal
                && principal.getStaffAccount().getId() != null) {
            recheck(request, response, principal.getStaffAccount());
        }
        filterChain.doFilter(request, response);
    }

    private void recheck(HttpServletRequest request, HttpServletResponse response, StaffAccount signedInAs) {
        Optional<StaffAccount> current = staffAccountRepository.findById(signedInAs.getId());

        if (current.isEmpty() || !Objects.equals(current.get().getPinHash(), signedInAs.getPinHash())) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            return;
        }

        StaffAccount now = current.get();
        if (now.getRole() != signedInAs.getRole() || !Objects.equals(now.getName(), signedInAs.getName())) {
            StaffPrincipal refreshed = new StaffPrincipal(now);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(refreshed, null, refreshed.getAuthorities()));
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
        }
    }

}
