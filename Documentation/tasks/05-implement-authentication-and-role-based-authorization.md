# Issue #5: Implement authentication and role-based authorization

## What was done

Added the backend's first web/security layer — until now the backend had entities and Spring Data repositories only, no controllers, no `@Configuration` classes, no Spring Security at all. Staff now log in with `{name, pin}` against the existing `StaffAccount` entity and get a server-side session; role-based `@PreAuthorize` protection rejects the wrong role on a protected endpoint. Guest-facing endpoints (none exist yet — Order/OrderItem is issue #9, guest screens are issues #17–22) are unaffected: everything outside `/api/staff/**` stays permissive by default.

Two design forks with no answer anywhere in the design docs or proposal PDF were resolved directly with the user: session cookie (not JWT — no new dependency, no signing-key management, fits a single-instance backend) and login by `name` + PIN (added a DB unique constraint on `staff_account.name`, which had none, rather than inventing a separate login-code field).

## Why each dependency

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-security` | The security starter itself — confirmed unchanged in Boot 4.x (only the OAuth2 starters got a `-security-` infix rename); autoconfiguration now lives in `org.springframework.boot.security.autoconfigure`. |
| `spring-boot-starter-security-test` | A new Boot-4.1-only modular test starter (confirmed on Maven Central at 4.1.0), used instead of a bare `spring-security-test` dependency to match this project's existing `-webmvc-test`/`-data-jpa-test` convention. |

## The other files

- **`V10__staff_account_unique_name.sql`** + `@Table(uniqueConstraints = ...)` on `StaffAccount` — `name` had no uniqueness guarantee before; needed as the login lookup key.
- **`staff/security/StaffPrincipal.java`** — `UserDetails` wrapping `StaffAccount` directly, so the `/me` endpoint and `lastSeenAt` updates reach the entity without a second query. Maps `role` to a single `ROLE_<NAME>` authority.
- **`staff/security/StaffAccountUserDetailsService.java`** — looks up via a new `StaffAccountRepository.findByName`.
- **`security/SecurityConfig.java`** — session-based auth (`HttpSessionSecurityContextRepository` exposed as its own bean so the login controller can persist the authenticated context the same way the filter chain reads it), CSRF disabled (deliberate — JSON-only API for a decoupled PWA, not server-rendered forms), a custom `AuthenticationEntryPoint` returning a plain 401 instead of Spring Security's default redirect to a nonexistent `/login` page, `/api/staff/login` public and `/api/staff/**` authenticated with everything else left open, and Spring Security's built-in `logout()` DSL (no hand-rolled logout code).
- **`staff/web/StaffAuthController.java`** — `POST /login` (authenticates, saves the security context into the session, updates `lastSeenAt`, returns name+role — never `pinHash`; bad credentials → generic 401, never reveals whether the name exists), `GET /me`, and `GET /accounts` (`@PreAuthorize("hasRole('ADMIN')")`, id/name/role/lastSeenAt only) — a minimal read-only listing that proves role-based rejection end-to-end without pre-empting issue #15's full staff CRUD. No `/logout` controller method exists: Spring Security's own `LogoutFilter` already intercepts that URL before any controller would be reached.
- **`staff/web/LoginRequest.java` / `StaffResponse.java`** — this project's first DTOs.
- **`security/SecurityConfigTest.java`** — a test-only dummy `@RestController` (one open endpoint, one `@PreAuthorize("hasRole('ADMIN')")` endpoint), `@Import`ed the same way `TestcontainersConfiguration` already is, proving the filter chain + method security + role mapping in isolation from any real business endpoint.
- **`staff/web/StaffAuthControllerTest.java`** — login success/failure, `/me` without a session, `/accounts` as non-admin (403) vs admin (200, confirms `pinHash` never appears in the JSON), and a full login → session cookie → `/me` round trip.

## Verification performed

1. `./mvnw compile test-compile` — all new security/web code compiles cleanly, confirming several unverified Boot 4.1/Spring Security API assumptions made along the way: `spring-boot-starter-security`/`spring-boot-starter-security-test` are real, resolvable Maven Central artifacts at 4.1.1/4.1.0 (checked directly, not from memory — this project has been burned twice before by Boot 4 starter renames); `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` is the correct Boot-4-modularized import path for `@AutoConfigureMockMvc` (confirmed by successful compilation, not guessed); `DaoAuthenticationProvider(UserDetailsService)`'s modern constructor and the `HttpSecurity` lambda DSL calls used all matched the resolved Spring Security version.
2. `./mvnw test -Dtest=SecurityConfigTest` was run (not just compiled) — the Spring context got as far as resolving and instantiating every security autoconfiguration class (`ServletWebSecurityAutoConfiguration`, `SecurityAutoConfiguration`, `UserDetailsServiceAutoConfiguration`, `SecurityFilterAutoConfiguration`, `SecurityMockMvcAutoConfiguration`) before failing purely on Testcontainers requiring Docker, which isn't available in this sandbox (same root cause — `Could not find a valid Docker environment` — as every other DB-backed test in this repo, confirmed via the surefire report's exact stack trace). This rules out a wiring bug in the security config itself, but the tests still need a real run on Docker-enabled CI/local before merge to confirm the actual login/401/403 assertions pass.
3. `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` and a manual `curl` login/`/me`/`/accounts` round trip were **not** run, for the same Docker-availability reason — still outstanding.
