# Issue #83: Publish the Docker images to Docker Hub with GitHub Actions

## What was done

The two images from #81 existed only on the machine that built them. A GitHub Actions workflow now builds both and pushes them to Docker Hub as `osamasado2024/restaurant-orders-backend` and `osamasado2024/restaurant-orders-frontend` (public), for `linux/amd64` and `linux/arm64`, and a second compose file runs the published images so a server needs only that file, an `.env` and Docker. Nothing about the images' contents changed.

- **`.github/workflows/docker-publish.yml`**
  - A tag `vX.Y.Z` pushes `X.Y.Z`, `X.Y` and `latest` (a tag with a hyphen, such as `v1.0.0-rc.1`, gets no `latest`).
  - A push to `master` pushes `edge`.
  - A pull request only builds the images (for `amd64`), never pushes and needs no secrets.
  - The two images are a build matrix with the GitHub Actions layer cache; every action is pinned to a commit SHA like the SonarCloud workflows; the workflow's permissions are `contents: read`.
  - **Without the `DOCKERHUB_TOKEN` secret it still builds and says in a warning that it did not push,** so merging the workflow does not turn `master` red before the secrets exist.
- **`docker-compose.hub.yml`** uses `image:` instead of `build:`, with the same project name, services and volumes as `docker-compose.yml` (so switching between building and pulling keeps the data). `APP_VERSION` in `.env` picks the tag (default `latest`); pin a release for anything that matters.
- **One place for the names:** the `env` block at the top of the workflow (`DOCKERHUB_NAMESPACE`, `IMAGE_PREFIX`); the compose file reads the same namespace from `DOCKERHUB_NAMESPACE` in `.env`.
- **Both Dockerfiles** now start their build stage `FROM --platform=$BUILDPLATFORM`: the jar and the bundle are the same on every CPU, so they are built once natively and only the small final stage is made per architecture. Without this the `arm64` Maven build would run under emulation and take far longer.

Decisions worth knowing:
- **`edge` on every push to `master`** (an open question in the issue, answered yes) so the newest build can be tried; releases are the tags.
- **The tag names the images only.** The jar (`0.0.1-SNAPSHOT`) and the frontend package keep their own numbers.
- **The Docker Hub repository descriptions are filled in by hand once.** Changing a description needs a more powerful token than the Read & Write one the workflow uses.
- **The workflow does not push attestations** (`provenance` and `sbom` off), which keeps the Docker Hub tag list free of "unknown/unknown" entries; SBOMs and signing were out of scope.

## The other files

- **`Documentation/deployment.md`**: "Running the published images" (the tags and what they mean) and "Publishing a release" (the one-time setup of the token and the two secrets, cutting a tag, checking the result, and pushing by hand in an emergency).
- **`.env.example`** (`APP_VERSION`), **`README.md`** (a link to the published images).

## Verification performed

Docker Hub itself could not be pushed to from here (no credentials), so the publish was rehearsed against a throwaway local registry with the same commands the workflow runs.

1. **`actionlint`** (the workflow linter) on all workflows: clean.
2. **Both images built for `linux/amd64,linux/arm64` with Buildx and pushed** to the local registry as `0.1.0` and `latest`; `docker buildx imagetools inspect` lists both platforms for each, and running the `arm64` images shows `aarch64`, the non-root user `app` and nginx 1.29.
3. **The app run only from the pulled images** (local copies deleted first, then `docker compose -f docker-compose.hub.yml pull` and `up -d`, nothing built): all three services healthy, `/` 200, the public settings 200, sign-in 200, and **`demo-rehearsal.mjs` 22 of 22** against it.
4. The existing compose file still builds the same images (both Dockerfiles built fine with the new `--platform` line).

Issues found along the way:
- **A wrong emulation image name** in my first draft of the manual-push instructions (`tonistiiv`; the real one is `tonistiigi/binfmt`). Caught when the command failed, and fixed in the docs.

Not done, for a decision (needs you, on GitHub and Docker Hub):
- **Add the two secrets** `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` (a Read & Write access token) and **push the first tag** (`v0.1.0`). Until then nothing is published; the workflow's first real run on Docker Hub is the one thing not rehearsed.
- **Give the two repositories a short description** on Docker Hub.
