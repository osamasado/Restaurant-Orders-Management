# Running the app with Docker

`docker-compose.yml` at the repository root starts the whole app: PostgreSQL, the Spring Boot backend and nginx, which serves the React app and forwards the API. Only nginx is reachable from outside.

```
browser ──> frontend (nginx, port 80 inside, APP_PORT outside)
              ├─ /           the React app (index.html for every screen's path)
              ├─ /api/...    ──> backend:8080
              └─ /images/... ──> backend:8080   (meal and raw-material photos)
            backend ──> postgres:5432           (neither is published)
```

The browser sees one origin, so the staff session cookie works and there is no CORS.

## First start

```
cp .env.example .env     # set POSTGRES_PASSWORD (compose refuses to start without it)
docker compose up --build -d
```

The backend waits for a healthy database, the web server waits for a healthy backend, and the schema is created by Flyway on the first start. Open `http://localhost:8088` (change `APP_PORT` in `.env`).

A new database is empty and has no staff account, so nobody can sign in. For a demo set `APP_SEED_DEMO=true` before the first start: the demo menu, raw materials, tables and staff (admin `O. Sado`, kitchen `M. Behr`, PIN `1234` for all) are loaded, and each table gets a pairing code (Tables & devices). The seed is idempotent: leaving it on only tops up what is missing and never overwrites an admin's changes. **It is for demos.** For real use turn it off and change every PIN in Staff accounts.

## Settings (`.env`)

| Variable | Default | Meaning |
|---|---|---|
| `POSTGRES_PASSWORD` | none, required | The database password. Compose stops with a message if it is missing |
| `POSTGRES_DB`, `POSTGRES_USER` | `restaurant_orders` | The database and its user |
| `APP_PORT` | `8088` | The port the app is published on |
| `SESSION_COOKIE_SECURE` | `false` | Set `true` when the app is served over HTTPS, so the session cookie is never sent over plain HTTP |
| `APP_SEED_DEMO` | `false` | Load the demo data (see above) |

The backend container itself reads `SPRING_PROFILES_ACTIVE=prod`, `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `UPLOAD_DIR` (`/data/uploads`) and `SESSION_COOKIE_SECURE`. The `prod` profile has no defaults for the database or the upload folder on purpose, so a missing variable stops the backend at startup instead of quietly using a wrong database.

## Volumes and backups

| Volume | Holds |
|---|---|
| `pgdata` | The database |
| `uploads` | The meal and raw-material photos (`/data/uploads`) |

`docker compose down` keeps both; `docker compose down -v` deletes them. Back up the database with

```
docker compose exec postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' > backup.sql
```

and the photos by copying the `uploads` volume (for example `docker run --rm -v restaurant-orders_uploads:/data -v "$PWD":/out alpine tar czf /out/uploads.tgz -C /data .`).

## HTTPS

The images speak plain HTTP. For a real deployment put a TLS-terminating proxy (Caddy, Traefik, a cloud load balancer) in front of the `frontend` service, forward `X-Forwarded-Proto`, and set `SESSION_COOKIE_SECURE=true`.

## The images

- **Backend** (`backend/Dockerfile`): built with the Maven wrapper on a JDK 25 image, the jar split into layers (dependencies, loader, application) so a code change rebuilds only the last layer, and run on a JRE 25 image as a non-root user. Tests are not run in the image build (they need Docker themselves); run them with `./mvnw test`. A health check calls the public `GET /api/guest/settings`, which also proves the database is reachable.
- **Frontend** (`frontend/Dockerfile`): `npm ci` and `npm run build` on a Node 24 image, then only the `dist` folder and `nginx.conf` on an nginx image. nginx never caches `index.html` and `sw.js` (so an update reaches installed apps), caches `/assets/*` for a year (the names are hashed), compresses text, and allows 6 MB uploads.
- Both have a `.dockerignore`, so the build context holds no `node_modules`, `target`, `dist`, `uploads` or `.git`.

## Checks against the container stack

The browser checks in `frontend/scripts/` take the address of any stack, so they run against the containers too (use a throwaway project, never one with real data, because they place and cancel orders):

```
BASE=http://localhost:8088 API=http://localhost:8088/api node scripts/demo-rehearsal.mjs
BASE=http://localhost:8088 API=http://localhost:8088/api node scripts/qa-walk.mjs
```

Both need `APP_SEED_DEMO=true`.
