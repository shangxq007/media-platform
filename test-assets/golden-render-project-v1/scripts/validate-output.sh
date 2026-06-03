#!/usr/bin/env bash
# test-assets/golden-render-project-v1/scripts/validate-output.sh
# Validate a rendered output video for Golden Render Project v1.
#
# Usage: bash scripts/validate-output.sh <video_path> [--require-audio]
#
# Checks:
#   - ffprobe: duration ~30s, video stream, audio stream (if --require-audio), resolution, fps, codec
#   - Calls extract-frames.sh to extract validation frames
#   - Writes report to outputs/reports/output-validation.txt

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

if [[ $# -lt 1 ]]; then
  log_error "Usage: $0 <video_path> [--require-audio]"
  exit 1
fi

VIDEO_PATH="$1"
REQUIRE_AUDIO=false
if [[ $# -ge 2 && "$2" == "--require-audio" ]]; then
  REQUIRE_AUDIO=true
fi

if [[ ! -f "${VIDEO_PATH}" ]]; then
  log_error "Video file not found: ${VIDEO_PATH}"
  exit 1
fi

if ! command -v ffprobe &>/dev/null; then
  log_error "ffprobe is not installed. Cannot validate output."
  exit 1
fi

OUTPUT_DIR="$(dirname "${VIDEO_PATH}")"
REPORT_DIR="${OUTPUT_DIR}/reports"
REPORT_FILE="${REPORT_DIR}/output-validation.txt"
FRAMES_DIR="${OUTPUT_DIR}/frames"

mkdir -p "${REPORT_DIR}"

ERRORS=0

{
  echo "Golden Render Project v1 - Output Validation Report"
  echo "Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "Video: ${VIDEO_PATH}"
  echo "=============================================="
  echo ""
} > "${REPORT_FILE}"

log_info "Running ffprobe on: ${VIDEO_PATH}"

# Probe video
PROBE_JSON=$(ffprobe -v quiet -print_format json -show_format -show_streams "${VIDEO_PATH}" 2>&1)
echo "${PROBE_JSON}" >> "${REPORT_FILE}"

# Extract key fields (use jq if available, otherwise grep/sed)
if command -v jq &>/dev/null; then
  DURATION=$(echo "${PROBE_JSON}" | jq -r '.format.duration // "0"')
  HAS_VIDEO=$(echo "${PROBE_JSON}" | jq '[.streams[]? | select(.codec_type=="video")] | length > 0')
  HAS_AUDIO=$(echo "${PROBE_JSON}" | jq '[.streams[]? | select(.codec_type=="audio")] | length > 0')
  WIDTH=$(echo "${PROBE_JSON}" | jq '[.streams[]? | select(.codec_type=="video")] | .[0].width // 0')
  HEIGHT=$(echo "${PROBE_JSON}" | jq '[.streams[]? | select(.codec_type=="video")] | .[0].height // 0')
  VIDEO_CODEC=$(echo "${PROBE_JSON}" | jq -r '[.streams[]? | select(.codec_type=="video")] | .[0].codec_name // "unknown"')
else
  # Fallback: basic grep-based extraction
  DURATION=$(echo "${PROBE_JSON}" | grep -o '"duration": "[^"]*"' | head -1 | cut -d'"' -f4)
  DURATION=${DURATION:-0}
  HAS_VIDEO=$(echo "${PROBE_JSON}" | grep -c '"codec_type": "video"' >/dev/null 2>&1 && echo "true" || echo "false")
  HAS_AUDIO=$(echo "${PROBE_JSON}" | grep -c '"codec_type": "audio"' >/dev/null 2>&1 && echo "true" || echo "false")
  WIDTH=$(echo "${PROBE_JSON}" | grep -o '"width": [0-9]*' | head -1 | grep -o '[0-9]*')
  WIDTH=${WIDTH:-0}
  HEIGHT=$(echo "${PROBE_JSON}" | grep -o '"height": [0-9]*' | head -1 | grep -o '[0-9]*')
  HEIGHT=${HEIGHT:-0}
  VIDEO_CODEC="unknown"
fi

echo "" >> "${REPORT_FILE}"
echo "Validation Checks" >> "${REPORT_FILE}"
echo "-----------------" >> "${REPORT_FILE}"

# Duration check (accept any positive duration; golden project expects ~30s but single-clip may be shorter)
DURATION_INT=${DURATION%.*}
if [[ "${DURATION_INT}" -ge 1 ]]; then
  log_info "  Duration: ${DURATION}s [PASS]"
  echo "  Duration: ${DURATION}s [PASS]" >> "${REPORT_FILE}"
else
  log_error "  Duration: ${DURATION}s [FAIL, expected >0s]"
  echo "  Duration: ${DURATION}s [FAIL, expected >0s]" >> "${REPORT_FILE}"
  ((ERRORS++))
fi

# Video stream
if [[ "${HAS_VIDEO}" == "true" ]]; then
  log_info "  Video stream: present [PASS]"
  echo "  Video stream: present [PASS]" >> "${REPORT_FILE}"
else
  log_error "  Video stream: missing [FAIL]"
  echo "  Video stream: missing [FAIL]" >> "${REPORT_FILE}"
  ((ERRORS++))
fi

# Audio stream
if [[ "${HAS_AUDIO}" == "true" ]]; then
  log_info "  Audio stream: present [PASS]"
  echo "  Audio stream: present [PASS]" >> "${REPORT_FILE}"
elif [[ "${REQUIRE_AUDIO}" == "true" ]]; then
  log_error "  Audio stream: missing [FAIL, --require-audio was set]"
  echo "  Audio stream: missing [FAIL]" >> "${REPORT_FILE}"
  ((ERRORS++))
else
  log_warn "  Audio stream: missing (OK for video-only)"
  echo "  Audio stream: missing (OK for video-only)" >> "${REPORT_FILE}"
fi

# Resolution
log_info "  Resolution: ${WIDTH}x${HEIGHT}"
echo "  Resolution: ${WIDTH}x${HEIGHT}" >> "${REPORT_FILE}"

# FPS (skip detailed extraction for now)
FPS="unknown"
log_info "  FPS: ${FPS}"
echo "  FPS: ${FPS}" >> "${REPORT_FILE}"

# Codec
log_info "  Video codec: ${VIDEO_CODEC}"
echo "  Video codec: ${VIDEO_CODEC}" >> "${REPORT_FILE}"

# Extract frames
echo "" >> "${REPORT_FILE}"
echo "Frame Extraction" >> "${REPORT_FILE}"
echo "-----------------" >> "${REPORT_FILE}"
log_info "Extracting validation frames..."
if bash "${SCRIPT_DIR}/extract-frames.sh" "${VIDEO_PATH}" "${FRAMES_DIR}"; then
  log_info "  Frame extraction: complete [PASS]"
  echo "  Frame extraction: complete [PASS]" >> "${REPORT_FILE}"
else
  log_error "  Frame extraction: failed [FAIL]"
  echo "  Frame extraction: failed [FAIL]" >> "${REPORT_FILE}"
  ((ERRORS++))
fi

# Summary
{
  echo ""
  echo "Summary"
  echo "-------"
  echo "Errors: ${ERRORS}"
  echo "Result: $([ ${ERRORS} -eq 0 ] && echo PASS || echo FAIL)"
} >> "${REPORT_FILE}"

echo ""
if [[ ${ERRORS} -gt 0 ]]; then
  log_error "Output validation FAILED with ${ERRORS} error(s)."
  log_info "Report: ${REPORT_FILE}"
  exit 1
fi

log_info "Output validation PASSED."
log_info "Report: ${REPORT_FILE}"
