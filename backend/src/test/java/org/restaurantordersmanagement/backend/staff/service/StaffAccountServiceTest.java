package org.restaurantordersmanagement.backend.staff.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.staff.web.StaffAccountUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/**
 * The last-administrator rule on its own. Through the API it is only reachable
 * when exactly one admin exists, which depends on what else is in the database,
 * so it is tested here against a stubbed repository.
 */
class StaffAccountServiceTest {

    private static final Long ACTOR_ID = 99L;

    private StaffAccountRepository repository;
    private StaffAccountService service;

    @BeforeEach
    void setUp() {
        repository = mock(StaffAccountRepository.class);
        service = new StaffAccountService(repository, mock(PasswordEncoder.class));
        when(repository.saveAndFlush(any(StaffAccount.class))).thenAnswer(call -> call.getArgument(0));
    }

    private StaffAccount account(long id, Role role) {
        StaffAccount account = new StaffAccount();
        account.setId(id);
        account.setName("Account " + id);
        account.setRole(role);
        when(repository.findById(id)).thenReturn(Optional.of(account));
        return account;
    }

    private static StaffAccountUpdateRequest updateTo(Role role) {
        return new StaffAccountUpdateRequest("Renamed", role);
    }

    @Test
    void theLastAdministratorCannotBeDemoted() {
        StaffAccount onlyAdmin = account(1, Role.ADMIN);
        when(repository.findByRole(Role.ADMIN)).thenReturn(List.of(onlyAdmin));

        ResponseStatusException error =
                assertThrows(ResponseStatusException.class, () -> service.update(1L, updateTo(Role.KITCHEN), ACTOR_ID));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(Role.ADMIN, onlyAdmin.getRole());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void theLastAdministratorCannotBeDeleted() {
        StaffAccount onlyAdmin = account(1, Role.ADMIN);
        when(repository.findByRole(Role.ADMIN)).thenReturn(List.of(onlyAdmin));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.delete(1L));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void oneOfSeveralAdministratorsCanBeDemotedOrDeleted() {
        StaffAccount first = account(1, Role.ADMIN);
        StaffAccount second = account(2, Role.ADMIN);
        when(repository.findByRole(Role.ADMIN)).thenReturn(List.of(first, second));

        service.update(2L, updateTo(Role.WAITER), ACTOR_ID);
        assertEquals(Role.WAITER, second.getRole());

        service.delete(1L);
        verify(repository).deleteById(1L);
    }

    @Test
    void changingAnotherAccountsRoleNeedsNoSpareAdmin() {
        StaffAccount cook = account(3, Role.KITCHEN);
        StaffAccount admin = account(1, Role.ADMIN);
        when(repository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));

        service.update(3L, updateTo(Role.WAITER), ACTOR_ID);

        assertEquals(Role.WAITER, cook.getRole());
    }

    @Test
    void nobodyCanChangeTheirOwnRole() {
        StaffAccount self = account(ACTOR_ID, Role.ADMIN);
        StaffAccount other = account(2, Role.ADMIN);
        when(repository.findByRole(Role.ADMIN)).thenReturn(List.of(self, other));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class, () -> service.update(ACTOR_ID, updateTo(Role.KITCHEN), ACTOR_ID));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

}
