package org.restaurantordersmanagement.backend.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
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

/**
 * The security rules themselves, with throwaway endpoints: the default is
 * "closed", only the explicit public list is open, and @PreAuthorize narrows
 * a signed-in request to roles. The real endpoints are checked against the
 * access-control checklist in AccessControlMatrixTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SecurityConfigTest.SecuredTestController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anEndpointWithoutAnyAnnotationIsNotPublic() throws Exception {
        // The point of failing closed: a new endpoint nobody remembered to protect still needs a sign-in.
        mockMvc.perform(get("/api/test/unannotated"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anEndpointWithoutRoleRulesIsOpenToAnySignedInAccount() throws Exception {
        mockMvc.perform(get("/api/test/unannotated").with(user("waiter").roles("WAITER")))
                .andExpect(status().isOk());
    }

    @Test
    void publicListIsNotAWholePrefix() throws Exception {
        // /api/guest/menu is public, but that does not make everything under /api/guest public.
        mockMvc.perform(get("/api/guest/unlisted"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsStayOpenToAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/hall/orders")).andExpect(status().isOk());
        mockMvc.perform(get("/api/guest/menu").param("language", "EN")).andExpect(status().isOk());
        mockMvc.perform(get("/api/guest/settings")).andExpect(status().isOk());
    }

    @Test
    void picturesAreOpenToAnonymousGetAndHeadRequests() throws Exception {
        // The file does not exist, so 404 is the answer: what matters is that it is not 401 (the rule let it through).
        mockMvc.perform(get("/images/meals/none.png")).andExpect(status().isNotFound());
        mockMvc.perform(head("/images/meals/none.png")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/images/meals/none.png")).andExpect(status().isUnauthorized());
    }

    @Test
    void aPublicPathIsOnlyPublicForItsListedMethod() throws Exception {
        mockMvc.perform(delete("/api/hall/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminOnlyEndpointChallengesAnonymousRequest() throws Exception {
        // Spring Security returns 401 here, not 403: ExceptionTranslationFilter treats an
        // AccessDeniedException from an anonymous principal as "we don't know who this is
        // yet" and delegates to the AuthenticationEntryPoint. 403 is reserved for a request
        // that IS authenticated but lacks the required role - see
        // adminOnlyEndpointRejectsNonAdminRole below.
        mockMvc.perform(get("/api/test/admin-only"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminOnlyEndpointRejectsNonAdminRole() throws Exception {
        mockMvc.perform(get("/api/test/admin-only").with(user("waiter").roles("WAITER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminOnlyEndpointAllowsAdminRole() throws Exception {
        mockMvc.perform(get("/api/test/admin-only").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @RestController
    static class SecuredTestController {

        @GetMapping("/api/test/unannotated")
        String unannotated() {
            return "unannotated";
        }

        @GetMapping("/api/guest/unlisted")
        String unlistedUnderPublicPrefix() {
            return "unlisted";
        }

        @GetMapping("/api/test/admin-only")
        @PreAuthorize("hasRole('ADMIN')")
        String adminOnly() {
            return "admin-only";
        }

    }

}
