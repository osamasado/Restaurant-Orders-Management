package org.restaurantordersmanagement.backend.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.security.StaffPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Documentation/access-control.md as an executable table. EXPECTED is the
 * single place that says who may call what; this test fails when
 * - an endpoint exists that is not in the table (a new, unreviewed endpoint),
 * - the table lists an endpoint that no longer exists,
 * - an endpoint's @PreAuthorize roles differ from the table (a widened or narrowed role),
 * - an anonymous or wrong-role request to a protected endpoint is not refused (401 / 403), or
 * - a public endpoint answers an anonymous request with a sign-in challenge.
 *
 * Only refusals are exercised against protected endpoints, so nothing is
 * changed: a refused request never reaches the controller method. Allowed
 * calls are covered by each controller's own tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccessControlMatrixTest {

    private enum Kind {
        ADMIN(Set.of(Role.ADMIN)),
        KITCHEN_OR_ADMIN(Set.of(Role.KITCHEN, Role.ADMIN)),
        /** No sign-in needed; guarded by a device code or by returning only what its screen shows. */
        PUBLIC(Set.of()),
        /** Any signed-in staff account, whatever its role. */
        SIGNED_IN(Set.of());

        private final Set<Role> roles;

        Kind(Set<Role> roles) {
            this.roles = roles;
        }
    }

    private record Rule(String method, String path, Kind kind) {

        String key() {
            return method + " " + path;
        }

        boolean protectedByRole() {
            return kind == Kind.ADMIN || kind == Kind.KITCHEN_OR_ADMIN;
        }
    }

    private static final List<Rule> EXPECTED = List.of(
            // Kitchen board and availability
            kitchen("GET", "/api/kitchen/orders"),
            kitchen("GET", "/api/kitchen/orders/cancelled"),
            kitchen("POST", "/api/kitchen/orders/{orderId}/acknowledge-cancellation"),
            kitchen("POST", "/api/kitchen/orders/{orderId}/transition"),
            kitchen("GET", "/api/kitchen/meals"),
            kitchen("PATCH", "/api/kitchen/meals/{id}/availability"),
            // Menu
            admin("GET", "/api/categories"),
            admin("POST", "/api/categories"),
            admin("PUT", "/api/categories/{id}"),
            admin("DELETE", "/api/categories/{id}"),
            admin("GET", "/api/meals"),
            admin("POST", "/api/meals"),
            admin("GET", "/api/meals/{id}"),
            admin("PUT", "/api/meals/{id}"),
            admin("DELETE", "/api/meals/{id}"),
            admin("PATCH", "/api/meals/{id}/availability"),
            admin("POST", "/api/meals/{id}/image"),
            admin("DELETE", "/api/meals/{id}/image"),
            admin("GET", "/api/raw-materials"),
            admin("POST", "/api/raw-materials"),
            admin("GET", "/api/raw-materials/{id}"),
            admin("PUT", "/api/raw-materials/{id}"),
            admin("DELETE", "/api/raw-materials/{id}"),
            admin("POST", "/api/raw-materials/{id}/image"),
            admin("DELETE", "/api/raw-materials/{id}/image"),
            admin("GET", "/api/meal-sizes/{mealSizeId}/recipe"),
            admin("PUT", "/api/meal-sizes/{mealSizeId}/recipe"),
            // Orders, tables, staff, settings
            admin("POST", "/api/admin/orders/{orderId}/cancel"),
            admin("GET", "/api/tables"),
            admin("POST", "/api/tables"),
            admin("PUT", "/api/tables/{id}"),
            admin("DELETE", "/api/tables/{id}"),
            admin("POST", "/api/tables/{id}/pair"),
            admin("DELETE", "/api/tables/{id}/pair"),
            admin("GET", "/api/staff/accounts"),
            admin("POST", "/api/staff/accounts"),
            admin("PUT", "/api/staff/accounts/{id}"),
            admin("DELETE", "/api/staff/accounts/{id}"),
            admin("POST", "/api/staff/accounts/{id}/reset-pin"),
            admin("GET", "/api/settings"),
            admin("PUT", "/api/settings"),
            // Public: guest, hall and sign-in
            publicEndpoint("GET", "/api/guest/menu"),
            publicEndpoint("GET", "/api/guest/settings"),
            publicEndpoint("POST", "/api/guest/cart/quote"),
            publicEndpoint("POST", "/api/guest/device/claim"),
            publicEndpoint("POST", "/api/guest/orders"),
            publicEndpoint("GET", "/api/guest/orders/{orderId}"),
            publicEndpoint("GET", "/api/hall/orders"),
            publicEndpoint("POST", "/api/staff/login"),
            // Any signed-in staff account
            new Rule("GET", "/api/staff/me", Kind.SIGNED_IN));

    private static final Map<String, Rule> BY_KEY =
            EXPECTED.stream().collect(Collectors.toMap(Rule::key, Function.identity()));

    private static final Pattern ROLE_EXPRESSION =
            Pattern.compile("^(hasRole\\('[A-Z]+'\\)|hasAnyRole\\('[A-Z]+'(,\\s*'[A-Z]+')*\\))$");

    private static Rule admin(String method, String path) {
        return new Rule(method, path, Kind.ADMIN);
    }

    private static Rule kitchen(String method, String path) {
        return new Rule(method, path, Kind.KITCHEN_OR_ADMIN);
    }

    private static Rule publicEndpoint(String method, String path) {
        return new Rule(method, path, Kind.PUBLIC);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyEndpointIsInTheTableWithTheRolesTheTableSays() {
        List<String> problems = new ArrayList<>();
        Set<String> found = new HashSet<>();

        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            if (!handler.getBeanType().getName().startsWith("org.restaurantordersmanagement.backend")) {
                return;
            }
            for (String key : keysOf(info)) {
                found.add(key);
                Rule rule = BY_KEY.get(key);
                if (rule == null) {
                    problems.add("not in the access-control table: " + key
                            + " (review it, then add it here, to Documentation/access-control.md and, if public, to SecurityConfig)");
                    continue;
                }
                checkAnnotation(key, rule, handler, problems);
            }
        });

        EXPECTED.stream()
                .filter(rule -> !found.contains(rule.key()))
                .forEach(rule -> problems.add("the table lists an endpoint that does not exist: " + rule.key()));

        assertNoProblems(problems);
    }

    @Test
    void protectedEndpointsRefuseAnonymousAndWrongRoleRequests() throws Exception {
        List<String> problems = new ArrayList<>();

        for (Rule rule : EXPECTED) {
            if (!rule.protectedByRole()) {
                continue;
            }
            int anonymous = statusOf(rule, null);
            if (anonymous != 401) {
                problems.add(rule.key() + ": anonymous got " + anonymous + ", expected 401");
            }
            for (Role role : Role.values()) {
                if (rule.kind.roles.contains(role)) {
                    continue;
                }
                int status = statusOf(rule, user("someone").roles(role.name()));
                if (status != 403) {
                    problems.add(rule.key() + ": " + role + " got " + status + ", expected 403");
                }
            }
        }

        assertNoProblems(problems);
    }

    @Test
    void publicEndpointsDoNotChallengeAnonymousRequests() throws Exception {
        List<String> problems = new ArrayList<>();

        for (Rule rule : EXPECTED) {
            // Sign-in itself answers a wrong or missing PIN with 401, which says nothing about the rules.
            if (rule.kind != Kind.PUBLIC || rule.path.equals("/api/staff/login")) {
                continue;
            }
            int status = statusOf(rule, null);
            if (status == 401) {
                problems.add(rule.key() + " is in the public list but an anonymous request was challenged (401)");
            }
        }

        assertNoProblems(problems);
    }

    @Test
    void staffMeNeedsASignInButNotAParticularRole() throws Exception {
        Rule me = BY_KEY.get("GET /api/staff/me");
        List<String> problems = new ArrayList<>();

        int anonymous = statusOf(me, null);
        if (anonymous != 401) {
            problems.add("anonymous got " + anonymous + ", expected 401");
        }
        for (Role role : Role.values()) {
            StaffAccount account = new StaffAccount();
            account.setName("Someone " + role);
            account.setRole(role);
            int status = statusOf(me, user(new StaffPrincipal(account)));
            if (status != 200) {
                problems.add(role + " got " + status + ", expected 200");
            }
        }

        assertNoProblems(problems);
    }

    private static Set<String> keysOf(RequestMappingInfo info) {
        Set<String> keys = new HashSet<>();
        Set<String> paths = info.getPathPatternsCondition().getPatternValues();
        info.getMethodsCondition().getMethods()
                .forEach(method -> paths.forEach(path -> keys.add(method.name() + " " + path)));
        if (info.getMethodsCondition().getMethods().isEmpty()) {
            paths.forEach(path -> keys.add("ANY " + path));
        }
        return keys;
    }

    private static void checkAnnotation(String key, Rule rule, HandlerMethod handler, List<String> problems) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), PreAuthorize.class);
        }

        if (!rule.protectedByRole()) {
            if (annotation != null) {
                problems.add(key + " is " + rule.kind + " in the table but has @PreAuthorize(\"" + annotation.value() + "\")");
            }
            return;
        }
        if (annotation == null) {
            problems.add(key + " should be limited to " + rule.kind.roles + " but has no @PreAuthorize");
            return;
        }
        if (!ROLE_EXPRESSION.matcher(annotation.value()).matches()) {
            problems.add(key + " uses an expression this test cannot read: " + annotation.value()
                    + " (use hasRole / hasAnyRole with plain role names)");
            return;
        }
        Set<Role> actual = Pattern.compile("'([A-Z]+)'").matcher(annotation.value()).results()
                .map(match -> Role.valueOf(match.group(1)))
                .collect(Collectors.toSet());
        if (!actual.equals(rule.kind.roles)) {
            problems.add(key + " allows " + actual + " but the table says " + rule.kind.roles);
        }
    }

    /**
     * Spring reads the body and multipart parts before it evaluates
     * @PreAuthorize, so a refusal test needs a body that parses (and a file
     * part for the image uploads), or it would be a 400 and never reach the check.
     */
    private int statusOf(Rule rule, RequestPostProcessor login) throws Exception {
        String url = rule.path.replaceAll("\\{[^}]+}", "1");
        HttpMethod method = HttpMethod.valueOf(rule.method);

        RequestBuilder request;
        if (method == HttpMethod.POST && url.endsWith("/image")) {
            var upload = MockMvcRequestBuilders.multipart(url)
                    .file(new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1}));
            if (login != null) {
                upload.with(login);
            }
            request = upload;
        } else {
            var plain = MockMvcRequestBuilders.request(method, url);
            if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
                plain.contentType(MediaType.APPLICATION_JSON).content(bodyFor(rule));
            }
            if (login != null) {
                plain.with(login);
            }
            request = plain;
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    /**
     * A body that parses for the endpoints whose request has primitive fields
     * or is a list (an empty object is rejected with 400 while the body is
     * read, before the role check). The content does not matter: a refused
     * request never reaches the method.
     */
    private static String bodyFor(Rule rule) {
        String path = rule.path;
        if (path.endsWith("/availability")) {
            return "{\"available\":true}";
        }
        if (path.endsWith("/recipe")) {
            return "[]";
        }
        if (path.startsWith("/api/categories")) {
            return "{\"sortOrder\":1,\"translations\":[]}";
        }
        if (path.startsWith("/api/meals")) {
            return "{\"categoryId\":1,\"available\":true,\"translations\":[],\"sizes\":[]}";
        }
        if (path.startsWith("/api/tables")) {
            return "{\"tableNumber\":\"1\",\"room\":\"Room\",\"seats\":1}";
        }
        return "{}";
    }

    private static void assertNoProblems(List<String> problems) {
        assertTrue(problems.isEmpty(), "\n" + String.join("\n", problems));
    }

}
