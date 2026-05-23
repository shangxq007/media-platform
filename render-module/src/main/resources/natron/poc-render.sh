#!/usr/bin/env bash
# Natron Worker — video.natron_vignette
# Phase 2: NatronRenderer + Python batch script (-i MyReader -w MyWriter)
# Phase 1 fallback: FFmpeg vignette

set -euo pipefail

EFFECT_KEY=""
INPUT=""
OUTPUT=""
INTENSITY="0.5"
SATURATION="1.15"
FALLBACK_FFMPEG="false"
BATCH_SCRIPT=""
READER_NODE="MyReader"
WRITER_NODE="MyWriter"
RENDERER="${NATRON_RENDERER_BIN:-NatronRenderer}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --effect-key) EFFECT_KEY="$2"; shift 2 ;;
    --input) INPUT="$2"; shift 2 ;;
    --output) OUTPUT="$2"; shift 2 ;;
    --intensity) INTENSITY="$2"; shift 2 ;;
    --saturation) SATURATION="$2"; shift 2 ;;
    --batch-script) BATCH_SCRIPT="$2"; shift 2 ;;
    --reader-node) READER_NODE="$2"; shift 2 ;;
    --writer-node) WRITER_NODE="$2"; shift 2 ;;
    --fallback-ffmpeg) FALLBACK_FFMPEG="true"; shift ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [[ -z "$INPUT" || -z "$OUTPUT" || -z "$EFFECT_KEY" ]]; then
  echo "Usage: poc-render.sh --effect-key KEY --input PATH --output PATH [--batch-script PATH] [--fallback-ffmpeg]" >&2
  exit 2
fi

if [[ ! -f "$INPUT" ]]; then
  echo "Input file not found: $INPUT" >&2
  exit 1
fi

mkdir -p "$(dirname "$OUTPUT")"

render_ffmpeg_color_grade() {
  if ! command -v ffmpeg >/dev/null 2>&1; then
    echo "ffmpeg not found; cannot run fallback" >&2
    return 1
  fi
  ffmpeg -y -hide_banner -loglevel error -i "$INPUT" \
    -vf "eq=saturation=${SATURATION}:contrast=1.08:brightness=0.02" \
    -c:v libx264 -preset fast -crf 23 -c:a aac -b:a 128k \
    -movflags +faststart "$OUTPUT"
}

render_ffmpeg_vignette() {
  if ! command -v ffmpeg >/dev/null 2>&1; then
    echo "ffmpeg not found; cannot run fallback" >&2
    return 1
  fi
  local gamma
  gamma=$(awk -v i="$INTENSITY" 'BEGIN { printf "%.3f", 1.0 - (i * 0.35) }')
  ffmpeg -y -hide_banner -loglevel error -i "$INPUT" \
    -vf "eq=gamma=${gamma}:contrast=1.05,vignette=angle=PI/5:mode=forward" \
    -c:v libx264 -preset fast -crf 23 -c:a aac -b:a 128k \
    -movflags +faststart "$OUTPUT"
}

render_natron_batch() {
  if [[ -z "$BATCH_SCRIPT" || ! -f "$BATCH_SCRIPT" ]]; then
    echo "[natron-poc] batch script missing: $BATCH_SCRIPT" >&2
    return 1
  fi
  if ! command -v "$RENDERER" >/dev/null 2>&1; then
    echo "[natron-poc] NatronRenderer not found: $RENDERER" >&2
    return 1
  fi
  export NATRON_INTENSITY="$INTENSITY"
  echo "[natron-poc] NatronRenderer -i ${READER_NODE} ${INPUT} -w ${WRITER_NODE} ${OUTPUT}" >&2
  # Absolute paths required by Natron CLI
  "$RENDERER" -b \
    -i "$READER_NODE" "$INPUT" \
    -w "$WRITER_NODE" "$OUTPUT" \
    "$BATCH_SCRIPT"
}

case "$EFFECT_KEY" in
  video.natron_color_grade)
    if [[ "$FALLBACK_FFMPEG" != "true" ]]; then
      if render_natron_batch; then
        :
      else
        echo "[natron-poc] Natron batch failed; using FFmpeg color-grade fallback" >&2
        render_ffmpeg_color_grade
      fi
    else
      render_ffmpeg_color_grade
    fi
    ;;
  video.natron_vignette)
    if [[ "$FALLBACK_FFMPEG" != "true" ]]; then
      if render_natron_batch; then
        :
      else
        echo "[natron-poc] Natron batch failed; using FFmpeg fallback" >&2
        render_ffmpeg_vignette
      fi
    else
      render_ffmpeg_vignette
    fi
    ;;
  *)
    echo "Unsupported effect key: $EFFECT_KEY" >&2
    exit 1
    ;;
esac

if [[ ! -f "$OUTPUT" ]]; then
  echo "Output was not created: $OUTPUT" >&2
  exit 1
fi

echo "[natron-poc] OK effect=${EFFECT_KEY} output=${OUTPUT}"
