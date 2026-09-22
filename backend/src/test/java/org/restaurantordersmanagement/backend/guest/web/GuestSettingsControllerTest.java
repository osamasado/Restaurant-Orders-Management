package org.restaurantordersmanagement.backend.guest.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Read-only, no fixtures created - nothing to tear down. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GuestSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void settingsAreReachableWithoutAuthenticationAndExposeCurrency() throws Exception {
        mockMvc.perform(get("/api/guest/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencyCode").value("EUR"))
                .andExpect(jsonPath("$.currencySymbol").value("€"))
                .andExpect(jsonPath("$.symbolPosition").value("SUFFIX"));
    }

}
