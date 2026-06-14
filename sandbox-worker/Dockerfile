# syntax=docker/dockerfile:1

# -----------------------------------------------------------------------------
# Sandbox Worker — Python execution service
# -----------------------------------------------------------------------------

FROM gradle:9.1-jdk25-noble AS build
WORKDIR /workspace
COPY . .
RUN gradle :sandbox-worker:bootJar --no-daemon

FROM eclipse-temurin:25-jre-jammy

# Create non-root user
RUN groupadd --gid 10003 sandbox && useradd --uid 10003 --gid 10003 sandbox

# Install Python 3
RUN apt-get update && \
    apt-get install -y --no-install-recommends python3 && \
    ln -sf /usr/bin/python3 /usr/bin/python && \
    rm -rf /var/lib/apt/*

WORKDIR /app

# Copy JAR from build stage
COPY --from=build /workspace/sandbox-worker/build/libs/*.jar app.jar

# Create /tmp directory for sandbox execution
RUN mkdir -p /tmp/sandbox && chown -R sandbox:sandbox /tmp/sandbox

# Switch to non-root user
USER sandbox:sandbox

EXPOSE 8091

HEALTHCHECK --interval=10s --timeout=3s --retries=3 --start-period=15s \
    CMD python3 -c "import urllib.request; urllib.request.urlopen('http://localhost:8091/v1/sandbox/healthz')" || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
