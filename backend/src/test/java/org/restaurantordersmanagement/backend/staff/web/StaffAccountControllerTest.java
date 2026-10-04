package org.restaurantordersmanagement.backend.staff.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

/** Not @Transactional - see CategoryControllerTest's javadoc for why. tearDown() deletes tracked ids instead. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StaffAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<Long> createdStaffAccountIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long id : createdStaffAccountIds) {
            staffAccountRepository.findById(id).ifPresent(staffAccountRepository::delete);
        }
    }

    private StaffAccount createStaffAccount(String name, Role role, String pin) {
        StaffAccount staffAccount = new StaffAccount();
        staffAccount.setName(name);
        staffAccount.setRole(role);
        staffAccount.setPinHash(passwordEncoder.encode(pin));
        StaffAccount saved = staffAccountRepository.saveAndFlush(staffAccount);
        createdStaffAccountIds.add(saved.getId());
        return saved;
    }

    @Test
    void createRejectsNonAdminRole() throws Exception {
        mockMvc.perform(post("/api/staff/accounts")
                        .with(user("waiter").roles("WAITER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "New Kitchen", "role": "KITCHEN", "pin": "1234"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRejectsUserRole() throws Exception {
        mockMvc.perform(post("/api/staff/accounts")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Someone", "role": "USER", "pin": "1234"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsDuplicateName() throws Exception {
        createStaffAccount("Dup Name", Role.WAITER, "1234");

        mockMvc.perform(post("/api/staff/accounts")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Dup Name", "role": "CASHIER", "pin": "5678"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void createListUpdateAndDeleteRoundTrip() throws Exception {
        // The delete step's self-delete guard needs a real StaffPrincipal, which the
        // with(user(...)) shortcut below doesn't produce - log in for real instead.
        createStaffAccount("Round Trip Admin", Role.ADMIN, "1234");
        MvcResult loginResult = mockMvc.perform(post("/api/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Round Trip Admin", "pin": "1234"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession adminSession = (MockHttpSession) loginResult.getRequest().getSession(false);

        MvcResult createResult = mockMvc.perform(post("/api/staff/accounts")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "New Waiter", "role": "WAITER", "pin": "4321"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Waiter"))
                .andExpect(jsonPath("$.role").value("WAITER"))
                .andReturn();

        String body = createResult.getResponse().getContentAsString();
        assertFalse(body.contains("pinHash"));
        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        createdStaffAccountIds.add(id);

        mockMvc.perform(put("/api/staff/accounts/" + id)
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "New Waiter (renamed)", "role": "CASHIER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Waiter (renamed)"))
                .andExpect(jsonPath("$.role").value("CASHIER"));

        mockMvc.perform(delete("/api/staff/accounts/" + id).session(adminSession))
                .andExpect(status().isNoContent());

        assertTrue(staffAccountRepository.findById(id).isEmpty());
    }

    @Test
    void resetPinAllowsTheNewPinToLogIn() throws Exception {
        StaffAccount staffAccount = createStaffAccount("Reset Pin Target", Role.KITCHEN, "0000");

        mockMvc.perform(post("/api/staff/accounts/" + staffAccount.getId() + "/reset-pin")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pin": "9999"}
                                """))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name": "Reset Pin Target", "pin": "9999"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(
                        get("/api/staff/me").session((MockHttpSession) loginResult.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Reset Pin Target"));
    }

    /** A real sign-in, because changing your own role needs a real StaffPrincipal. */
    private MockHttpSession signIn(String name, String pin) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"pin\": \"" + pin + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private org.springframework.test.web.servlet.ResultActions createWithPin(String name, String pin) throws Exception {
        return mockMvc.perform(post("/api/staff/accounts")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\", \"role\": \"KITCHEN\", \"pin\": \"" + pin + "\"}"));
    }

    @Test
    void createRejectsPinsThatAreNotFourToEightDigits() throws Exception {
        for (String pin : List.of("", "123", "123456789", "12a4", " 1234", "1234 ", "\u0661\u0662\u0663\u0664")) {
            createWithPin("Bad Pin " + pin.length() + pin.hashCode(), pin).andExpect(status().isBadRequest());
        }
    }

    @Test
    void createAcceptsFourAndEightDigitPins() throws Exception {
        for (String pin : List.of("1234", "12345678")) {
            MvcResult result = createWithPin("Good Pin " + pin, pin).andExpect(status().isOk()).andReturn();
            createdStaffAccountIds.add(((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue());
        }
    }

    @Test
    void resetPinRejectsAnInvalidPinAndKeepsTheOldOne() throws Exception {
        StaffAccount target = createStaffAccount("Pin Keeper", Role.KITCHEN, "4321");

        for (String pin : List.of("12", "abcd", "123456789")) {
            mockMvc.perform(post("/api/staff/accounts/" + target.getId() + "/reset-pin")
                            .with(user("admin").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pin\": \"" + pin + "\"}"))
                    .andExpect(status().isBadRequest());
        }

        signIn("Pin Keeper", "4321");
    }

    @Test
    void anAdminCannotChangeTheirOwnRole() throws Exception {
        StaffAccount admin = createStaffAccount("Self Demote Admin", Role.ADMIN, "1234");
        MockHttpSession session = signIn("Self Demote Admin", "1234");

        mockMvc.perform(put("/api/staff/accounts/" + admin.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Self Demote Admin\", \"role\": \"KITCHEN\"}"))
                .andExpect(status().isBadRequest());

        assertEquals(Role.ADMIN, staffAccountRepository.findById(admin.getId()).orElseThrow().getRole());

        // Renaming yourself, keeping the role, is fine.
        mockMvc.perform(put("/api/staff/accounts/" + admin.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Self Demote Admin\", \"role\": \"ADMIN\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void anAdminCanDemoteOrDeleteAnotherAdminWhileTheyRemain() throws Exception {
        createStaffAccount("Demoting Admin", Role.ADMIN, "1234");
        StaffAccount other = createStaffAccount("Demoted Admin", Role.ADMIN, "1234");
        StaffAccount toDelete = createStaffAccount("Deleted Admin", Role.ADMIN, "1234");
        MockHttpSession session = signIn("Demoting Admin", "1234");

        mockMvc.perform(put("/api/staff/accounts/" + other.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Demoted Admin\", \"role\": \"KITCHEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("KITCHEN"));

        mockMvc.perform(delete("/api/staff/accounts/" + toDelete.getId()).session(session))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletingYourOwnAccountIsRejected() throws Exception {
        StaffAccount admin = createStaffAccount("Self Delete Admin", Role.ADMIN, "1234");

        MvcResult loginResult = mockMvc.perform(post("/api/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name": "Self Delete Admin", "pin": "1234"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(delete("/api/staff/accounts/" + admin.getId())
                        .session((MockHttpSession) loginResult.getRequest().getSession(false)))
                .andExpect(status().isBadRequest());

        assertTrue(staffAccountRepository.findById(admin.getId()).isPresent());
    }

}
