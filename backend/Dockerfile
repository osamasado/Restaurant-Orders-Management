# syntax=docker/dockerfile:1

# ---- build: compile the jar and split it into layers (dependencies change rarely, the application often) ----
# The jar is the same on every CPU, so it is built once on the machine that runs the build (not under emulation)
# even when the image is made for several architectures.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build

# The wrapper and the pom first, so the dependency download is a cached layer that a source change does not redo.
COPY .mvn .mvn
# --chmod: git on Windows does not keep the executable bit of mvnw, and a Linux checkout may not have it either.
COPY --chmod=755 mvnw ./
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

# Tests need Docker (Testcontainers) and run in CI, not here.
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/rom-app.jar extract --layers --destination /build/extracted

# ---- run: a JRE only, as a user that owns nothing but the upload folder ----
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S -G app -h /app app \
 && mkdir -p /data/uploads && chown -R app:app /data
WORKDIR /app
USER app

COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

# The prod profile has no defaults on purpose: DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD and UPLOAD_DIR
# must be set, or the application refuses to start. See the compose file and Documentation/deployment.md.
ENV SPRING_PROFILES_ACTIVE=prod \
    UPLOAD_DIR=/data/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
VOLUME /data/uploads
EXPOSE 8080

# /api/guest/settings is public and reads the database, so a 200 means the server is up and the database is reachable.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/api/guest/settings || exit 1

ENTRYPOINT ["java", "-jar", "rom-app.jar"]
