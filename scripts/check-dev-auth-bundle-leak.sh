#!/usr/bin/env bash
# AUTH-DEV-AUTH-CALLER-ADAPT-001 — prove the dev auth secret never reaches the browser bundle.
#
# Builds the frontend with a sentinel DEV_AUTH_SECRET and asserts the sentinel,
# the secret header name and the secret-protected backend path are absent from
# the emitted assets. Also statically checks that no client source references the
# secret and that the vite env prefix is not widened.
#
# Usage: bash scripts/check-dev-auth-bundle-leak.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FRONTEND="$REPO_ROOT/frontend"
SENTINEL="DEV_AUTH_SECRET_SENTINEL_9f3a1c_LEAK_PROBE"

FAILURES=0
check() {
    if [ "$2" = "0" ]; then
        echo "  ✅ $1"
    else
        echo "  ❌ $1"
        FAILURES=$((FAILURES + 1))
    fi
}

echo "=== dev auth bundle leak check ==="

# 1. Static: no client source may read the secret or send its header.
echo "1. client source never reads the secret"
client_hits="$(grep -rIn --exclude='*.test.ts' --exclude='*.test.tsx' \
    -e 'import\.meta\.env\.[A-Za-z_]*DEV_AUTH' \
    -e 'process\.env\.[A-Za-z_]*DEV_AUTH' \
    -e 'VITE_DEV_AUTH' \
    -e "\"X-Dev-Auth-Secret\"" \
    -e "'X-Dev-Auth-Secret'" \
    -e '"app.security.dev-auth-secret"' \
    "$FRONTEND/src" 2>/dev/null || true)"
check "no client-side read of the dev auth secret" \
    "$([ -z "$client_hits" ] && echo 0 || echo 1)"
[ -n "$client_hits" ] && echo "$client_hits"

# 2. Static: the client env prefix must not be widened to expose non-VITE_ vars.
echo "2. vite config keeps the secret server-side"
check "client env prefix not widened" \
    "$(grep -q 'envPrefix' "$FRONTEND/vite.config.ts" && echo 1 || echo 0)"

# 3. Dynamic: build with a sentinel and grep the emitted assets.
echo "3. production build carries no secret"
if [ ! -d "$FRONTEND/node_modules" ]; then
    echo "  ⚠️  frontend/node_modules missing — run 'npm ci' in frontend/ to enable the build check"
    echo ""
    echo "=== FAIL (build check unavailable) ==="
    exit 1
fi

BUILD_ROOT="$(mktemp -d)"
BUILD_OUT="$BUILD_ROOT/dist"
(
    cd "$FRONTEND"
    DEV_AUTH_SECRET="$SENTINEL" npx vite build --outDir "$BUILD_OUT" --emptyOutDir >/dev/null 2>&1
)

check "sentinel value absent from emitted assets" \
    "$(grep -rq "$SENTINEL" "$BUILD_OUT" && echo 1 || echo 0)"
check "secret header name absent from emitted assets" \
    "$(grep -rqi 'X-Dev-Auth-Secret' "$BUILD_OUT" && echo 1 || echo 0)"
check "secret-protected backend path absent from emitted assets" \
    "$(grep -rq 'api/dev/auth' "$BUILD_OUT" && echo 1 || echo 0)"
rm -rf "$BUILD_ROOT"

echo ""
if [ "$FAILURES" -eq 0 ]; then
    echo "=== PASS ==="
else
    echo "=== FAIL ($FAILURES) ==="
    exit 1
fi
