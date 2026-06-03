#!/usr/bin/env bash
# test-assets/golden-render-project-v1/scripts/validate-assets.sh
# Validate synthetic assets for Golden Render Project v1.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ASSETS_DIR="${PROJECT_DIR}/assets"
REPORT_DIR="${PROJECT_DIR}/expected/reports"
REPORT_FILE="${REPORT_DIR}/assets-validation.txt"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

mkdir -p "${REPORT_DIR}"

HAS_FFPROBE=false
command -v ffprobe &>/dev/null && HAS_FFPROBE=true

ERRORS=0

check_file() {
  local path="$1"
  local desc="$2"
  if [ ! -f "${path}" ]; then
    log_error "MISSING: ${desc} (${path})"
    ERRORS=$((ERRORS + 1))
    return 1
  fi
  local sz
  sz=$(wc -c < "${path}" 2>/dev/null || echo 0)
  if [ "${sz}" -eq 0 ]; then
    log_error "EMPTY: ${desc} (${path})"
    ERRORS=$((ERRORS + 1))
    return 1
  fi
  log_info "OK: ${desc} (${sz} bytes)"
}

{
  echo "Golden Render Project v1 - Asset Validation Report"
  echo "Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "=============================================="
  echo ""
} > "${REPORT_FILE}"

log_info "Validating video assets..."
for f in color_bars_1080p.mp4 grid_motion_1080p.mp4 moving_box_1080p.mp4 \
         portrait_test_1080x1920.mp4 square_test_1080x1080.mp4 green_screen_test.mp4; do
  check_file "${ASSETS_DIR}/video/${f}" "video/${f}"
  if [ "${HAS_FFPROBE}" = true ]; then
    echo "--- video/${f} ---" >> "${REPORT_FILE}"
    ffprobe -v quiet -print_format json -show_format -show_streams "${ASSETS_DIR}/video/${f}" >> "${REPORT_FILE}" 2>&1 || true
    echo "" >> "${REPORT_FILE}"
  fi
done

log_info "Validating image assets..."
for f in logo_transparent.png product_card.png lower_third_bg.png mask_shape.png; do
  check_file "${ASSETS_DIR}/image/${f}" "image/${f}"
done

log_info "Validating audio assets..."
for f in music_bgm.wav voiceover.wav sfx_click.wav sfx_whoosh.wav; do
  check_file "${ASSETS_DIR}/audio/${f}" "audio/${f}"
done

log_info "Validating subtitle assets..."
for f in subtitles_zh.srt subtitles_en.srt subtitles_webvtt.vtt; do
  check_file "${ASSETS_DIR}/subtitle/${f}" "subtitle/${f}"
done

{
  echo ""
  echo "Summary"
  echo "-------"
  echo "Errors:   ${ERRORS}"
  echo "ffprobe:  ${HAS_FFPROBE}"
} >> "${REPORT_FILE}"

echo ""
if [ ${ERRORS} -gt 0 ]; then
  log_error "Validation FAILED with ${ERRORS} error(s)."
  exit 1
fi

log_info "Validation PASSED."
log_info "Report: ${REPORT_FILE}"
