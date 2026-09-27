#!/usr/bin/env bash
# AUTH-DEV-AUTH-CALLER-ADAPT-001 — verification for the smoke-script dev-auth caller.
#
# Hermetic (no network, no backend): uses a stub `curl` on PATH. Verifies that the
# smoke dev-auth caller
#   * fails closed and explains how to configure DEV_AUTH_SECRET when it is absent;
#   * reads DEV_AUTH_SECRET from the environment and from a .env file;
#   * carries the X-Dev-Auth-Secret header to /api/dev/auth/token;
#   * never puts the secret in the process argument list.
#
# Usage: bash scripts/smoke/dev-auth-caller.test.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SECRET="smoke-sentinel-secret-value"

TMP="$(mktemp -d)"
cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT

# A hermetic copy of the smoke scripts so repo-root .env files never leak into the
# fail-closed assertions. $TMP/repo/scripts/smoke is the "repo" the scripts believe in.
mkdir -p "$TMP/bin" "$TMP/work" "$TMP/repo/scripts"
cp -R "$SCRIPT_DIR" "$TMP/repo/scripts/smoke"
SMOKE="$TMP/repo/scripts/smoke"

# Stub curl: records argv to $STUB_CURL_LOG, records stdin when invoked with
# --config -, and answers with a canned JSON body according to $STUB_CURL_MODE.
cat > "$TMP/bin/curl" <<'STUB'
#!/usr/bin/env bash
{
    printf 'ARGV: %s\n' "$*"
    if printf '%s\n' "$@" | grep -qx -- '--config'; then
        printf 'CONFIG-BEGIN\n'
        cat
        printf 'CONFIG-END\n'
    fi
} >> "$STUB_CURL_LOG" 2>/dev/null
case "${STUB_CURL_MODE:-token}" in
    health) printf '%s' '{"status":"UP"}' ;;
    token)  printf '%s' '{"accessToken":"stub-dev-token","tokenType":"Bearer"}' ;;
    *)      printf '%s' '{}' ;;
esac
STUB
chmod +x "$TMP/bin/curl"

export PATH="$TMP/bin:$PATH"
export STUB_CURL_LOG="$TMP/curl.log"
export STUB_CURL_MODE="token"

FAILURES=0
check() {
    if [ "$2" = "0" ]; then
        echo "  ✅ $1"
    else
        echo "  ❌ $1"
        FAILURES=$((FAILURES + 1))
    fi
}

echo "=== dev-auth smoke caller tests ==="

# 1. Fail closed when DEV_AUTH_SECRET is absent (hermetic fake repo root).
echo "1. fail closed without DEV_AUTH_SECRET"
set +e
output="$(cd "$TMP/work" && env -u DEV_AUTH_SECRET bash -c \
    '. "$1/repo/scripts/smoke/lib/dev-auth.sh"; smoke_require_dev_auth_secret' _ "$TMP" 2>&1)"
rc=$?
set -e
check "exits non-zero" "$([ "$rc" -ne 0 ] && echo 0 || echo 1)"
check "prints setup guidance" "$(echo "$output" | grep -q 'DEV_AUTH_SECRET is not set' && echo 0 || echo 1)"

# 2. Succeeds when DEV_AUTH_SECRET is exported.
echo "2. accepts DEV_AUTH_SECRET from the environment"
set +e
DEV_AUTH_SECRET="$SECRET" bash -c \
    '. "$1/repo/scripts/smoke/lib/dev-auth.sh"; smoke_require_dev_auth_secret' _ "$TMP"
rc=$?
set -e
check "exits zero" "$([ "$rc" -eq 0 ] && echo 0 || echo 1)"

# 3. Reads DEV_AUTH_SECRET from a .env file.
echo "3. reads DEV_AUTH_SECRET from a .env file"
printf 'SOMETHING=else\nexport DEV_AUTH_SECRET="%s"\n' "$SECRET" > "$TMP/repo/.env"
set +e
resolved="$(cd "$TMP/work" && env -u DEV_AUTH_SECRET bash -c \
    '. "$1/repo/scripts/smoke/lib/dev-auth.sh"; smoke_require_dev_auth_secret && printf %s "$DEV_AUTH_SECRET"' _ "$TMP")"
rc=$?
set -e
check "exits zero" "$([ "$rc" -eq 0 ] && echo 0 || echo 1)"
check "resolves the value and strips quotes" "$([ "$resolved" = "$SECRET" ] && echo 0 || echo 1)"
rm -f "$TMP/repo/.env"

# 4. Sends the header to the canonical backend path, without leaking into argv.
echo "4. carries X-Dev-Auth-Secret to /api/dev/auth/token"
: > "$STUB_CURL_LOG"
token="$(DEV_AUTH_SECRET="$SECRET" bash -c \
    '. "$1/repo/scripts/smoke/lib/dev-auth.sh"; smoke_require_dev_auth_secret; smoke_dev_auth_token "http://127.0.0.1:8088" smoke-tenant smoke-user' _ "$TMP")"
check "returns the token" "$([ "$token" = "stub-dev-token" ] && echo 0 || echo 1)"
check "targets the canonical path" "$(grep -q 'url = "http://127.0.0.1:8088/api/dev/auth/token"' "$STUB_CURL_LOG" && echo 0 || echo 1)"
check "sends the secret header" "$(grep -q "X-Dev-Auth-Secret: $SECRET" "$STUB_CURL_LOG" && echo 0 || echo 1)"
check "secret absent from curl argv" "$(grep '^ARGV:' "$STUB_CURL_LOG" | grep -q "$SECRET" && echo 1 || echo 0)"

# 5. render-execution-smoke.sh fails closed before minting a token.
echo "5. render-execution-smoke.sh fails closed without a secret"
: > "$STUB_CURL_LOG"
set +e
output="$(cd "$TMP/work" && env -u DEV_AUTH_SECRET RENDER_EXECUTION_WRITE=1 STUB_CURL_MODE=health \
    bash "$SMOKE/render-execution-smoke.sh" 2>&1)"
rc=$?
set -e
check "exits non-zero" "$([ "$rc" -ne 0 ] && echo 0 || echo 1)"
check "prints setup guidance" "$(echo "$output" | grep -q 'DEV_AUTH_SECRET is not set' && echo 0 || echo 1)"
check "never calls the dev auth endpoint" "$(grep -q 'dev/auth' "$STUB_CURL_LOG" && echo 1 || echo 0)"

# 6. real-media-render-smoke.sh fails closed before any network call.
echo "6. real-media-render-smoke.sh fails closed before any network call"
: > "$STUB_CURL_LOG"
set +e
output="$(cd "$TMP/work" && env -u DEV_AUTH_SECRET REAL_MEDIA_RENDER_WRITE=1 \
    bash "$SMOKE/real-media-render-smoke.sh" 2>&1)"
rc=$?
set -e
check "exits non-zero" "$([ "$rc" -ne 0 ] && echo 0 || echo 1)"
check "prints setup guidance" "$(echo "$output" | grep -q 'DEV_AUTH_SECRET is not set' && echo 0 || echo 1)"
check "made no network call" "$([ ! -s "$STUB_CURL_LOG" ] && echo 0 || echo 1)"

# 7. With a secret, real-media-render-smoke.sh passes the token gate using the secret header.
echo "7. real-media-render-smoke.sh mints a token when the secret is set"
: > "$STUB_CURL_LOG"
set +e
output="$(cd "$TMP/work" && env DEV_AUTH_SECRET="$SECRET" REAL_MEDIA_RENDER_WRITE=1 \
    bash "$SMOKE/real-media-render-smoke.sh" 2>&1)"
set -e
check "reports a token was obtained" "$(echo "$output" | grep -q 'Token obtained' && echo 0 || echo 1)"
check "sent the secret header" "$(grep -q "X-Dev-Auth-Secret: $SECRET" "$STUB_CURL_LOG" && echo 0 || echo 1)"

echo ""
if [ "$FAILURES" -eq 0 ]; then
    echo "=== PASS ==="
else
    echo "=== FAIL ($FAILURES) ==="
    exit 1
fi
