# Access control

Who may do what, per role. This is the checklist for issue #29: every endpoint and screen was reviewed against it, and the tests below keep it true. The backend is the only enforcement: the frontend guards below are a convenience that stops a signed-in user from seeing a screen that would only return errors.

## Principles

- **Fail closed.** An endpoint is private unless it is on the public list below. A new endpoint nobody remembered to annotate must not become public by accident.
- **One place per rule.** Roles are checked with `@PreAuthorize` on the controller method; status changes go through `OrderStateMachineService`, which also decides what is legal.
- **The server resolves identity.** A guest device is identified by its pairing code and the server looks up the table; a client-sent table id or total is never trusted.
- **Public means minimal.** The two public data feeds (guest menu and hall board) return only what their screen shows.

## Roles

| Role | Can do today |
|---|---|
| Admin | Everything: the management backend, and the kitchen board and availability toggles. Only role that can cancel an order. |
| Kitchen | The kitchen board: see active orders, Start / Ready / Picked up, acknowledge cancellations, mark a meal sold out. Cannot cancel, cannot see payment details, cannot touch menu, staff, tables or settings. |
| Waiter | Nothing yet. No screen or endpoint is assigned to this role. |
| Cashier | Nothing yet. No screen or endpoint is assigned to this role. |
| Guest (not a staff role) | Browse the menu, quote a cart, order from a paired table device, follow that table's own orders. |

Waiter and cashier accounts can be created and can sign in, but every staff screen shows "access denied" until a screen for them exists. That is deliberate: access is added together with the screen, not before. The admin Orders view (#71) shows payment method and totals, and it is **Admin only**: the waiter and cashier roles do not get it. They can be given a screen of their own later, and it would be added together with its endpoints and matrix rows.

`Role.USER` is reserved for a future guest login and cannot be assigned to a staff account.

## Screens

| Screen | Who | Guard |
|---|---|---|
| `/guest` | Anyone holding a paired table device | Pairing code gate |
| `/hall` | Anyone in the dining area | None, shows order and table numbers only |
| `/kitchen` | Kitchen, Admin | Sign-in; other roles see "access denied" |
| `/admin` | Admin | Sign-in; other roles see "access denied" |

## Endpoints

Allowed roles are exactly the `@PreAuthorize` on each method; an anonymous request gets 401, a signed-in user with the wrong role gets 403.

**This table is enforced by the build.** `AccessControlMatrixTest` keeps the same list and fails when an endpoint exists that is not in it, when the table lists one that no longer exists, when a method's `@PreAuthorize` roles differ from the table, or when an anonymous or wrong-role request to a protected endpoint is not refused. To add or change an endpoint, update the table in that test, this document and, for a public endpoint, the public list in `SecurityConfig`.

### Kitchen board

| Method | Path | Allowed roles |
|---|---|---|
| GET | `/api/kitchen/orders` | Kitchen, Admin |
| GET | `/api/kitchen/orders/cancelled` | Kitchen, Admin |
| POST | `/api/kitchen/orders/{orderId}/acknowledge-cancellation` | Kitchen, Admin |
| POST | `/api/kitchen/orders/{orderId}/transition` | Kitchen, Admin |

### Kitchen availability ("ran out?")

| Method | Path | Allowed roles |
|---|---|---|
| GET | `/api/kitchen/meals` | Kitchen, Admin |
| PATCH | `/api/kitchen/meals/{id}/availability` | Kitchen, Admin |

### Menu: categories

| Method | Path | Allowed roles |
|---|---|---|
| GET | `/api/categories` | Admin |
| POST | `/api/categories` | Admin |
| PUT | `/api/categories/{id}` | Admin |
| DELETE | `/api/categories/{id}` | Admin |

### Menu: meals

| Method | Path | Allowed roles |
|---|---|---|
| GET | `/api/meals` | Admin |
| GET | `/api/meals/{id}` | Admin |
| POST | `/api/meals` | Admin |
| PUT | `/api/meals/{id}` | Admin |
| DELETE | `/api/meals/{id}` | Admin |
| PATCH | `/api/meals/{id}/availability` | Admin |
| POST | `/api/meals/{id}/image` | Admin |
| DELETE | `/api/meals/{id}/image` | Admin |

### Menu: raw materials and recipes

| Method | Path | Allowed roles |
|---|---|---|
| GET | `/api/raw-materials` | Admin |
| GET | `/api/raw-materials/{id}` | Admin |
| POST | `/api/raw-materials` | Admin |
| PUT | `/api/raw-materials/{id}` | Admin |
| DELETE | `/api/raw-materials/{id}` | Admin |
| POST | `/api/raw-materials/{id}/image` | Admin |
| DELETE | `/api/raw-materials/{id}/image` | Admin |
| GET | `/api/meal-sizes/{mealSizeId}/recipe` | Admin |
| PUT | `/api/meal-sizes/{mealSizeId}/recipe` | Admin |

### Orders (admin)

| Method | Path | Allowed roles |
|---|---|---|
| GET | `/api/admin/orders` | Admin |
| POST | `/api/admin/orders/{orderId}/transition` | Admin |
| GET | `/api/admin/orders/history` | Admin |
| POST | `/api/admin/orders/{orderId}/cancel` | Admin |

### Tables and devices

| Method | Path | Allowed roles |
|---|---|---|
| GET | `/api/tables` | Admin |
| POST | `/api/tables` | Admin |
| PUT | `/api/tables/{id}` | Admin |
| DELETE | `/api/tables/{id}` | Admin |
| POST | `/api/tables/{id}/pair` | Admin |
| DELETE | `/api/tables/{id}/pair` | Admin |

### Staff accounts

| Method | Path | Allowed roles |
|---|---|---|
| GET | `/api/staff/accounts` | Admin |
| POST | `/api/staff/accounts` | Admin |
| PUT | `/api/staff/accounts/{id}` | Admin |
| DELETE | `/api/staff/accounts/{id}` | Admin |
| POST | `/api/staff/accounts/{id}/reset-pin` | Admin |

### Settings

| Method | Path | Allowed roles |
|---|---|---|
| GET | `/api/settings` | Admin |
| PUT | `/api/settings` | Admin |
| GET | `/api/settings/order-number` | Admin |
| POST | `/api/settings/order-number/reset` | Admin |

### Public or session-only endpoints

| Method | Path | Guard | Notes |
|---|---|---|---|
| POST | `/api/guest/cart/quote` | None (public) | Prices computed on the server, nothing stored. |
| POST | `/api/guest/device/claim` | Device code | Needs a valid pairing code. Returns table number and room only, never the code. |
| GET | `/api/guest/menu` | None (public) | Available meals only, in one language. |
| POST | `/api/guest/orders` | Device code | The paired device code in the body identifies the table; the server resolves it, never a client-sent table id. |
| GET | `/api/guest/orders/{orderId}` | Device code | `X-Device-Code` header; only that device's own table's orders, anything else is a 404. |
| GET | `/api/guest/settings` | None (public) | Currency, tax rate, default language, enabled payment methods. |
| GET | `/api/hall/orders` | None (public) | Order number and table number only. No names, prices or items. |
| POST | `/api/staff/login` | None (public) | Name and PIN. |
| GET, HEAD | `/`, `/index.html`, `/version.txt`, `/sw.js`, `/manifest.webmanifest`, `/favicon.svg`, `/assets/**`, `/icons/**`, `/guest`, `/kitchen`, `/hall`, `/admin`, `/admin/**` | None (public) | The React app's own files and screen paths, only when the backend serves the app itself (the single-image deployment; the paths are in `SpaPaths`). No data: the screens ask for the name and PIN themselves. |
| GET | `/api/staff/me` | Signed-in staff (any role) | Any signed-in staff account. |

## Other rules

- **Payment details** (payment method and totals): among staff, only Admin sees them, through the admin Orders list. The kitchen and hall boards never carry them. A guest device sees them only for its own table's orders (`/api/guest/orders/{orderId}`).
- **The admin transition endpoint** accepts only Start, Ready and Served. `CANCELLED` is refused with 403 (use the cancel endpoint) and so is submitting (the guest's own step, which prices the order and takes its number); an illegal step from the order's current status is a 409.
- **Cancelling** an order is Admin-only (`/api/admin/orders/{id}/cancel`), and the kitchen transition endpoint refuses `CANCELLED` with 403 even for the kitchen's own account.
- **Restarting the displayed order number** (`POST /api/settings/order-number/reset`) is Admin-only, is refused with 409 while any order is submitted, in preparation or ready, and is recorded with the admin and the time (`order_number_reset`). It restarts only the displayed number; the internal order number is never reset.
- **Staff accounts:** only Admin can create, edit or delete. An admin cannot delete their own account (400).
- **Where the first account comes from:** the API cannot create it (every staff endpoint needs a signed-in admin). On an empty installation the backend creates the first administrator, and optionally a kitchen account, at startup from `BOOTSTRAP_ADMIN_NAME`/`BOOTSTRAP_ADMIN_PIN` (and `BOOTSTRAP_KITCHEN_*`), or the demo seed does (`APP_SEED_DEMO`, public PIN `1234`). The bootstrap never touches an installation that has accounts, except that `BOOTSTRAP_ADMIN_RESET=true` resets the PIN of the one named existing administrator for that start. The PIN rule (4 to 8 digits) is enforced in `StaffAccountService` for the API and the bootstrap alike. See `Documentation/deployment.md`.
- **Uploaded images** under `/images/**` are public (GET and HEAD), because the guest menu shows them.

## Sign-in and session rules

All enforced and tested:

- **PINs** are 4 to 8 digits, checked when an account is created or its PIN is reset (400 otherwise).
- **Throttle:** after 5 failed sign-ins for one name, that name is locked for 15 minutes. While locked, every attempt gets 429 with a `Retry-After` header, even with the right PIN, and refused attempts do not extend the lock. A successful sign-in clears the count. A name that does not exist locks exactly like one that does, so the lock cannot be used to find accounts. The count is in memory (one backend instance; a restart clears it). The price: anyone who knows a name can lock that account for 15 minutes.
- **The last admin is protected.** Nobody can change their own role, and the last remaining admin cannot be demoted or deleted (409). Admins are locked while this is checked, so two admins demoting each other at the same moment cannot leave none.

- **Changes take effect immediately.** The account is read again on every request from a signed-in staff member (`StaffSessionRecheckFilter`). If it was deleted or its PIN was reset, the session ends and the request is treated as anonymous; if its role or name changed, the session continues with the new values, so a demotion removes the old access on the very next request. Cost: one lookup by primary key per signed-in request.
- **Fresh session on sign-in:** the session id changes when someone signs in, so an id planted beforehand does not survive.
- **Screens follow the session.** A 401 on any staff request (account deleted, PIN reset, session gone) returns the screen to the sign-in form; a 403 (role changed) makes the screen ask the server who it is now, so the kitchen or admin screen switches to "access denied" instead of sitting on "Connection lost". A locked name sees "Too many failed attempts. Try again in N min." instead of "Invalid name or PIN". The staff form limits a new PIN to 4 to 8 digits and does not offer to change your own role.
- **Session cookie** is `HttpOnly` and `SameSite=Lax`. CSRF tokens are not used: the API is same-origin and JSON-only, and `SameSite=Lax` keeps the browser from sending the cookie on cross-site POST, PUT, PATCH and DELETE. In production set `SESSION_COOKIE_SECURE=true` when serving over HTTPS (it defaults to off so a plain-HTTP deployment can still sign in).
