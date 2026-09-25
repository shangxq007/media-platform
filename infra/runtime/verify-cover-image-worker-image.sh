#!/usr/bin/env bash
# OCI acceptance gate for the cover-image worker image. Independent of the thumbnail verify script.
set -euo pipefail

image=${1:?usage: verify-cover-image-worker-image.sh IMAGE}
command -v podman >/dev/null || { echo 'podman is required' >&2; exit 2; }

config=$(podman image inspect "$image" --format '{{json .Config}}')
python3 - "$config" <<'PY'
import json, sys
cfg = json.loads(sys.argv[1])
ports = cfg.get("ExposedPorts") or {}
if ports:
    raise SystemExit(f"worker image advertises unexpected ports: {sorted(ports)}")
entrypoint = cfg.get("Entrypoint") or []
if entrypoint != ["java", "-jar", "/app/platform-cover-image-worker.jar"]:
    raise SystemExit(f"unexpected worker entrypoint: {entrypoint!r}")
if cfg.get("User") != "spring:spring":
    raise SystemExit(f"worker image must run as spring:spring: {cfg.get('User')!r}")
env = cfg.get("Env") or []
joined = "\n".join(env)
for required in (
    "SPRING_PROFILES_ACTIVE=dev,temporal,cover-image-worker",
    "SPRING_MAIN_WEB_APPLICATION_TYPE=none",
    "APP_TEMPORAL_TASK_QUEUE=media-platform-tasks",
    "SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/app/cover-image-worker-override.yml",
):
    if required not in joined:
        raise SystemExit(f"worker image is missing required env: {required}")
print("oci-config=pass exposed-ports=empty entrypoint=CoverImageWorkerApplication user=spring:spring")
PY

podman run --rm --network none --cap-drop=all --security-opt=no-new-privileges \
  --entrypoint /bin/sh "$image" -eu -c '
    test "$(id -u)" = 10001
    test -x /usr/bin/ffmpeg
    test -x /usr/bin/ffprobe
    test -x /usr/bin/bwrap
    test -f /app/platform-cover-image-worker.jar
    test -f /app/cover-image-worker-override.yml
    ! find /app -maxdepth 1 -name app.jar -print -quit | grep -q .
    echo runtime-self-check=pass uid=10001 network=none cap-drop=all no-new-privileges
  '
