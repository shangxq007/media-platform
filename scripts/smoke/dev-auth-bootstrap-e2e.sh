#!/usr/bin/env bash
# AUTH-DEV-AUTH-CALLER-ADAPT-001 — end-to-end check of the frontend dev bootstrap.
#
# Starts the real vite dev server (real vite.config.ts) and a stub backend that
# enforces X-Dev-Auth-Secret exactly like DevAuthSecretGuard, then verifies:
#   * the dev-server proxy path mints a token (the proxy injects the secret);
#   * the raw backend dev-auth path is still rejected without the secret;
#   * the secret never appears in what the dev server serves to the browser.
#
# Usage: bash scripts/smoke/dev-auth-bootstrap-e2e.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FRONTEND="$REPO_ROOT/frontend"

SECRET="e2e-dev-auth-secret-4c1f7a"
BACKEND_PORT="${DEV_AUTH_E2E_BACKEND_PORT:-8088}"
VITE_PORT="${DEV_AUTH_E2E_VITE_PORT:-3077}"
NO_SECRET_VITE_PORT="${DEV_AUTH_E2E_NOSECRET_VITE_PORT:-3078}"
WORK="$(mktemp -d)"
BACKEND_PID=""
VITE_PID=""
VITE_NOSECRET_PID=""

cleanup() {
    [ -n "$VITE_NOSECRET_PID" ] && kill "$VITE_NOSECRET_PID" 2>/dev/null || true
    [ -n "$VITE_PID" ] && kill "$VITE_PID" 2>/dev/null || true
    [ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" 2>/dev/null || true
    rm -rf "$WORK"
}
trap cleanup EXIT

FAILURES=0
check() {
    if [ "$2" = "0" ]; then
        echo "  ✅ $1"
    else
        echo "  ❌ $1"
        FAILURES=$((FAILURES + 1))
    fi
}

echo "=== dev auth frontend bootstrap e2e ==="

if [ ! -d "$FRONTEND/node_modules" ]; then
    echo "❌ frontend/node_modules missing — run 'npm ci' in frontend/"
    exit 1
fi

# Stub backend that mirrors the DevAuthController / DevAuthSecretGuard contract.
cat > "$WORK/stub-backend.cjs" <<'JS'
const http = require('node:http')
const secret = process.env.DEV_AUTH_SECRET || ''
const server = http.createServer((req, res) => {
  if (req.method === 'POST' && req.url === '/api/dev/auth/token') {
    const presented = req.headers['x-dev-auth-secret']
    if (!secret || presented !== secret) {
      res.writeHead(401, { 'content-type': 'application/json' })
      res.end('{"error":"dev auth endpoint unavailable"}')
      return
    }
    res.writeHead(200, { 'content-type': 'application/json' })
    res.end('{"accessToken":"e2e-dev-token","tenantId":"tenant-1","userId":"user-1"}')
    return
  }
  res.writeHead(404, { 'content-type': 'application/json' })
  res.end('{}')
})
server.listen(Number(process.env.STUB_BACKEND_PORT), () => console.log('ready'))
JS

DEV_AUTH_SECRET="$SECRET" STUB_BACKEND_PORT="$BACKEND_PORT" node "$WORK/stub-backend.cjs" > "$WORK/backend.log" 2>&1 &
BACKEND_PID=$!
for _ in $(seq 1 50); do
    grep -q ready "$WORK/backend.log" && break
    sleep 0.1
done
if ! grep -q ready "$WORK/backend.log"; then
    echo "❌ stub backend failed to start on :$BACKEND_PORT (port in use?)"
    cat "$WORK/backend.log"
    exit 1
fi

(
    cd "$FRONTEND"
    DEV_AUTH_SECRET="$SECRET" npx vite --port "$VITE_PORT" --strictPort --host 127.0.0.1 > "$WORK/vite.log" 2>&1
) &
VITE_PID=$!
VITE_UP=1
for _ in $(seq 1 100); do
    if curl -sS --noproxy '*' -o /dev/null "http://127.0.0.1:$VITE_PORT/" 2>/dev/null; then
        VITE_UP=0
        break
    fi
    sleep 0.2
done
if [ "$VITE_UP" != "0" ]; then
    echo "❌ vite dev server failed to start on :$VITE_PORT"
    cat "$WORK/vite.log"
    exit 1
fi

echo "1. dev-server proxy mints a token through the secret-injecting route"
code="$(curl -sS --noproxy '*' -o "$WORK/token.json" -w '%{http_code}' \
    -X POST "http://127.0.0.1:$VITE_PORT/dev-auth/token" \
    -H 'Content-Type: application/json' -d '{"userId":"user-1"}')"
check "proxy route returns 200" "$([ "$code" = "200" ] && echo 0 || echo 1)"
check "proxy route returns an access token" "$(grep -q '"accessToken":"e2e-dev-token"' "$WORK/token.json" && echo 0 || echo 1)"

echo "2. the raw backend dev-auth path is rejected without the secret"
code="$(curl -sS --noproxy '*' -o /dev/null -w '%{http_code}' \
    -X POST "http://127.0.0.1:$VITE_PORT/api/dev/auth/token" \
    -H 'Content-Type: application/json' -d '{"userId":"user-1"}')"
check "unproxied path returns 401" "$([ "$code" = "401" ] && echo 0 || echo 1)"

echo "3. the server never hands the secret to the browser"
curl -sS --noproxy '*' "http://127.0.0.1:$VITE_PORT/src/api/index.ts" > "$WORK/served-module.js"
curl -sS --noproxy '*' "http://127.0.0.1:$VITE_PORT/src/api/dev/dev-auth.ts" > "$WORK/served-dev-auth.js"
check "served bootstrap module omits the secret" "$(grep -q "$SECRET" "$WORK/served-module.js" && echo 1 || echo 0)"
check "served dev-auth module omits the secret" "$(grep -q "$SECRET" "$WORK/served-dev-auth.js" && echo 1 || echo 0)"
check "served dev-auth module uses the dev-only proxy path" "$(grep -q 'dev-auth/token' "$WORK/served-dev-auth.js" && echo 0 || echo 1)"

echo "4. the dev-server proxy fails closed when DEV_AUTH_SECRET is unset"
(
    cd "$FRONTEND"
    env -u DEV_AUTH_SECRET npx vite --port "$NO_SECRET_VITE_PORT" --strictPort --host 127.0.0.1 > "$WORK/vite-nosecret.log" 2>&1
) &
VITE_NOSECRET_PID=$!
NO_SECRET_UP=1
for _ in $(seq 1 100); do
    if curl -sS --noproxy '*' -o /dev/null "http://127.0.0.1:$NO_SECRET_VITE_PORT/" 2>/dev/null; then
        NO_SECRET_UP=0
        break
    fi
    sleep 0.2
done
if [ "$NO_SECRET_UP" != "0" ]; then
    echo "❌ vite dev server (no secret) failed to start on :$NO_SECRET_VITE_PORT"
    cat "$WORK/vite-nosecret.log"
    exit 1
fi
code="$(curl -sS --noproxy '*' -o /dev/null -w '%{http_code}' \
    -X POST "http://127.0.0.1:$NO_SECRET_VITE_PORT/dev-auth/token" \
    -H 'Content-Type: application/json' -d '{"userId":"user-1"}')"
check "proxy route returns 401 without the secret" "$([ "$code" = "401" ] && echo 0 || echo 1)"
check "vite warns that DEV_AUTH_SECRET is unset" "$(grep -q 'DEV_AUTH_SECRET is not set' "$WORK/vite-nosecret.log" && echo 0 || echo 1)"

echo ""
if [ "$FAILURES" -eq 0 ]; then
    echo "=== PASS ==="
else
    echo "=== FAIL ($FAILURES) ==="
    exit 1
fi
