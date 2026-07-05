# Optimized Dockerfile - no javacv, layered build
FROM gradle:9.1-jdk25-noble AS deps
WORKDIR /workspace
COPY build.gradle* settings.gradle* ./
COPY gradle gradle
COPY gradlew .
RUN chmod +x gradlew
COPY render-module/build.gradle* render-module/
COPY platform-app/build.gradle* platform-app/
COPY shared-kernel/build.gradle* shared-kernel/
COPY ai-module/build.gradle* ai-module/
COPY storage-module/build.gradle* storage-module/
COPY extension-module/build.gradle* extension-module/
COPY entitlement-module/build.gradle* entitlement-module/
COPY billing-module/build.gradle* billing-module/
COPY quota-billing-module/build.gradle* quota-billing-module/
COPY outbox-event-module/build.gradle* outbox-event-module/
COPY remote-render-worker/build.gradle* remote-render-worker/
RUN ./gradlew dependencies --no-daemon 2>/dev/null || true

FROM deps AS build
COPY . .
RUN ./gradlew :platform-app:bootJar --no-daemon -x test \
    && JAR=$(ls platform-app/build/libs/*.jar | grep -v plain | head -n1) \
    && test -n "$JAR" \
    && cp "$JAR" /workspace/app.jar

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/*
RUN groupadd --gid 10001 spring && useradd --uid 10001 --gid 10001 spring
USER spring:spring
COPY --from=build /workspace/app.jar /app/app.jar
EXPOSE 8080
ENV JAVA_OPTS=""
ENV SPRING_PROFILES_ACTIVE="preview"
HEALTHCHECK --interval=10s --timeout=3s --retries=3 --start-period=30s CMD curl -f http://localhost:8080/healthz || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
