FROM gradle:9.1-jdk25-noble AS build
WORKDIR /workspace
COPY . .
RUN echo "=== Docker context diagnostics ===" && \
    du -sh . && \
    ls -la settings.gradle.kts build.gradle.kts && \
    ls -d shared-kernel remote-render-worker render-module storage-module ai-module 2>&1 && \
    head -30 settings.gradle.kts && \
    echo "=== End diagnostics ==="
RUN gradle :remote-render-worker:bootJar --no-daemon

FROM eclipse-temurin:25-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg curl && rm -rf /var/lib/apt/*
RUN groupadd -g 10002 worker && useradd -u 10002 -g 10002 -d /app worker
WORKDIR /app
COPY --from=build /workspace/remote-render-worker/build/libs/*.jar app.jar
RUN chown -R worker:worker /app
USER worker
EXPOSE 8090
HEALTHCHECK --interval=10s --timeout=3s --retries=3 CMD curl -f http://localhost:8090/healthz || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
