package org.restaurantordersmanagement.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A signed-in session must follow the account it belongs to: when an admin
 * deletes the account, changes its role or resets its PIN, the open session
 * changes with it on the very next request, not whenever it would expire. The
 * changes are made straight in the database, which is what any other path
 * (another admin, another backend call) amounts to.
 *
 * Not @Transactional - see CategoryControllerTest's javadoc for why.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SessionRevocationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<Long> createdIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        createdIds.forEach(id -> staffAccountRepository.findById(id).ifPresent(staffAccountRepository::delete));
    }

    private StaffAccount account(String name, Role role, String pin) {
        StaffAccount account = new StaffAccount();
        account.setName(name);
        account.setRole(role);
        account.setPinHash(passwordEncoder.encode(pin));
        StaffAccount saved = staffAccountRepository.saveAndFlush(account);
        createdIds.add(saved.getId());
        return saved;
    }

    private MockHttpSession signIn(String name, String pin) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"pin\": \"" + pin + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void anUnchangedAccountKeepsItsSession() throws Exception {
        account("Steady Cook", Role.KITCHEN, "1234");
        MockHttpSession session = signIn("Steady Cook", "1234");

        mockMvc.perform(get("/api/kitchen/orders").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/kitchen/orders").session(session)).andExpect(status().isOk());
    }

    @Test
    void deletingAnAccountEndsItsOpenSession() throws Exception {
        StaffAccount cook = account("Fired Cook", Role.KITCHEN, "1234");
        MockHttpSession session = signIn("Fired Cook", "1234");
        mockMvc.perform(get("/api/kitchen/orders").session(session)).andExpect(status().isOk());

        staffAccountRepository.deleteById(cook.getId());

        mockMvc.perform(get("/api/kitchen/orders").session(session)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/staff/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void demotingAnAccountTakesAwayTheOldRoleAtOnce() throws Exception {
        StaffAccount boss = account("Demoted Boss", Role.ADMIN, "1234");
        MockHttpSession session = signIn("Demoted Boss", "1234");
        mockMvc.perform(get("/api/staff/accounts").session(session)).andExpect(status().isOk());

        boss.setRole(Role.KITCHEN);
        staffAccountRepository.saveAndFlush(boss);

        mockMvc.perform(get("/api/staff/accounts").session(session)).andExpect(status().isForbidden());
        // ...and gives the new role's access right away.
        mockMvc.perform(get("/api/kitchen/orders").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/staff/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("KITCHEN"));
    }

    @Test
    void resettingAPinEndsTheSessionsSignedInWithTheOldOne() throws Exception {
        StaffAccount cook = account("Reset Cook", Role.KITCHEN, "1234");
        MockHttpSession session = signIn("Reset Cook", "1234");
        mockMvc.perform(get("/api/kitchen/orders").session(session)).andExpect(status().isOk());

        cook.setPinHash(passwordEncoder.encode("5678"));
        staffAccountRepository.saveAndFlush(cook);

        mockMvc.perform(get("/api/kitchen/orders").session(session)).andExpect(status().isUnauthorized());
        signIn("Reset Cook", "5678");
    }

    @Test
    void aRenamedAccountShowsItsNewNameInTheOpenSession() throws Exception {
        StaffAccount cook = account("Old Name", Role.KITCHEN, "1234");
        MockHttpSession session = signIn("Old Name", "1234");

        cook.setName("New Name");
        staffAccountRepository.saveAndFlush(cook);

        mockMvc.perform(get("/api/staff/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

}
