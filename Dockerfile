# syntax=docker/dockerfile:1

# -----------------------------------------------------------------------------
# Build: Gradle 9.1 + JDK 25 (JVM 25 requires Gradle 9.1+ to run the build).
# Base image uses Ubuntu Noble; runtime uses Temurin 25 on Jammy.
# -----------------------------------------------------------------------------
FROM gradle:9.1-jdk25-noble AS build
WORKDIR /workspace

COPY . .

RUN chmod +x gradlew \
    && ./gradlew :platform-app:bootJar --no-daemon -x test \
    && JAR=$(ls platform-app/build/libs/*.jar | grep -v plain | head -n1) \
    && test -n "$JAR" \
    && cp "$JAR" /workspace/app.jar

# -----------------------------------------------------------------------------
# Runtime: JRE 25
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

COPY --from=build /workspace/app.jar /app/app.jar

EXPOSE 8080

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
