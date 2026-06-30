#!/usr/bin/env bash
# P2O.0f — Safety Check
# Verifies no secrets, paths, URLs, or provider hints leaked in outputs.
# Operator-run only. Not production.
set -euo pipefail

PREVIEW_ROOT="${PREVIEW_ROOT:-build/opencue-shared/media-platform-smoke/preview/p2o0f}"
FAIL=0

echo "[P2O.0f] Safety check on: ${PREVIEW_ROOT}"

if [ ! -d "${PREVIEW_ROOT}" ]; then
    echo "[P2O.0f] WARN: Preview root not found. Run scenarios first."
    exit 0
fi

# Check for signed URLs
echo "[P2O.0f] Checking for signed URLs..."
if grep -rl "presign\|signedUrl\|signed-url\|X-Amz-Signature\|X-Goog-Signature" "${PREVIEW_ROOT}" 2>/dev/null; then
    echo "[P2O.0f] FAIL: Signed URL patterns found!"
    FAIL=1
else
    echo "[P2O.0f] OK: No signed URLs found."
fi

# Check for absolute filesystem paths (excluding container internal paths)
echo "[P2O.0f] Checking for host filesystem paths..."
if grep -rl "/home/\|/Users/\|/root/" "${PREVIEW_ROOT}" 2>/dev/null | grep -v ".txt$" || \
   grep -rl "C:\\\\Users" "${PREVIEW_ROOT}" 2>/dev/null; then
    echo "[P2O.0f] FAIL: Host filesystem paths found!"
    FAIL=1
else
    echo "[P2O.0f] OK: No host filesystem paths found."
fi

# Check for provider/backend/environment hints
echo "[P2O.0f] Checking for provider/backend hints..."
if grep -rl "storageProvider\|executionBackend\|executionEnvironment" "${PREVIEW_ROOT}" 2>/dev/null; then
    echo "[P2O.0f] FAIL: Provider/backend hints found!"
    FAIL=1
else
    echo "[P2O.0f] OK: No provider/backend hints found."
fi

# Check for secrets
echo "[P2O.0f] Checking for secrets..."
if grep -rl "password=\|secret=\|api_key=\|token=" "${PREVIEW_ROOT}" 2>/dev/null | grep -v "opencue_local_smoke"; then
    echo "[P2O.0f] FAIL: Potential secrets found!"
    FAIL=1
else
    echo "[P2O.0f] OK: No secrets found."
fi

# Check terminology
echo "[P2O.0f] Checking OpenCue terminology..."
if grep -rl "opencue.*provider\|opencue.*backend" "${PREVIEW_ROOT}" 2>/dev/null | grep -iv "executionenvironment\|not.*provider\|not.*backend"; then
    echo "[P2O.0f] WARN: OpenCue may be incorrectly labeled as provider/backend."
else
    echo "[P2O.0f] OK: OpenCue terminology correct."
fi

if [ "${FAIL}" -eq 1 ]; then
    echo "[P2O.0f] SAFETY CHECK: FAIL"
    exit 1
else
    echo "[P2O.0f] SAFETY CHECK: PASS"
    exit 0
fi
