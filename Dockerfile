# syntax=docker/dockerfile:1

# -----------------------------------------------------------------------------
# Stage 1: Build frontend (Vue 3 + Vite)
# -----------------------------------------------------------------------------
FROM node:22-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ .
RUN npm run build

# -----------------------------------------------------------------------------
# Stage 2: Build backend (Gradle 9.1 + JDK 25)
# -----------------------------------------------------------------------------
FROM gradle:9.1-jdk25-noble AS backend-build
WORKDIR /workspace
COPY --from=frontend-build /app/frontend/dist /workspace/platform-app/src/main/resources/static
COPY . .
RUN chmod +x gradlew \
    && ./gradlew :platform-app:bootJar --no-daemon -x test \
    && JAR=$(ls platform-app/build/libs/*.jar | grep -v plain | head -n1) \
    && test -n "$JAR" \
    && cp "$JAR" /workspace/app.jar

# -----------------------------------------------------------------------------
# Stage 3: Runtime (JRE 25)
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/*
RUN groupadd --gid 10001 spring && useradd --uid 10001 --gid 10001 spring
USER spring:spring
COPY --from=backend-build /workspace/app.jar /app/app.jar
EXPOSE 8080
ENV JAVA_OPTS=""
HEALTHCHECK --interval=10s --timeout=3s --retries=3 --start-period=30s CMD curl -f http://localhost:8080/healthz || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
