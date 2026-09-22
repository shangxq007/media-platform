#!/usr/bin/env bash
set -euo pipefail
compose="docker compose -f docker-compose.thumbnail-acceptance.yml"
echo "PostgreSQL: postgres:16.4-alpine; Temporal: temporalio/auto-setup:1.26.2; Storage: minio RELEASE.2024-10-13T13-34-11Z"
echo "Config: postgres localhost:55432/platform, Temporal localhost:7233, S3 localhost:59000, task queue media-platform-tasks"
echo "Command: $compose up -d && ./gradlew --no-daemon :platform-app:renderIntegrationTest"
if ! command -v docker >/dev/null; then echo "NOT_RUN: docker unavailable"; exit 2; fi
$compose up -d
$compose ps
./gradlew --no-daemon :platform-app:renderIntegrationTest
