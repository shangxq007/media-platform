# Render Worker — JavaCV
# Dockerfile draft for future worker split

FROM eclipse-temurin:25-jdk-jammy

# Install FFmpeg and OpenCV dependencies
RUN apt-get update && apt-get install -y \
    ffmpeg \
    libopencv-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY render-module/build.gradle.kts render-module/

# Copy source
COPY render-module/src render-module/src
COPY shared-kernel/src shared-kernel/src

# Build the worker
RUN ./gradlew :render-module:jar --no-daemon

# Runtime
FROM eclipse-temurin:25-jre-jammy

RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=0 /app/render-module/build/libs/*.jar app.jar

ENV APP_STORAGE_LOCAL_ROOT=/data/storage
VOLUME ["/data/storage"]

ENTRYPOINT ["java", "-jar", "app.jar"]
