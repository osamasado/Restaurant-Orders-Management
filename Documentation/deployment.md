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

## Running the published images (nothing to build)

The images are public on Docker Hub: [`osamasado2024/restaurant-orders-backend`](https://hub.docker.com/r/osamasado2024/restaurant-orders-backend) and [`osamasado2024/restaurant-orders-frontend`](https://hub.docker.com/r/osamasado2024/restaurant-orders-frontend), for `linux/amd64` and `linux/arm64`. `docker-compose.hub.yml` runs them, so a server only needs that file, an `.env` and Docker:

```
curl -O https://raw.githubusercontent.com/osamasado/Restaurant-Orders-Management/master/docker-compose.hub.yml
curl -o .env https://raw.githubusercontent.com/osamasado/Restaurant-Orders-Management/master/.env.example
# edit .env: set POSTGRES_PASSWORD, and APP_VERSION to a release such as 0.1.0
docker compose -f docker-compose.hub.yml up -d
```

| Tag | Means |
|---|---|
| `0.1.0` | exactly that release; **pin one of these** for anything that matters |
| `0.1` | the newest `0.1.x` |
| `latest` | the newest release (never a pre-release such as `v1.0.0-rc.1`) |
| `edge` | the newest build of `master`, not a release; for trying things out |

`APP_VERSION` in `.env` chooses the tag (default `latest`). To update, change it and run `docker compose -f docker-compose.hub.yml up -d` again (it pulls the new images). The file uses the same project name and volumes as `docker-compose.yml`, so switching between building and pulling keeps the database and the photos. Do not rename the services: the frontend image reaches the backend as `backend`.

## Publishing a release

`.github/workflows/docker-publish.yml` builds both images and pushes them. Nobody needs to build on a laptop.

| Event | What happens |
|---|---|
| a tag `vX.Y.Z` is pushed | both images are built and pushed as `X.Y.Z`, `X.Y` and `latest` (a tag with a hyphen, such as `v1.0.0-rc.1`, gets no `latest`) |
| a push to `master` | both images are built and pushed as `edge` |
| a pull request | both images are only built (for `amd64`), nothing is pushed, no secrets are needed |

**One-time setup**

1. On hub.docker.com make an access token (Account settings, Personal access tokens, permission **Read & Write**). Docker Hub creates the two repositories on the first push; make them public.
2. In the GitHub repository (Settings, Secrets and variables, Actions) add the secrets `DOCKERHUB_USERNAME` (`osamasado2024`) and `DOCKERHUB_TOKEN` (the token). Without them the workflow still builds the images and says in a warning that it did not push.
3. On Docker Hub, give each repository a short description and a link to this repository (the workflow does not touch them: changing a description needs a more powerful token than the one above).

**Cutting a release**

```
git switch master && git pull
git tag v0.1.0
git push origin v0.1.0
```

Watch the "Docker images" workflow, then check the tags on Docker Hub and `docker buildx imagetools inspect osamasado2024/restaurant-orders-backend:0.1.0` (it should list `linux/amd64` and `linux/arm64`). The version in the tag names the images only; the jar (`0.0.1-SNAPSHOT`) and the frontend package keep their own numbers.

**The names** are set once, in the `env` block at the top of the workflow (`DOCKERHUB_NAMESPACE`, `IMAGE_PREFIX`); `docker-compose.hub.yml` takes the same namespace from `DOCKERHUB_NAMESPACE` in `.env` (default `osamasado2024`).

**Pushing by hand** (an emergency, or the very first publish before the secrets exist):

```
docker login -u osamasado2024            # paste the access token as the password
docker buildx build --platform linux/amd64,linux/arm64 \
  -t osamasado2024/restaurant-orders-backend:0.1.0 -t osamasado2024/restaurant-orders-backend:latest \
  --push ./backend
docker buildx build --platform linux/amd64,linux/arm64 \
  -t osamasado2024/restaurant-orders-frontend:0.1.0 -t osamasado2024/restaurant-orders-frontend:latest \
  --push ./frontend
```

Add `--provenance=false --sbom=false` to keep the tag list free of extra "unknown/unknown" entries. If your Docker uses the default builder and refuses a multi-platform build, run `docker buildx create --use` once; on a Linux machine without emulation, `docker run --privileged --rm tonistiigi/binfmt --install arm64` adds it.

