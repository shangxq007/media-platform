#!/usr/bin/env bash
set -euo pipefail

image=${1:?usage: verify-thumbnail-worker-image.sh IMAGE}
command -v podman >/dev/null || { echo 'podman is required' >&2; exit 2; }

config=$(podman image inspect "$image" --format '{{json .Config}}')
python3 - "$config" <<'PY'
import json, sys
cfg = json.loads(sys.argv[1])
ports = cfg.get("ExposedPorts") or {}
if "8080/tcp" in ports:
    raise SystemExit("worker image must not advertise 8080/tcp")
if ports:
    raise SystemExit(f"worker image advertises unexpected ports: {sorted(ports)}")
entrypoint = cfg.get("Entrypoint") or []
if entrypoint != ["java", "-jar", "/app/platform-thumbnail-worker.jar"]:
    raise SystemExit(f"unexpected worker entrypoint: {entrypoint!r}")
if cfg.get("User") != "spring:spring":
    raise SystemExit(f"worker image must run as spring:spring: {cfg.get('User')!r}")
print("oci-config=pass exposed-ports=empty entrypoint=ThumbnailWorkerApplication user=spring:spring")
PY

podman run --rm --network none --cap-drop=all --security-opt=no-new-privileges \
  --entrypoint /bin/sh "$image" -eu -c '
    test "$(id -u)" = 10001
    test -x /usr/bin/ffmpeg
    test -x /usr/bin/ffprobe
    test -x /usr/bin/bwrap
    test -f /app/platform-thumbnail-worker.jar
    ! find /app -maxdepth 1 -name app.jar -print -quit | grep -q .
    echo runtime-self-check=pass uid=10001 network=none cap-drop=all no-new-privileges
  '
