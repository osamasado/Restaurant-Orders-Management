package org.restaurantordersmanagement.backend.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SecurityConfigTest.SecuredTestController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openEndpointIsReachableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/test/open"))
                .andExpect(status().isOk());
    }

    @Test
    void adminOnlyEndpointRejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/test/admin-only"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminOnlyEndpointRejectsNonAdminRole() throws Exception {
        mockMvc.perform(get("/test/admin-only").with(user("waiter").roles("WAITER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminOnlyEndpointAllowsAdminRole() throws Exception {
        mockMvc.perform(get("/test/admin-only").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @RestController
    static class SecuredTestController {

        @GetMapping("/test/open")
        String open() {
            return "open";
        }

        @GetMapping("/test/admin-only")
        @PreAuthorize("hasRole('ADMIN')")
        String adminOnly() {
            return "admin-only";
        }

    }

}
