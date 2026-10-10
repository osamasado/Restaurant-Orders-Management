package org.restaurantordersmanagement.backend.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The backend serving the React app itself (the single-image deployment). A small fake build in
 * src/test/resources/spa-test stands in for the real frontend bundle.
 */
@SpringBootTest(properties = {
    "app.spa.index=classpath:/spa-test/index.html",
    "spring.web.resources.static-locations=classpath:/spa-test/"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SpaServingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theStartPageAndTheFilesArePublicAndTheStartPageIsNeverCached() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getForwardedUrl())
                        .as("the welcome page is index.html").contains("index.html"))
                .andExpect(header().string("Cache-Control", "no-cache"));
        mockMvc.perform(get("/sw.js")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"));
        mockMvc.perform(get("/version.txt")).andExpect(status().isOk())
                .andExpect(content().string("0.0.0-test"));
    }

    @Test
    void hashedAssetsAreCachedForAYear() throws Exception {
        mockMvc.perform(get("/assets/app-abc123.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"));
    }

    @Test
    void everyScreenPathAnswersWithTheStartPage() throws Exception {
        for (String path : new String[] {"/guest", "/kitchen", "/hall", "/admin", "/admin/orders", "/admin/dashboard"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"))
                    .andExpect(header().string("Cache-Control", "no-cache"));
        }
    }

    @Test
    void aHeadRequestForThePageIsPublicToo() throws Exception {
        mockMvc.perform(head("/")).andExpect(status().isOk());
        mockMvc.perform(head("/kitchen")).andExpect(status().isOk());
        mockMvc.perform(head("/api/staff/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void theApiAndEverythingElseStayBehindTheirRules() throws Exception {
        mockMvc.perform(get("/api/staff/accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/staff/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/something-else")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/admin")).andExpect(status().isUnauthorized());
    }
}
