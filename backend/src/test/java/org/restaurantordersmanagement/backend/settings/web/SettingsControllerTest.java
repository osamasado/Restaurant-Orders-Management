package org.restaurantordersmanagement.backend.settings.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Config is a singleton row shared by the whole test suite (ConfigRepositoryTest
 * and OrderPricingServiceTest both assert its seeded baseline) - unlike the
 * list-based CRUD tests elsewhere, this one can't just delete what it created,
 * because it doesn't create anything; it mutates the only row that exists.
 * @BeforeEach captures the current settings and @AfterEach restores them
 * exactly, so this test never leaves the shared row in a different state.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private String originalSettingsJson;

    @BeforeEach
    void captureOriginalSettings() throws Exception {
        originalSettingsJson = mockMvc.perform(get("/api/settings").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @AfterEach
    void restoreOriginalSettings() throws Exception {
        mockMvc.perform(put("/api/settings")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJsonFrom(originalSettingsJson)))
                .andExpect(status().isOk());
    }

    /** Builds a ConfigRequest body from a captured ConfigResponse body (which has an extra "id" field ConfigRequest doesn't accept). */
    private String requestJsonFrom(String responseJson) {
        String currencyCode = JsonPath.read(responseJson, "$.currencyCode");
        String currencySymbol = JsonPath.read(responseJson, "$.currencySymbol");
        String symbolPosition = JsonPath.read(responseJson, "$.symbolPosition");
        Number taxRate = JsonPath.read(responseJson, "$.taxRate");
        String defaultLanguage = JsonPath.read(responseJson, "$.defaultLanguage");
        List<String> paymentMethods = JsonPath.read(responseJson, "$.enabledPaymentMethods");

        return """
                {
                    "currencyCode": "%s",
                    "currencySymbol": "%s",
                    "symbolPosition": "%s",
                    "taxRate": %s,
                    "defaultLanguage": "%s",
                    "enabledPaymentMethods": [%s]
                }
                """
                .formatted(
                        currencyCode,
                        currencySymbol,
                        symbolPosition,
                        taxRate,
                        defaultLanguage,
                        paymentMethods.stream().map(method -> "\"" + method + "\"").collect(Collectors.joining(",")));
    }

    @Test
    void getRejectsNonAdminRole() throws Exception {
        mockMvc.perform(get("/api/settings").with(user("waiter").roles("WAITER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getReturnsSeededDefaults() throws Exception {
        mockMvc.perform(get("/api/settings").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencyCode").value("EUR"))
                .andExpect(jsonPath("$.currencySymbol").value("€"))
                .andExpect(jsonPath("$.symbolPosition").value("SUFFIX"))
                .andExpect(jsonPath("$.taxRate").value(19.00))
                .andExpect(jsonPath("$.defaultLanguage").value("DE"))
                .andExpect(jsonPath("$.enabledPaymentMethods.length()").value(4));
    }

    @Test
    void updateRejectsNonAdminRole() throws Exception {
        mockMvc.perform(put("/api/settings")
                        .with(user("waiter").roles("WAITER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJsonFrom(originalSettingsJson)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateRejectsBlankCurrencyCode() throws Exception {
        mockMvc.perform(put("/api/settings")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                    "currencyCode": "",
                                    "currencySymbol": "€",
                                    "symbolPosition": "SUFFIX",
                                    "taxRate": 19.00,
                                    "defaultLanguage": "DE",
                                    "enabledPaymentMethods": ["CASH"]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRejectsEmptyPaymentMethods() throws Exception {
        mockMvc.perform(put("/api/settings")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                    "currencyCode": "EUR",
                                    "currencySymbol": "€",
                                    "symbolPosition": "SUFFIX",
                                    "taxRate": 19.00,
                                    "defaultLanguage": "DE",
                                    "enabledPaymentMethods": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateChangesPersistAndCanBeRetrieved() throws Exception {
        mockMvc.perform(put("/api/settings")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                    "currencyCode": "USD",
                                    "currencySymbol": "$",
                                    "symbolPosition": "PREFIX",
                                    "taxRate": 7.50,
                                    "defaultLanguage": "EN",
                                    "enabledPaymentMethods": ["CASH", "CARD"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.symbolPosition").value("PREFIX"))
                .andExpect(jsonPath("$.taxRate").value(7.50))
                .andExpect(jsonPath("$.enabledPaymentMethods.length()").value(2));

        mockMvc.perform(get("/api/settings").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.defaultLanguage").value("EN"));
    }

}
