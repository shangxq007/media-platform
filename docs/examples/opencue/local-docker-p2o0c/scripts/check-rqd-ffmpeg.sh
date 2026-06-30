#!/usr/bin/env bash
# P2O.0c — Check RQD ffmpeg/ffprobe availability
# Operator-run only. Not production.
set -euo pipefail

echo "[P2O.0c] Checking RQD ffmpeg/ffprobe..."

# Check ffmpeg
echo "--- ffmpeg ---"
if docker exec opencue-rqd sh -c 'command -v ffmpeg'; then
    echo "[P2O.0c] ffmpeg: AVAILABLE"
    docker exec opencue-rqd ffmpeg -version 2>&1 | head -3
else
    echo "[P2O.0c] ffmpeg: NOT AVAILABLE"
    echo "[P2O.0c] RQD smoke image does not include ffmpeg. Rebuild with rqd-smoke.Dockerfile."
    exit 1
fi

# Check ffprobe
echo "--- ffprobe ---"
if docker exec opencue-rqd sh -c 'command -v ffprobe'; then
    echo "[P2O.0c] ffprobe: AVAILABLE"
    docker exec opencue-rqd ffprobe -version 2>&1 | head -3
else
    echo "[P2O.0c] ffprobe: NOT AVAILABLE"
    exit 1
fi

echo "[P2O.0c] Done."
