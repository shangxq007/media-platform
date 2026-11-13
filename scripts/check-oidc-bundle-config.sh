#!/usr/bin/env bash
# Prove the committed frontend bundle carries no environment-specific OIDC values.
#
# The bundle is built without VITE_OIDC_* and receives the deployment issuer/audience at
# runtime from the backend /config.js endpoint. This guard fails if a test/local issuer
# (loopback host, `f8-local` audience) is committed, and optionally verifies that a clean
# rebuild reproduces the committed assets.
#
# Usage: bash scripts/check-oidc-bundle-config.sh [--rebuild]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FRONTEND="$REPO_ROOT/frontend"
STATIC="$REPO_ROOT/platform-app/src/main/resources/static"

FAILURES=0
check() {
    if [ "$2" = "0" ]; then
        echo "  ✅ $1"
    else
        echo "  ❌ $1"
        FAILURES=$((FAILURES + 1))
    fi
}

echo "=== OIDC bundle config guard ==="

# 1. No test/local OIDC issuer or audience in the committed bundle.
#    Library code legitimately contains generic loopback fallbacks
#    (React Router's "http://localhost", a URL parser's "http://127.0.0.1"), so the
#    guard matches the exact local-validation issuer/audience instead of any loopback.
echo "1. committed assets carry no test OIDC values"
if [ -d "$STATIC/assets" ]; then
    hits="$(grep -rIn -e '127\.0\.0\.1:18081' -e 'localhost:18081' -e 'f8-local' "$STATIC/assets" 2>/dev/null || true)"
    check "no local-validation issuer / audience in committed assets" \
        "$([ -z "$hits" ] && echo 0 || echo 1)"
    [ -n "$hits" ] && echo "$hits"
else
    check "committed static assets directory exists" 1
fi

# 2. The runtime config endpoint is wired into the document.
echo "2. index.html loads the runtime config endpoint"
check "index.html references /config.js" \
    "$(grep -q 'src="/config.js"' "$STATIC/index.html" && echo 0 || echo 1)"

# 3. The backend serves the runtime config endpoint.
echo "3. backend serves /config.js"
check "ClientConfigController mapped at /config.js" \
    "$(grep -rq 'GetMapping("/config.js")' "$REPO_ROOT/platform-app/src/main/java" && echo 0 || echo 1)"

# 4. Optional reproducibility: rebuild without VITE_OIDC_* and compare asset hashes.
if [ "${1:-}" = "--rebuild" ]; then
    if [ ! -d "$FRONTEND/node_modules" ]; then
        echo "  ⚠️  frontend/node_modules missing — skipping reproducibility check"
    else
        echo "4. clean rebuild reproduces the committed assets"
        BUILD_ROOT="$(mktemp -d)"
        (
            cd "$FRONTEND"
            env -u VITE_OIDC_ISSUER -u VITE_OIDC_CLIENT_ID -u VITE_OIDC_REDIRECT_URI -u VITE_OIDC_SCOPE \
                npx vite build --outDir "$BUILD_ROOT/dist" --emptyOutDir >/dev/null 2>&1
        )
        changed=0
        for emitted in "$BUILD_ROOT"/dist/assets/*; do
            name="$(basename "$emitted")"
            committed="$STATIC/assets/$name"
            if [ ! -f "$committed" ]; then
                echo "  ❌ emitted asset not committed: $name"
                changed=1
            elif ! cmp -s "$emitted" "$committed"; then
                echo "  ❌ committed asset differs from a clean rebuild: $name"
                changed=1
            fi
        done
        for committed in "$STATIC"/assets/*; do
            name="$(basename "$committed")"
            [ -f "$BUILD_ROOT/dist/assets/$name" ] || {
                echo "  ❌ committed asset absent from a clean rebuild: $name"
                changed=1
            }
        done
        check "committed assets are reproducible from source" "$changed"
        rm -rf "$BUILD_ROOT"
    fi
fi

echo ""
if [ "$FAILURES" -eq 0 ]; then
    echo "=== PASS ==="
else
    echo "=== FAIL ($FAILURES) ==="
    exit 1
fi
