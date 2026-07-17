#!/usr/bin/env bash
set -euo pipefail

# Document Governance Guard — Unified Entry Point
# Usage: scripts/check-document-governance.sh --head HEAD --output /tmp/report.json

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON="python3"

usage() {
    echo "Usage: $0 --head <git-ref> [--base <git-ref>] [--output <json-path>] [--mode current|transition] [--verbose]"
    exit 2
}

HEAD=""
BASE=""
OUTPUT="/tmp/document-governance-report.json"
MODE="current"
VERBOSE="false"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --head) HEAD="$2"; shift 2 ;;
        --base) BASE="$2"; shift 2 ;;
        --output) OUTPUT="$2"; shift 2 ;;
        --mode) MODE="$2"; shift 2 ;;
        --verbose) VERBOSE="true"; shift ;;
        *) echo "Unknown parameter: $1"; usage ;;
    esac
done

if [[ -z "$HEAD" ]]; then
    echo "Error: --head is required"
    usage
fi

echo "=== Document Governance Guard ==="
echo "Mode: $MODE"
echo "Head: $HEAD"
[[ -n "$BASE" ]] && echo "Base: $BASE"
echo "Output: $OUTPUT"
echo ""

# Run the main checker
$PYTHON "$SCRIPT_DIR/document-governance/check_document_governance.py" \
    --head "$HEAD" \
    ${BASE:+--base "$BASE"} \
    --mode "$MODE" \
    --output "$OUTPUT" \
    ${VERBOSE:+--verbose}

EXIT_CODE=$?

echo ""
echo "Guard report: $OUTPUT"
exit $EXIT_CODE
