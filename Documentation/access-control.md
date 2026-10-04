# Access control

Who may do what, per role. This is the checklist for issue #29: every endpoint and screen was reviewed against it, and the tests below keep it true. The backend is the only enforcement: the frontend guards below are a convenience that stops a signed-in user from seeing a screen that would only return errors.

> **Status (work in progress, issue #29):** the endpoint and role tables below match the code today. The fail-closed default and the sign-in and session rules marked *planned* are being implemented on branch `feature/rbac-hardening`; until they land, `SecurityConfig` still ends with `permitAll()`. This note is removed when #29 is done.

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

Waiter and cashier accounts can be created and can sign in, but every staff screen shows "access denied" until a screen for them exists. That is deliberate: access is added together with the screen, not before. When the admin Orders view (which will show payment details) is built, decide there which roles see it.

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
| GET | `/api/staff/me` | Signed-in staff (any role) | Any signed-in staff account. |

## Other rules

- **Cancelling** an order is Admin-only (`/api/admin/orders/{id}/cancel`), and the kitchen transition endpoint refuses `CANCELLED` with 403 even for the kitchen's own account.
- **Staff accounts:** only Admin can create, edit or delete. An admin cannot delete their own account (400).
- **Uploaded images** under `/images/**` are public, because the guest menu shows them.

## Sign-in and session rules (planned in #29)

- **PINs** are 4 to 8 digits, enforced when an account is created or its PIN is reset.
- **Throttle:** after 5 failed sign-ins for one name, that name is locked for 15 minutes and further attempts get 429, even with the right PIN. The count resets on a successful sign-in.
- **Changes take effect immediately.** The account is re-checked on every request, so deleting an account, changing its role or resetting its PIN ends or downgrades an open session at once instead of when it expires.
- **The last admin is protected.** An admin cannot change their own role away from Admin, and the last remaining admin cannot be demoted or deleted.
- **Fresh session on sign-in** (the session id changes), and the session cookie is `SameSite=Lax` and `HttpOnly`. CSRF tokens are not used: the API is same-origin and JSON-only, so `SameSite` is the protection.
