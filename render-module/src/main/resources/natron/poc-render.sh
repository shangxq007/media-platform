#!/usr/bin/env bash
set -euo pipefail

EFFECT_KEY=""
INPUT=""
OUTPUT=""
INTENSITY="0.5"
SATURATION="1.15"
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
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [[ -z "$INPUT" || -z "$OUTPUT" || -z "$EFFECT_KEY" || -z "$BATCH_SCRIPT" ]]; then
  echo "Usage: poc-render.sh --effect-key KEY --input PATH --output PATH --batch-script PATH" >&2
  exit 2
fi
if [[ ! -f "$INPUT" || ! -f "$BATCH_SCRIPT" ]]; then
  echo "Input or batch script missing" >&2
  exit 1
fi
if ! command -v "$RENDERER" >/dev/null 2>&1; then
  echo "NatronRenderer not found: $RENDERER" >&2
  exit 1
fi

mkdir -p "$(dirname "$OUTPUT")"
export NATRON_INTENSITY="$INTENSITY"
export NATRON_SATURATION="$SATURATION"
"$RENDERER" -b \
  -i "$READER_NODE" "$INPUT" \
  -w "$WRITER_NODE" "$OUTPUT" \
  "$BATCH_SCRIPT"

test -f "$OUTPUT"
