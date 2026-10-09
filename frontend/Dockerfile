# syntax=docker/dockerfile:1

# ---- build: install from the lock file and make the production bundle ----
# The bundle is the same on every CPU: build it once, natively, whatever the image's target architectures are.
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
WORKDIR /build

# The manifests first, so installing is a cached layer that a source change does not redo.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY . .
RUN npm run build

# ---- run: nginx serves the bundle and forwards /api and /images to the backend ----
FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
