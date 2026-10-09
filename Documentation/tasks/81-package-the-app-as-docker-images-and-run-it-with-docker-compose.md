# Issue #81: Package the app as Docker images and run it with Docker Compose

## What was done

The app only ran from source. It now ships as two images and a compose file: `docker compose up --build` starts PostgreSQL, the Spring Boot backend and nginx (which serves the React app and forwards `/api` and `/images`), and everything is reachable on one port (default `http://localhost:8088`). Nothing about what the app does changed.

- **Backend image** (`backend/Dockerfile`): built with the Maven wrapper on a JDK 25 image, the jar split into layers (dependencies, loader, application) so a code change rebuilds only the last layer, then run on a JRE 25 image as a non-root user with the `prod` profile. It is configured only by environment variables, so a missing database setting stops it at startup. A health check calls the public `GET /api/guest/settings`, which also proves the database is reachable.
- **Frontend image** (`frontend/Dockerfile`, `nginx.conf`): `npm ci` and `npm run build`, then only `dist` on nginx. One origin for the browser (so the session cookie works and there is no CORS), the client-side routes fall back to `index.html`, `index.html` and `sw.js` are never cached, `/assets/*` is cached for a year, text is compressed, uploads up to 6 MB are allowed, and the backend is re-resolved so a restarted backend is found again.
- **`docker-compose.yml`** at the root: `postgres` (named volume, health check), `backend` (waits for a healthy database; photos in a named volume at `/data/uploads`) and `frontend` (the only published port). `.env.example` documents the settings; `.env` is git-ignored and compose refuses to start without `POSTGRES_PASSWORD`.
- **Demo data is now a switch, not a profile.** The seeder ran only in the `dev` profile, which also turns on the Docker Compose integration, so a `prod` container started with an empty database and no staff account. `app.seed.demo` (set by the dev profile, or `APP_SEED_DEMO=true`) now controls it; it is off by default and the seed is idempotent.

Decisions worth knowing:
- **nginx, not the Spring Boot jar, serves the frontend**: the backend image stays independent of the frontend build, and the cache headers (service worker, hashed assets) are nginx's job.
- **The demo seed is available in the production image, off by default.** Every demo account has PIN 1234, so `Documentation/deployment.md` says it is for demos.
- **The default port is 8088,** because 8080 is where the dev backend runs on the same machine.
- **No health endpoint was added.** Spring Boot Actuator would be a new public URL, and `Documentation/access-control.md` keeps the public list short on purpose; the existing public settings endpoint does the job.

## A bug found on the way

`jspecify` was declared `<optional>true</optional>` in `pom.xml`, so it was left out of the packaged jar and the first container died with `NoClassDefFoundError: org/jspecify/annotations/Nullable`. The same would have happened with the `java -jar` production run in `backend/README.md`, which had never been tried. It is now a normal dependency. The README also named the jar wrongly (`backend-0.0.1-SNAPSHOT.jar`; the pom sets `finalName` `rom-app`).

## The other files

- **`backend/Dockerfile`**, **`frontend/Dockerfile`**, **`frontend/nginx.conf`**, **`.dockerignore`** in both (no `node_modules`, `target`, `dist`, `uploads` or `.git` in the build context), **`docker-compose.yml`**, **`.env.example`**, **`.gitignore`** (`.env`).
- **`DemoDataStartupRunner`** (now `@ConditionalOnProperty`), **`application.properties`**, **`application-dev.properties`**, **`DemoDataSeeder`** (comment); **`DemoDataStartupRunnerConditionTest`**: the runner exists only when the switch is on.
- **`README.md`** ("Run with Docker"), **`backend/README.md`** (jar name), **`Documentation/deployment.md`** (first start, settings, volumes, backups, HTTPS, the images, checks against the containers).

## Verification performed

1. **`docker compose up --build` from the sources, on a fresh volume:** all three services healthy. The pages `/`, `/guest`, `/kitchen`, `/hall` and `/admin/dashboard` all load, as does the PWA manifest (served as `application/manifest+json`).
2. **Headers:** `sw.js` and `index.html` are `no-cache`; `/assets/*.js` is `immutable` and gzip-compressed.
3. **API through nginx:** the public settings answer 200, an anonymous `staff/me` is 401, sign-in works and sets the session cookie.
4. **Backend container:** runs as `app` (not root) with the `prod` profile; the database and backend ports are not published. Without database settings it stops at startup. `docker compose config` stops with a clear message when `POSTGRES_PASSWORD` is missing.
5. **Persistence:** a meal photo was uploaded, served at `/images/...` by nginx, and after `docker compose down` and `up` it, the sign-in and all 8 meals were still there.
6. **Seeding off:** a fresh stack starts with no staff (sign-in is 401).
7. **The repo's own checks against the container stack:** `demo-rehearsal.mjs` 22 of 22, `qa-walk.mjs` (English and Arabic, both themes) 108 of 108.
8. **Sizes and speed:** backend image 283 MB, frontend image 63 MB. After a one-line source change in each app the rebuild takes 39 s (the dependency layers are cached); the first build took about four minutes, mostly the Maven download.
9. **`./mvnw test`: 247 of 247** (the new switch test included).

Not done, for a decision:
- **Publishing the images to a registry and a CI build** (the issue's out of scope).
- **HTTPS** is only documented: put a TLS proxy in front of `frontend` and set `SESSION_COOKIE_SECURE=true`.
- **A first-admin step without the demo seed:** a production database with no demo data has no staff account, and nothing creates one. Real use needs either a bootstrap setting (an admin name and PIN from the environment) or the demo seed and then changing every PIN. That is a follow-up.
