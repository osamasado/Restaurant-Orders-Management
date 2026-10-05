package org.restaurantordersmanagement.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Over a real HTTP server, because cookie attributes are added by the servlet
 * container and MockMvc never sees them. The API has no CSRF tokens: the
 * session cookie being SameSite=Lax (not sent on cross-site POST, PUT, PATCH or
 * DELETE) and HttpOnly (not readable by page scripts) is what protects it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SessionCookieOverHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long createdId;

    @AfterEach
    void tearDown() {
        if (createdId != null) {
            staffAccountRepository.deleteById(createdId);
        }
    }

    @Test
    void theSessionCookieIsHttpOnlyAndSameSiteLax() throws Exception {
        StaffAccount account = new StaffAccount();
        account.setName("Cookie Cook");
        account.setRole(Role.KITCHEN);
        account.setPinHash(passwordEncoder.encode("1234"));
        createdId = staffAccountRepository.saveAndFlush(account).getId();

        HttpResponse<Void> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/staff/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Cookie Cook\",\"pin\":\"1234\"}"))
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        assertEquals(200, response.statusCode());
        List<String> cookies = response.headers().allValues("Set-Cookie");
        String session = cookies.stream().filter(c -> c.startsWith("JSESSIONID=")).findFirst().orElseThrow();
        assertTrue(session.toLowerCase().contains("httponly"), "session cookie was: " + session);
        assertTrue(session.toLowerCase().contains("samesite=lax"), "session cookie was: " + session);
    }

}
