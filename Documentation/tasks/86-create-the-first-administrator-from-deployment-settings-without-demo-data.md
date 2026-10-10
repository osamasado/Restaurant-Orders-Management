# Issue #86: Create the first administrator from deployment settings, without demo data

## What was done

A new installation has no staff account, and the only way to get one was the demo seed, whose accounts all have the public PIN `1234`. The backend can now create the first administrator (and a kitchen account) from deployment settings, so a real deployment never needs the demo data.

- **`BOOTSTRAP_ADMIN_NAME` + `BOOTSTRAP_ADMIN_PIN`** create the first administrator on an **empty** installation; **`BOOTSTRAP_KITCHEN_NAME` + `BOOTSTRAP_KITCHEN_PIN` create a kitchen account in the same transaction (both or neither, and never without the administrator).
- **It never touches an installation that has accounts.** With accounts present it creates nothing, changes nothing and logs one line telling you to remove the settings. A leftover setting can therefore never overwrite or reset a real account.
- **No settings, no accounts, one log line** explaining how to create the first administrator.
- **Wrong settings stop the start.** A name without a PIN (or the reverse), a PIN that is not 4 to 8 digits, a kitchen account without the administrator, the same name twice, or the reset switch without a name and PIN: the backend exits with a message naming the setting. The PIN is never in a message, a log line or the settings' `toString()`.
- **Recovery with a deliberate switch:** `BOOTSTRAP_ADMIN_RESET=true` together with the administrator's name and a new PIN resets that one existing administrator's PIN for that start, then logs a warning to remove the switch (left on, every restart would reset the PIN again). It refuses an account that does not exist or is not an administrator, creates nothing and promotes nobody.
- **It runs before the demo seed** (`@Order(1)`), so with both on the administrator you named is the one that exists. A start with the demo seed on in the `prod` profile now logs a warning that the demo accounts have the public PIN `1234`.
- **One PIN rule.** The issue said the 4 to 8 digit rule lived in the service; it was in `StaffAccountController`. It now lives in `StaffAccountService` (`isValidPin`, `requireValidPin`) and is applied by `create` and `resetPin`, so the admin API (still 400) and the bootstrap cannot drift apart.
- **Settings reach the containers** through `docker-compose.yml`, `docker-compose.hub.yml` and `.env.example` (all empty or `false` by default).

Decisions worth knowing:
- **Deliberate one-start switch for recovery** (the first open question): it is explicit, resets only a named existing administrator and is documented as "remove it afterwards". Without it the settings are inert on a non-empty installation.
- **The bootstrap also creates the kitchen account** (the second open question), because the kitchen screen is the other one a restaurant needs on day one; waiter and cashier accounts are created from Staff accounts after signing in.
- **Validation applies even when accounts exist,** so a typo in a leftover setting shows up instead of staying silent.
- **The check "is the installation empty" is `count() == 0` inside one transaction** with the creation. Two backends starting at the same moment against an empty database could both pass it; the unique staff name makes the second one fail instead of creating a duplicate.

## The other files

- **`staff/bootstrap/StaffBootstrapSettings`** (the five settings; blank means not set), **`StaffBootstrapService`** (the rules above), **`StaffBootstrapRunner`** (reads the environment, runs at startup).
- **`seed/DemoDataStartupRunner`** (the prod warning), **`staff/service/StaffAccountService`** and **`staff/web/StaffAccountController`** (the PIN rule moved).
- **`Documentation/deployment.md`** ("First administrator", "Lost the administrator PIN", "The demo data": the PINs are public, and the settings table), **`Documentation/access-control.md`** (where the first account comes from), **`README.md`**.

## Verification performed

1. **`./mvnw test`: 263 tests, 0 failures** (247 before, plus 16 in `StaffBootstrapServiceTest`, against the real PostgreSQL of Testcontainers). The new tests cover: the administrator and the kitchen account created and able to sign in; nothing created without settings; nothing created or changed when accounts exist; invalid PINs, unpaired values, kitchen without administrator and equal names each stopping the start with the setting named and the PIN absent from the message; nothing created when the kitchen part fails; the reset changing only an existing administrator and refusing a missing or non-admin account; the settings' `toString()` hiding the PINs; the PIN rule shared by the service, `create` and `resetPin`. The "empty installation" cases use the real repository with only `count()` answering 0, because the shared test database already holds accounts.
2. **The container stack** (`docker compose up --build`, throwaway project and port, demo seed off): the first start created exactly `Chef Boss` (ADMIN) and `Main Kitchen` (KITCHEN); both signed in, a wrong PIN got 401, the admin created a waiter and a 2-digit PIN got 400. A restart with the same settings and one with different names created nothing and signed nobody new in. The reset switch changed the PIN (new one 200, old one 401) and logged its warning; a reset naming the kitchen account stopped the start with a stack trace of the `IllegalStateException`; a PIN `12ab` stopped it with `BOOTSTRAP_ADMIN_PIN must be 4 to 8 digits.` The PINs used appeared nowhere in the backend log.
3. **Bootstrap together with the demo seed on a fresh database:** `Chef Boss` was created first, the prod warning was logged, then the four demo accounts were added; all five exist.

Not done, for a decision:
- **A start with an exit instead of a restart loop.** The compose files use `restart: unless-stopped`, so a backend stopped by wrong settings is restarted again and again until `.env` is fixed. That is the existing policy for every startup failure; the message is in `docker compose logs backend` each time.
- **Rotating the administrator PIN from the Staff accounts screen** is unchanged and still the normal way once someone can sign in.
