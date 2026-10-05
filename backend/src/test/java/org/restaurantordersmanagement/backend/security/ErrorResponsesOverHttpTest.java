package org.restaurantordersmanagement.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Over a real HTTP server, because MockMvc never performs the servlet
 * container's error forward. When a controller answers 403, 404 or 400 through
 * sendError(), the container forwards to /error; if the security rules also
 * guarded that forward, every anonymous guest's error would come back as 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ErrorResponsesOverHttpTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private int statusOf(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    @Test
    void anonymousGuestGetsTheRealErrorNotAChallenge() throws Exception {
        // Unpaired device: the service answers 403.
        assertEquals(403, statusOf(request("/api/guest/orders/999999").header("X-Device-Code", "NOPE00").GET()));
        // Unknown pairing code: 404.
        assertEquals(404, statusOf(request("/api/guest/device/claim")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"ZZZZZZ\"}"))));
        // Missing required header: 400.
        assertEquals(400, statusOf(request("/api/guest/orders/1").GET()));
    }

    @Test
    void wrongStaffCredentialsAreRejectedWith401() throws Exception {
        assertEquals(401, statusOf(request("/api/staff/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"nobody\",\"pin\":\"0000\"}"))));
    }

    @Test
    void protectedAndUnknownPathsAreChallengedWithoutRevealingWhichExist() throws Exception {
        assertEquals(401, statusOf(request("/api/meals").GET()));
        assertEquals(401, statusOf(request("/api/does-not-exist").GET()));
    }

}
