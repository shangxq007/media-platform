#!/usr/bin/env bash
set -euo pipefail

if ! command -v podman >/dev/null; then
  echo "BLOCKED/NOT_RUN: podman unavailable; PostgreSQL migration test not executed"
  exit 125
fi
name="artifact-convergence-migration-${RANDOM}"
trap 'podman stop "$name" >/dev/null 2>&1 || true; podman rm "$name" >/dev/null 2>&1 || true' EXIT
podman run --detach --name "$name" -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=review docker.io/library/postgres:16-alpine >/dev/null
ready=0
for _ in $(seq 1 30); do
  if podman exec "$name" pg_isready -U postgres >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
if [ "$ready" -ne 1 ]; then
  echo "BLOCKED/NOT_RUN: PostgreSQL container did not become ready"
  exit 125
fi
for migration in $(find platform-app/src/main/resources/db/migration -name 'V*.sql' -printf '%f\n' | sort -V); do
  podman exec -i "$name" psql -U postgres -d review -v ON_ERROR_STOP=1 -1 < "platform-app/src/main/resources/db/migration/$migration" >/dev/null
done
echo "PASS: clean PostgreSQL bootstrap through V19"
