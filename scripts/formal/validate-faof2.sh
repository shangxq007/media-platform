#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repository_root"

python3 scripts/formal/check-proof-holes.py
python3 scripts/formal/check-witness-mapping.py
python3 scripts/formal/test-formal-checks.py

lean_binary="${LEAN:-lean}"
"$lean_binary" --version | grep -F "version 4.19.0"
"$lean_binary" formal/lean/Faof2Graph.lean

coq_image="${COQ_IMAGE:-docker.io/coqorg/coq:8.20}"
if command -v coqc >/dev/null 2>&1; then
  coqc --version | grep -F "version 8.20.1"
  coqc -dump-glob /tmp/Faof2Graph.glob -o /tmp/Faof2Graph.vo formal/coq/Faof2Graph.v
else
  container_engine="${COQ_CONTAINER_ENGINE:-}"
  if [ -z "$container_engine" ]; then
    if command -v podman >/dev/null 2>&1; then
      container_engine=podman
    elif command -v docker >/dev/null 2>&1; then
      container_engine=docker
    else
      echo "Coq 8.20 runner unavailable" >&2
      exit 1
    fi
  fi
  "$container_engine" run --rm "$coq_image" coqc --version | grep -F "version 8.20.1"
  "$container_engine" run --rm \
    -v "$repository_root:/workspace:Z,ro" -w /workspace "$coq_image" \
    coqc -dump-glob /tmp/Faof2Graph.glob -o /tmp/Faof2Graph.vo formal/coq/Faof2Graph.v
fi

echo "FAOF2_FORMAL_VALIDATION=PASS lean=4.19.0 coq=8.20.1"
