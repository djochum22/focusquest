# syntax=docker/dockerfile:1
# FocusQuest in one image: the backend serving the built web app on one origin (see README.md, "Run with
# Docker"). The camera companion stays outside: Docker on macOS cannot reach the camera.

# 1. The web app, built by npm into frontend/dist.
FROM node:24-slim AS web-app
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --silent
COPY frontend/ ./
RUN npm run build --silent

# 2. The boot jar, with the web app from stage 1 packaged in (-PskipFrontendBuild: it is built already).
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /src/backend
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle/ gradle/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --console=plain dependencies > /dev/null
COPY backend/src/ src/
COPY --from=web-app /src/frontend/dist /src/frontend/dist
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain bootJar -PskipFrontendBuild \
    && cp build/libs/focusquest-backend-*-SNAPSHOT.jar /src/focusquest.jar

# 3. The runtime: a JRE and the jar, run as a user without root.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home-dir /app focusquest \
    && mkdir -p /app/data && chown focusquest /app/data
WORKDIR /app
COPY --from=backend /src/focusquest.jar focusquest.jar
USER focusquest
# Inside the container it listens on every interface; docker-compose.yml publishes it on this computer only.
ENV FOCUSQUEST_BIND=0.0.0.0 \
    FOCUSQUEST_PORT=8080 \
    FOCUSQUEST_DATA_DIR=/app/data
VOLUME /app/data
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/focusquest.jar"]
