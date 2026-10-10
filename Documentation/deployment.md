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

A new database is empty and has no staff account, so nobody can sign in until you create the first administrator (next section), or load the demo data (the section after it).

## First administrator

For real use, give the backend the first administrator in `.env` before the first start:

```
BOOTSTRAP_ADMIN_NAME=Your Name
BOOTSTRAP_ADMIN_PIN=<4 to 8 digits>
BOOTSTRAP_KITCHEN_NAME=Kitchen          # optional, together with its PIN
BOOTSTRAP_KITCHEN_PIN=<4 to 8 digits>
```

On the first start of an **empty** installation the backend creates that administrator, and the kitchen account when both kitchen values are set (both or neither, in one transaction). Open `/admin` (the app has no separate login page: each staff screen asks for the name and PIN) and sign in, create the waiter and cashier accounts in Staff accounts, **then remove the two PIN lines from `.env`** and restart (`docker compose up -d`). The log says so after the accounts are created.

What it will and will not do:

- **It only creates, and only into an empty installation.** If any staff account exists it creates nothing and changes nothing, so a leftover setting can never overwrite or reset a real account. It says so in one log line.
- **No settings, no accounts.** Without `BOOTSTRAP_ADMIN_NAME` and `BOOTSTRAP_ADMIN_PIN` an empty installation starts normally and logs one line explaining how to create the first administrator.
- **Wrong settings stop the start.** A name without a PIN (or the reverse), a PIN that is not 4 to 8 digits, a kitchen account without the administrator, or the same name twice makes the backend exit with a message that names the setting. The PIN is never part of a message and never logged. The PIN rule is the one the Staff accounts screen uses.
- **It runs before the demo data**, so with both set the administrator you named is the one that exists (the demo seed skips names that are taken).

### Lost the administrator PIN

If every administrator PIN is lost (the sign-in screen locks a name for 15 minutes after five wrong tries, which does not help here), reset one deliberately:

1. In `.env` set `BOOTSTRAP_ADMIN_NAME` to the existing administrator, `BOOTSTRAP_ADMIN_PIN` to a NEW PIN, and `BOOTSTRAP_ADMIN_RESET=true`.
2. `docker compose up -d` (the backend restarts and sets the PIN).
3. Sign in, then **remove all three lines again** and restart.

The switch works for **one start only**: leave it on and every restart would reset that PIN again, so the log warns you to remove it. It resets an existing administrator and nothing else: it never creates an account, never promotes anyone and refuses with a message if the name does not exist or is not an administrator. Without the switch these settings never touch an existing account.

## The demo data

For a demo set `APP_SEED_DEMO=true` before the first start: the demo menu, raw materials, tables and staff (admin `O. Sado`, kitchen `M. Behr`, waiter `L. Adler`, cashier `T. Nowak`, PIN `1234` for all) are loaded, and each table gets a pairing code (Tables & devices). The seed is idempotent: leaving it on only tops up what is missing and never overwrites an admin's changes.

**The demo PINs are public: they are in this repository.** Never leave the demo data on for an installation anyone else can reach. For real use turn it off and change every PIN in Staff accounts. The backend logs a warning at every start when the demo data is on together with the `prod` profile (which is what the containers run).

## Settings (`.env`)

| Variable | Default | Meaning |
|---|---|---|
| `POSTGRES_PASSWORD` | none, required | The database password. Compose stops with a message if it is missing |
| `POSTGRES_DB`, `POSTGRES_USER` | `restaurant_orders` | The database and its user |
| `APP_PORT` | `8088` | The port the app is published on |
| `SESSION_COOKIE_SECURE` | `false` | Set `true` when the app is served over HTTPS, so the session cookie is never sent over plain HTTP |
| `APP_SEED_DEMO` | `false` | Load the demo data (see [The demo data](#the-demo-data); its PINs are public) |
| `BOOTSTRAP_ADMIN_NAME`, `BOOTSTRAP_ADMIN_PIN` | empty | Create the first administrator of an empty installation (see [First administrator](#first-administrator)); remove the PIN afterwards |
| `BOOTSTRAP_KITCHEN_NAME`, `BOOTSTRAP_KITCHEN_PIN` | empty | Also create a kitchen account in the same step; both or neither |
| `BOOTSTRAP_ADMIN_RESET` | `false` | `true` for one start resets the named administrator's PIN (see [Lost the administrator PIN](#lost-the-administrator-pin)) |

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
- **App** (`Dockerfile.combined`, built from the repository root): both in ONE image. The React build is copied into the Spring Boot jar's static folder and the backend serves it (every screen path answers with `index.html`, hashed assets are cached for a year, `index.html` and `sw.js` never), together with the API and the meal photos, so there is one process and no nginx. It exists for hosts that run a single service, such as the free Render plan. The jar's `/version.txt` holds the release the image was built for. The JVM settings are tuned for 512 MB (`-XX:+UseSerialGC -Xss512k`); the backend honours the host's `PORT`. The backend and frontend images and both compose files are unchanged by it.
- All three have a `.dockerignore`, so the build context holds no `node_modules`, `target`, `dist`, `uploads` or `.git`.

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

`.github/workflows/docker-publish.yml` builds the three images (backend, frontend and the single `app` image) and pushes them. Nobody needs to build on a laptop.

| Event | What happens |
|---|---|
| a tag `vX.Y.Z` is pushed | all images are built and pushed as `X.Y.Z`, `X.Y` and `latest` (a tag with a hyphen, such as `v1.0.0-rc.1`, gets no `latest`) |
| a push to `master` | all images are built and pushed as `edge` |
| a pull request | all images are only built (for `amd64`), nothing is pushed, no secrets are needed |

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

## Deploying to Render (free demo)

[Render](https://render.com) runs the single `app` image (`osamasado2024/restaurant-orders-app`) as one web service next to a managed PostgreSQL database, at an HTTPS address. This is the **free demo** layout; the limits below are Render's free-plan limits as of October 2026, so check [render.com/pricing](https://render.com/pricing) before relying on them.

| Free-plan fact | What it means for the demo |
|---|---|
| The service sleeps after 15 minutes without traffic and, by Render's own figure, takes about a minute to wake (the app itself needs about 50 s to start on a 0.5 CPU instance, measured in Docker; the wake-up on Render has not been timed) | The first visit after a quiet period is slow. A kitchen screen left open keeps polling, so it keeps the service awake while it is open |
| Its files are erased on every sleep and deploy, but the database is kept | Photos an admin uploaded are lost (the meal then shows no picture until it is uploaded again). The demo illustrations are copied back at every start, because the seed checks that each demo image file exists (release 0.2.0 did not, and showed broken images after a redeploy; fixed in the release after it) |
| The server accepts requests while the demo data is still being loaded at a start | For a few seconds after a wake-up the menu can be empty; reload |
| 750 free instance hours per month for the whole workspace | One service awake all month fits (about 744 hours); a second always-on service would not, which is why the app is one service |
| The free database **expires 30 days after it was created** (14 more days to upgrade, then it is deleted), 1 GB, no backups | Everything in it is demo data. To keep going, upgrade the database or create a new one (the schema and demo data are created again at the first start) |
| 512 MB of memory | Measured in Docker with `--memory=512m --cpus=0.5`: about 280 MB used after the full demo rehearsal, no restarts |

**The demo accounts are public** (PIN `1234`, in this repository). Do not put real data on a demo. Your own administrator (`BOOTSTRAP_ADMIN_NAME` and `BOOTSTRAP_ADMIN_PIN`, see "First administrator") is created before the demo data, so you can sign in with a PIN only you know.

### One-time setup

1. **Render account**, and connect your GitHub account to it.
2. **Blueprint:** in the dashboard choose New, Blueprint, and pick this repository. Render reads `render.yaml` and creates the web service `restaurant-orders` and the database `restaurant-orders-db` in Frankfurt. It asks for the two `sync: false` values: `BOOTSTRAP_ADMIN_NAME` and `BOOTSTRAP_ADMIN_PIN` (4 to 8 digits).
3. Wait for the first deploy (it pulls `restaurant-orders-app:latest`, so a release must have been published, see "Publishing a release"). The service's address is shown at the top of its page, such as `https://restaurant-orders.onrender.com`.
4. **The deploy hook:** on the service, Settings, Deploy Hook: copy the URL. It contains a secret key.
5. **In GitHub** (Settings, Environments) create an environment named `production`. In it add the **secret** `RENDER_DEPLOY_HOOK` (the URL from step 4) and the **variable** `RENDER_PUBLIC_URL` (the address from step 3). To approve every deployment by hand, add yourself under "Required reviewers".

### How a release reaches Render

`.github/workflows/deploy-render.yml`:

1. You push a tag `vX.Y.Z`. The "Docker images" workflow publishes `restaurant-orders-app:X.Y.Z` (and the other two images).
2. When that workflow succeeds, "Deploy to Render" starts (after your approval, if you required one). It checks the image exists on Docker Hub, then calls the deploy hook with that exact tag (`imgURL=docker.io/osamasado2024/restaurant-orders-app:X.Y.Z`, never `latest`).
3. It then waits, up to 15 minutes, until `GET /version.txt` on the public address returns `X.Y.Z` (the old version keeps answering until the new one is ready, so this proves the new one is live) and `/` and `/api/guest/settings` answer 200. If not, the workflow ends red.

Pre-release tags such as `v1.0.0-rc.1` are not deployed. Two deployments never run at once.

### Rolling back

Actions, Deploy to Render, Run workflow, and enter an older version such as `0.1.0`. It deploys that image and checks it the same way. (The database is not rolled back: Flyway migrations only move forward, so roll back only to a version whose schema the database still matches.)

### Settings Render needs

`render.yaml` sets all of these; they are listed so you can change them in the dashboard.

| Variable | Value on Render | Meaning |
|---|---|---|
| `PORT` | set by Render (10000) | The port the app listens on; without it the app uses 8080 |
| `DATABASE_HOST`, `DATABASE_PORT`, `DATABASE_NAME`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | from the database (`fromDatabase`) | The connection, in the parts Render hands out. The backend builds the JDBC URL from them; `DATABASE_URL` (a full JDBC URL) still works and wins when set |
| `SESSION_COOKIE_SECURE` | `true` | Render terminates HTTPS, so the session cookie is only sent over it |
| `APP_SEED_DEMO` | `true` | Load the demo menu, tables and staff at every start (see the demo warning above) |
| `BOOTSTRAP_ADMIN_NAME`, `BOOTSTRAP_ADMIN_PIN` | entered in the dashboard | Your own administrator |

The image already sets `SPRING_PROFILES_ACTIVE=prod`, `UPLOAD_DIR=/data/uploads` and the JVM options.

### Going beyond the demo

The free plan is for showing the app. For a restaurant that really uses it: switch the database to a paid plan (it is kept and backed up), the web service to a paid instance (it never sleeps; a 512 MB instance is the smallest, a 2 GB one gives the JVM more room), add a persistent disk mounted at `/data/uploads` so photos survive deploys (a service with a disk runs one instance and stops briefly while deploying), set `APP_SEED_DEMO` to `false` and use the "First administrator" settings. In `render.yaml` that is `plan:` on the service and the database and a `disk:` block; the workflow does not change.

### Measured on the live deployment

From the first release deployed through the workflow (version 0.2.1, 10 October 2026, Frankfurt, free plan):

| What | Result |
|---|---|
| The deploy hook accepted the new image tag | in 2 seconds |
| From the hook call until `/version.txt` returned the new version and `/` and `/api/guest/settings` answered 200 | **2 minutes 34 seconds** (the workflow ran 2 minutes 37 seconds in all); the old version kept answering until then, so there was no gap |
| The demo rehearsal (`frontend/scripts/demo-rehearsal.mjs`) against the Render address | **22 of 22 checks**; the order reached the kitchen screen in 2.7 s, the hall board in 3.8 s, "Ready" on the board in 4.8 s, "Served" for the guest in 4.9 s; a cancelled order reached the kitchen in 1.9 s and left the hall board in 2.0 s; a meal toggled by the kitchen changed on an open table device in 5.4 s and 5.3 s (the limit is 10 s) |
| The session cookie | `Secure; HttpOnly; SameSite=Lax` |
| Every meal picture of the guest menu | served as `image/png` after the release that restores the demo images |

Not timed: the first deploy from the Blueprint and the wake-up of a sleeping service.

### Limits of this setup

- The memory figure above comes from Docker with Render's free memory and CPU, not from Render's own metrics (look at the service's Metrics tab for those).
- No custom domain, staging environment or monitoring (out of scope).
