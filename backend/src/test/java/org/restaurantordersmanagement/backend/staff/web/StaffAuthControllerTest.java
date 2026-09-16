package org.restaurantordersmanagement.backend.staff.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

}
