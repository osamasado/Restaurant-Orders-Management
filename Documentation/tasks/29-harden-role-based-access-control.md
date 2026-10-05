# Issue #29: Harden role-based access control across all APIs and screens

## What was done

Every endpoint and screen was reviewed against the roles, and the checklist is now `Documentation/access-control.md`. The role table itself turned out to be right: all 50 endpoints carry the roles they should (35 admin-only, 6 kitchen or admin, 9 public or session-only). What was wrong was everything around it: the default was open, nothing checked the table, and a signed-in session never noticed that its account had changed.

Gaps found and fixed:

- **Security failed open.** `SecurityConfig` ended with `anyRequest().permitAll()`, so a new endpoint without `@PreAuthorize` was silently public. Now everything needs a sign-in except an explicit list of the 8 public endpoints (by method and path, not whole prefixes), and `@PreAuthorize` narrows it to roles.
- **Nothing checked the table.** `AccessControlMatrixTest` keeps the 50-endpoint table in code and fails when an endpoint is missing from it, a listed one no longer exists, a method's roles differ from the table, or an anonymous or wrong-role request is not refused.
- **Open sessions ignored account changes.** Deleting an account, demoting it or resetting its PIN changed nothing until the session expired, and a kitchen screen polling every few seconds never lets one expire. The account is now re-read on every signed-in request.
- **An admin could lock everyone out** by changing their own role, or the last admin's. Changing your own role is refused, and the last administrator cannot be demoted or deleted. Admin rows are locked while this is checked, so two admins demoting each other at the same moment cannot leave none.
- **PINs were unrestricted and sign-in was unthrottled.** PINs are now 4 to 8 digits, and 5 failed sign-ins lock that name for 15 minutes (429 with `Retry-After`), even against the right PIN. A name that does not exist locks exactly like one that does.
- **Sign-in did not rotate the session id**, and the session cookie had no `SameSite`. The id now changes at sign-in, and the cookie is `HttpOnly` and `SameSite=Lax`, which is what stands in for CSRF tokens on a same-origin JSON API.
- **The screens did not react.** A locked name saw "Invalid name or PIN", a demoted kitchen user sat on "Connection lost" forever, and an admin who reset their own PIN was left on a dead screen. A 401 on any staff request now returns to the sign-in form, and a 403 makes the screen re-read the role so "access denied" appears.

Waiter and cashier are documented as having no access yet: no screen or endpoint is assigned to them, and access should arrive together with their screens.

## The other files

Backend:
- **`security/SecurityConfig.java`**: the explicit public list, `ERROR` dispatch left open, and the re-check filter added to the chain.
- **`staff/security/StaffSessionRecheckFilter.java`**: a plain filter, not a bean, so Boot does not register it twice. Gone or PIN changed ends the session; role or name changed continues with fresh values.
- **`staff/security/LoginThrottle.java`**: in-memory, per name (case and spaces ignored), with a capped table that drops the oldest name first. The clock is injectable for tests.
- **`staff/service/StaffAccountService.java`**, **`StaffAccountRepository.java`**, **`StaffAccountController.java`**: last-admin and self-role guards (with a pessimistic lock on the admin rows) and PIN validation.
- **`staff/web/StaffAuthController.java`**: throttle, 429 with `Retry-After`, and `changeSessionId()` before the context is saved.
- **`application.properties`**, **`application-prod.properties`**: cookie settings; `SESSION_COOKIE_SECURE` (default off, so a plain-HTTP deployment can still sign in) sets the `Secure` flag.

Frontend:
- **`api/http.ts`**: `onSessionProblem()` reports a 401 or 403 from a staff request (not from sign-in, `me` or logout); `ApiError` carries `retryAfterSeconds`.
- **`auth/AuthProvider.tsx`**: signs out on 401, re-reads the role on 403. Guest screens have no `AuthProvider`, so they are unaffected.
- **`auth/LoginForm.tsx`**: the locked message with the minutes left.
- **`StaffFormModal.tsx`**, **`StaffView.tsx`**: the new-PIN input is limited to 4 to 8 digits with a hint, the reset-PIN prompt checks the same rule, and the role select is disabled on your own account.
- **`i18n/locales/{en,de,ar}.json`**: `admin.auth.locked`, `admin.staff.pinHint`, `pinInvalid`, `ownRoleLocked` (287 keys each).

## Verification performed

1. Full backend suite (`./mvnw test`, Testcontainers Postgres): 193 tests, 0 failures (151 before this ticket, so 42 are new):
   - security rules, the matrix test and real-HTTP tests;
   - account rules and the last-admin unit tests;
   - 10 throttle unit tests with a fake clock, and 4 lockout API tests;
   - 7 session tests.
2. Every new guard was proved by breaking it and watching its tests fail, then restoring the code:
   - removing the `ERROR` permit made a guest's 403 come back as 401;
   - mutating three controllers (a removed `@PreAuthorize`, a widened role and an unreviewed endpoint) was caught with clear messages, and showed kitchen, waiter and cashier reaching meal delete;
   - removing the self-role, last-admin and PIN checks failed 6 tests, and a bad PIN was accepted;
   - removing failure counting failed 3 lockout tests, and the right PIN worked after five wrong guesses.
3. The session tests were written first and run red: 6 failed (deleted account still 200, demoted admin still 200, PIN reset not ending the session, rename not shown, same session id after sign-in, no `SameSite`) and went green with the fixes.
4. A browser check in headless Chromium against a separate throwaway stack (own Postgres container, backend on 18081, Vite on 15175; the dev setup was untouched): 14 checks, all passing, covering:
   - the locked message ("Try again in 15 min.");
   - a kitchen screen left open while an admin demotes, deletes, or resets the PIN of its account;
   - an admin resetting their own PIN and signing back in;
   - the staff form's PIN limits and locked role.

   With the hook turned off, the demotion checks and the own-PIN-reset check fail, so those depend on the new code.
5. Frontend: `npm run check:i18n` clean, `npx tsc -b`, `npm run build`, eslint on the touched files clean (the remaining errors are the existing `setState`-in-effect ones in admin views).

Issues found along the way:
- **Error forwards:** failing closed also guards the container's forward to `/error`, which turns every anonymous guest's 403, 404 or 400 into a 401. MockMvc never performs that forward, so a real-HTTP test was needed to see it.
- **Body before role:** Spring parses the request body before evaluating `@PreAuthorize`, so refusal tests for nine endpoints with primitive or list bodies got 400 until they were given a body that parses.
- **Own PIN reset ends your own session**, by design: the next action returns to the sign-in form.

Not done, for a decision:
- **The lock can be used against a person:** anyone who knows a staff name can lock that account for 15 minutes. That is the usual price of this kind of lock, and it is written in the checklist.
- **One lookup per signed-in request** is the cost of the session re-check. Fine at restaurant scale; a session registry would avoid it but needs a single instance and misses cases.
- **`Secure` cookie** is off by default; set `SESSION_COOKIE_SECURE=true` when serving over HTTPS.
- **The device pairing code** (6 characters) is not throttled on `POST /api/guest/device/claim`. About a billion combinations make guessing impractical, but it is public.
- **Waiter and cashier** have no screens or endpoints; decide their access when the admin Orders view (which will show payment details) is built.
