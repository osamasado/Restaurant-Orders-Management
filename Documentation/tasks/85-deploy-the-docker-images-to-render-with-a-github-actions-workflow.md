# Issue #85: Deploy the app to Render with a GitHub Actions workflow (free demo first)

## What was done

The app can now run on Render's **free plan** as a demo, and a GitHub Actions workflow deploys every release to it. The free plan decided the design: it cannot run a private backend behind a public frontend (free services receive no private traffic) and its 750 free hours a month fit one always-awake service, not two. So the deployment is **one web service from one image** that contains both the backend and the React app, plus a free PostgreSQL database.

- **`Dockerfile.combined`** (new, repository root) builds the React app, copies it into the Spring Boot jar's static folder and runs the jar (JRE 25, non-root, layered). The image is published as `osamasado2024/restaurant-orders-app` by the existing publish workflow (matrix entry `app`; tags `X.Y.Z`, `X.Y`, `latest`, `edge`). It records its release in `/version.txt`. The backend and frontend images and both compose files are unchanged.
- **The backend serves the app** (`web/SpaConfig`, `web/SpaPaths`): only when the build is in the jar. Every screen path (`/guest`, `/kitchen`, `/hall`, `/admin`, `/admin/**`) answers with `index.html`; hashed assets are cached for a year, `index.html`, `sw.js`, `version.txt` and the manifest never; the manifest gets `application/manifest+json`. These files and paths are public (GET and HEAD) in `SecurityConfig`, with everything else exactly as before. Responses are compressed.
- **`PORT`** is honoured (`server.port=${PORT:8080}`), and **the database can be given in parts** (`DATABASE_HOST`, `DATABASE_PORT`, `DATABASE_NAME` with the existing `DATABASE_USERNAME` and `DATABASE_PASSWORD`) because Render hands them out that way. `DATABASE_URL` still works and wins; with neither, the start still fails loudly.
- **`render.yaml`**: the free web service from the Docker Hub image (Frankfurt, health check `/api/guest/settings`, `SESSION_COOKIE_SECURE=true`, `APP_SEED_DEMO=true`), the free database, the database values passed with `fromDatabase`, and the two `BOOTSTRAP_ADMIN_*` values as `sync: false` (entered in the dashboard, never committed). It reuses #86 for the first administrator.
- **`.github/workflows/deploy-render.yml`**: runs when "Docker images" succeeds for a `vX.Y.Z` tag, or by hand with a version (which is also the rollback). It checks the image exists on Docker Hub, calls the deploy hook with that exact tag, then waits up to 15 minutes for `/version.txt` to return that version and for `/` and `/api/guest/settings` to answer 200, and fails otherwise. Permissions are none, a concurrency group stops overlapping deploys, the environment is `production` (an approval can be required), and untrusted text goes through environment variables. It uses no third-party action, so there is nothing to pin.
- **`Documentation/deployment.md`** has "Deploying to Render (free demo)" (the free-plan limits, one-time setup, how a release arrives, rollback, every setting, going beyond the demo, what is not verified). **`access-control.md`** lists the new public paths; **`README.md`** links to it. I also corrected an earlier line in the docs that said to sign in at `/login`: the app has no such page, each staff screen asks for the name and PIN.

Decisions worth knowing:
- **One service, not two.** The issue proposed a public frontend plus a private backend; that needs paid plans (and two always-on services would exceed the free hours). The two-image layout stays for Docker Compose.
- **`/version.txt` is how the workflow knows the right version is live.** The old version answers until the new one is ready, so "200 OK" alone would have passed too early.
- **Demo data on, on purpose.** Files are erased when a free service sleeps, so the demo seed puts the menu and photos back at every start. The demo PINs are public; the docs say so, and the prod-profile warning from #86 is logged at every start. Your own administrator comes from the bootstrap settings.
- **Photos are lost on sleep and deploy** (no disk on the free plan). A paid plan with a 1 GB disk is the documented next step.
- **The free database expires after 30 days** (14 more to upgrade). Documented; recreating it needs no code change.

## The other files

- **`Dockerfile.combined.dockerignore`** (keeps `.git`, `node_modules`, `Documentation` and the like out of the build context).
- **`backend/.../web/SpaPaths.java`**, **`SpaConfig.java`**; **`SecurityConfig.java`**; **`application.properties`**, **`application-prod.properties`**.
- **`.github/workflows/docker-publish.yml`** (the `app` matrix entry, the build argument and the new path filter).
- **Tests:** `web/SpaServingTest`, `web/SpaConfigConditionTest`, `config/ProdSettingsTest`, and a fake build in `src/test/resources/spa-test/`.

## Verification performed

1. **`./mvnw test`: 276 tests, 0 failures** (263 before, 13 new): the start page, files and every screen path public and cached as described (including HEAD); the API and any other path still 401; the config only active when the build is in the jar; the database URL from a full URL, from parts, with the default port, URL winning, and failing with neither; `PORT` and its default.
2. **The combined image under Render's free limits** (`docker run --memory=512m --cpus=0.5`, `PORT=10000`, database given in parts, `SESSION_COOKIE_SECURE=true`, demo seed and a bootstrap admin): ready after 50 s, about 265 MB at start and 280 MB after the rehearsal, no restart or out-of-memory kill. Screens, assets, `/version.txt` (the build argument), the manifest type, gzip and the `Secure; HttpOnly; SameSite=Lax` cookie were checked with `curl`; the bootstrap admin signed in.
3. **`demo-rehearsal.mjs` against the combined image: 22 of 22**, hand-offs to the kitchen and hall board in 1.6 to 5.4 s.
4. **Docker Compose (`docker-compose.yml`) still works** after the backend changes: the stack built and started, the same rehearsal passed 22 of 22.
5. **The workflow's shell steps ran locally:** the version step (a plain version, a leading `v`, a bad input failing on a manual run, a pre-release skipped on an automatic run), the Docker Hub check (a missing image fails with a clear message), the deploy hook (against a fake server, the request was `POST /deploy/srv-abc?key=…&imgURL=docker.io%2Fosamasado2024%2Frestaurant-orders-app%3A0.2.0`), a missing secret failing with its name, and the wait step passing for the live version and failing after its deadline for another one. The YAML of all three workflows and `render.yaml` parses.

Bugs the checks found along the way (root cause, fix):
- **HEAD requests got 401** (the rules named GET only); HEAD is now allowed for the app's files and screens, and tested.
- **The manifest was served as `application/octet-stream`;** a media-type setting on Spring MVC did not reach the static handler, so the servlet container's mapping is set instead.
- **The deploy step needed `jq`,** which is not guaranteed; the two characters that need encoding are encoded in bash.

Not done, for a decision (needs you, on Render and GitHub):
- **The first real deployment.** Create the Blueprint, copy the deploy hook, add the `production` environment (secret `RENDER_DEPLOY_HOOK`, variable `RENDER_PUBLIC_URL`), push a tag and watch the workflow. `render.yaml` and the hook's exact behaviour (the `imgURL` parameter, the free-plan cold start) could not be run from here; the Render CLI was not available to validate the Blueprint.
- **The workflow only runs from `master`** (a `workflow_run` and manual runs use the default branch's copy), so it can first run after this is merged and a release tag is pushed.
- **A release must exist first** (`restaurant-orders-app:latest` is created by the next `vX.Y.Z` tag), because the Blueprint pulls `latest` the first time.
- **The follow-ups from the issue** (a second `edge` service, a custom domain, monitoring, object storage for photos) are not part of the demo.

## Found after the first deployment

Meal images were broken on the live site after the first redeploy. The demo seed attached an illustration only when a meal had no image path at all, and the database keeps the path while a free Render service loses its files on every sleep and deploy, so the path pointed at a file that no longer existed (the published `0.2.0` image returned 404 for `/images/meals/pumpkin-soup.png` against a database that had already been seeded). The seed now also copies its own illustration again when that file is missing, and never touches an image an admin uploaded (another file name). Tests: `DemoDataSeederTest` (an erased demo image comes back; an admin's own path is left alone). Checked in a container: start, destroy the container, start a new one on the same database, and all 39 images are served again.

Also noticed: the server accepts requests (the health check passes) before the demo seed has finished, so for a few seconds after a start the menu can be empty. It is noted in `Documentation/deployment.md`; making the start wait for the seed would be a separate change.
