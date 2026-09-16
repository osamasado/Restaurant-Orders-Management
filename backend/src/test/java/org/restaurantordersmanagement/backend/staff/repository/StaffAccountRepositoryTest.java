package org.restaurantordersmanagement.backend.staff.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class StaffAccountRepositoryTest {

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Test
    void savesAccountWithoutPinYet() {
        StaffAccount account = new StaffAccount();
        account.setName("O. Sado");
        account.setRole(Role.ADMIN);

        StaffAccount saved = staffAccountRepository.saveAndFlush(account);

        Optional<StaffAccount> found = staffAccountRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("O. Sado", found.get().getName());
        assertEquals(Role.ADMIN, found.get().getRole());
        assertNull(found.get().getPinHash());
        assertNull(found.get().getLastSeenAt());
    }

    @Test
    void savesAccountWithPinAndLastSeen() {
        StaffAccount account = new StaffAccount();
        account.setName("Kitchen Staff");
        account.setRole(Role.KITCHEN);
        account.setPinHash("hashed-pin");
        Instant lastSeenAt = Instant.parse("2026-09-16T12:00:00Z");
        account.setLastSeenAt(lastSeenAt);

        StaffAccount saved = staffAccountRepository.saveAndFlush(account);

        Optional<StaffAccount> found = staffAccountRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(Role.KITCHEN, found.get().getRole());
        assertEquals("hashed-pin", found.get().getPinHash());
        assertEquals(lastSeenAt, found.get().getLastSeenAt());
    }

}
