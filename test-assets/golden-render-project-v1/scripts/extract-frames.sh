#!/usr/bin/env bash
# test-assets/golden-render-project-v1/scripts/extract-frames.sh
# Extract frames from a video at fixed validation timestamps.
#
# Usage: bash scripts/extract-frames.sh <video_path> [output_dir]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

if [ $# -lt 1 ]; then
  log_error "Usage: $0 <video_path> [output_dir]"
  exit 1
fi

VIDEO_PATH="$1"
OUTPUT_DIR="${2:-${PROJECT_DIR}/outputs/frames}"

if [ ! -f "${VIDEO_PATH}" ]; then
  log_error "Video file not found: ${VIDEO_PATH}"
  exit 1
fi

if ! command -v ffmpeg &>/dev/null; then
  log_error "ffmpeg is not installed."
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"

FFMPEG="ffmpeg -y -hide_banner -loglevel warning"

TIMESTAMPS=(2 7 12 17 22 28)

BASENAME="$(basename "${VIDEO_PATH}" | sed 's/\.[^.]*$//')"

log_info "Extracting frames from: ${VIDEO_PATH}"
log_info "Output directory: ${OUTPUT_DIR}"

for ts in "${TIMESTAMPS[@]}"; do
  H=$((ts / 3600))
  M=$(( (ts % 3600) / 60 ))
  S=$((ts % 60))
  TIMESTAMP=$(printf "%02d:%02d:%02d" "${H}" "${M}" "${S}")
  OUT_FILE="${OUTPUT_DIR}/${BASENAME}_frame_${ts}s.png"
  log_info "  Extracting frame at ${TIMESTAMP} -> ${OUT_FILE}"
  ${FFMPEG} -ss "${TIMESTAMP}" -i "${VIDEO_PATH}" -frames:v 1 -q:v 2 -update 1 "${OUT_FILE}"
done

log_info "Frame extraction complete. ${#TIMESTAMPS[@]} frames extracted."
