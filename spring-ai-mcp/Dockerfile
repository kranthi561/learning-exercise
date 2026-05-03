# syntax=docker/dockerfile:1.7

# ---------- Stage 1: build ----------
# Uses the Eclipse Temurin Java 21 image with Maven preinstalled. The build
# stage downloads dependencies into a layer that is cached separately from
# the source so iterative rebuilds are fast.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Copy only pom.xml first to maximise dependency-layer cache reuse.
COPY pom.xml ./
RUN mvn -B -e -ntp dependency:go-offline

# Now copy sources and build the fat jar.
COPY src ./src
RUN mvn -B -e -ntp clean package -DskipTests \
    && mv target/spring-ai-mcp-*.jar target/app.jar

# ---------- Stage 2: runtime ----------
# Slim JRE-only image. Adds curl for healthcheck, then drops to a non-root user.
FROM eclipse-temurin:21-jre

# Default folders the watcher reads/writes inside the container. They are
# expected to be bind-mounted by docker-compose so images survive restarts.
ENV SOURCE_FOLDER=/app/source-images \
    DESTINATION_FOLDER=/app/uploaded-images \
    REEL_TEMP_FOLDER=/app/reel-temp \
    SERVER_PORT=8081

WORKDIR /app

# eclipse-temurin:21-jre resolves to Ubuntu 24.04 (noble) which ships
# libheif 1.17.6 in its default repos — no PPA needed.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates ffmpeg libheif-examples \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home /app --shell /usr/sbin/nologin app \
    && mkdir -p /app/source-images /app/uploaded-images /app/reel-temp \
    && chown -R app:app /app

COPY --from=build --chown=app:app /build/target/app.jar /app/app.jar

USER app

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -fsS "http://localhost:${SERVER_PORT}/actuator/health" || curl -fsS "http://localhost:${SERVER_PORT}/" || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/app.jar"]
