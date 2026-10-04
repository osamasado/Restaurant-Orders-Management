package org.restaurantordersmanagement.backend.staff.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.staff.security.LoginThrottle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StaffAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginThrottle loginThrottle;

    /** The throttle lives in the shared Spring context, so one test's failed sign-ins must not lock the next test's names. */
    @BeforeEach
    void forgetEarlierFailedSignIns() {
        loginThrottle.reset();
    }

    private ResultActions signIn(String name, String pin) throws Exception {
        return mockMvc.perform(post("/api/staff/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\", \"pin\": \"" + pin + "\"}"));
    }

    private StaffAccount createStaffAccount(String name, Role role, String pin) {
        StaffAccount staffAccount = new StaffAccount();
        staffAccount.setName(name);
        staffAccount.setRole(role);
        staffAccount.setPinHash(passwordEncoder.encode(pin));
        return staffAccountRepository.saveAndFlush(staffAccount);
    }

    @Test
    void loginWithCorrectPinReturnsRoleAndEstablishesSession() throws Exception {
        createStaffAccount("Admin One", Role.ADMIN, "1234");

        mockMvc.perform(post("/api/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                                "name": "Admin One",
                                "pin": "1234"
                            }
                            """
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Admin One"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void loginWithWrongPinIsRejected() throws Exception {
        createStaffAccount("Kitchen One", Role.KITCHEN, "1234");

        mockMvc.perform(post("/api/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                    "name": "Kitchen One",
                                    "pin": "wrong"
                                }
                                """
                        ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/staff/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accountsRejectsNonAdminRole() throws Exception {
        mockMvc.perform(get("/api/staff/accounts").with(user("waiter").roles("WAITER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountsAllowsAdminRoleAndNeverExposesPinHash() throws Exception {
        createStaffAccount("Admin Two", Role.ADMIN, "5678");

        MvcResult result = mockMvc.perform(get("/api/staff/accounts").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("pinHash"));
        assertTrue(body.contains("Admin Two"));
    }

    @Test
    void loginPersistsSessionSoSubsequentMeRequestSucceeds() throws Exception {
        createStaffAccount("Cashier One", Role.CASHIER, "9999");

        MvcResult loginResult = mockMvc.perform(post("/api/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                    "name": "Cashier One",
                                    "pin": "9999"
                                }
                                """
                        ))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(get("/api/staff/me").session((MockHttpSession) loginResult.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cashier One"));
    }

    @Test
    void fiveWrongPinsLockTheNameEvenAgainstTheRightPin() throws Exception {
        createStaffAccount("Throttled Kitchen", Role.KITCHEN, "1234");

        for (int i = 0; i < 5; i++) {
            signIn("Throttled Kitchen", "0000").andExpect(status().isUnauthorized());
        }

        MvcResult locked = signIn("Throttled Kitchen", "1234")
                .andExpect(status().isTooManyRequests())
                .andReturn();
        String retryAfter = locked.getResponse().getHeader("Retry-After");
        assertNotNull(retryAfter);
        long seconds = Long.parseLong(retryAfter);
        assertTrue(seconds > 0 && seconds <= 15 * 60 + 1, "Retry-After was " + seconds);
    }

    @Test
    void anUnknownNameIsLockedExactlyLikeARealOne() throws Exception {
        for (int i = 0; i < 5; i++) {
            signIn("Nobody Here", "0000").andExpect(status().isUnauthorized());
        }

        signIn("Nobody Here", "0000").andExpect(status().isTooManyRequests());
    }

    @Test
    void aSuccessfulSignInStartsTheCountOver() throws Exception {
        createStaffAccount("Forgetful Cashier", Role.CASHIER, "1234");

        for (int i = 0; i < 4; i++) {
            signIn("Forgetful Cashier", "0000").andExpect(status().isUnauthorized());
        }
        signIn("Forgetful Cashier", "1234").andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            signIn("Forgetful Cashier", "0000").andExpect(status().isUnauthorized());
        }

        signIn("Forgetful Cashier", "1234").andExpect(status().isOk());
    }

    @Test
    void lockingOneNameLeavesOtherNamesAlone() throws Exception {
        createStaffAccount("Locked Waiter", Role.WAITER, "1234");
        createStaffAccount("Free Waiter", Role.WAITER, "1234");

        for (int i = 0; i < 5; i++) {
            signIn("Locked Waiter", "0000").andExpect(status().isUnauthorized());
        }

        signIn("Locked Waiter", "1234").andExpect(status().isTooManyRequests());
        signIn("Free Waiter", "1234").andExpect(status().isOk());
    }

}
