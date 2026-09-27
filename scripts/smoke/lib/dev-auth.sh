#!/usr/bin/env bash
# shellcheck shell=bash
#
# AUTH-DEV-AUTH-CALLER-ADAPT-001 — shared dev-auth handling for smoke scripts.
#
# The backend `/api/dev/auth/token` endpoint requires the `X-Dev-Auth-Secret`
# header (see DevAuthSecretGuard / DevAuthController). The caller must therefore
# present the shared secret. This helper:
#   * reads DEV_AUTH_SECRET from the environment, or from a .env file;
#   * never hardcodes the secret and never prints it;
#   * FAILS CLOSED (non-zero) with setup guidance when the secret is missing.
#
# Usage:
#   SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
#   . "$SCRIPT_DIR/lib/dev-auth.sh"
#   smoke_require_dev_auth_secret || exit 1
#   TOKEN="$(smoke_dev_auth_token "$API_BASE" "$TENANT_ID" smoke-user)" || exit 1

# Backend path of the dev token endpoint (override with SMOKE_DEV_AUTH_TOKEN_PATH).
SMOKE_DEV_AUTH_TOKEN_PATH="${SMOKE_DEV_AUTH_TOKEN_PATH:-/api/dev/auth/token}"

# Extract DEV_AUTH_SECRET from a KEY=VALUE .env file without sourcing the whole
# file (so unrelated variables are never clobbered).
_smoke_dev_auth_read_secret_from_file() {
    local file="$1" line
    [ -f "$file" ] || return 1
    line="$(grep -E '^[[:space:]]*(export[[:space:]]+)?DEV_AUTH_SECRET[[:space:]]*=' "$file" 2>/dev/null | tail -n 1)"
    [ -n "$line" ] || return 1
    line="${line#*=}"
    line="${line%$'\r'}"
    line="$(printf '%s' "$line" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
    case "$line" in
        \"*) line="${line#\"}"; line="${line%\"}" ;;
        \'*) line="${line#\'}"; line="${line%\'}" ;;
    esac
    [ -n "$line" ] || return 1
    DEV_AUTH_SECRET="$line"
    export DEV_AUTH_SECRET
}

# Try to populate DEV_AUTH_SECRET from a .env file. Returns 0 on success.
smoke_load_dev_auth_secret() {
    [ -n "${DEV_AUTH_SECRET:-}" ] && return 0
    local repository_root candidate
    repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
    local candidates=()
    [ -n "${SMOKE_DEV_AUTH_ENV_FILE:-}" ] && candidates+=("$SMOKE_DEV_AUTH_ENV_FILE")
    candidates+=(
        "$PWD/.env"
        "$PWD/.env.local"
        "$repository_root/.env"
        "$repository_root/.env.local"
        "$repository_root/scripts/smoke/.env"
    )
    for candidate in "${candidates[@]}"; do
        if _smoke_dev_auth_read_secret_from_file "$candidate"; then
            return 0
        fi
    done
    return 1
}

# Require DEV_AUTH_SECRET (from the environment or a .env file). Fail closed.
smoke_require_dev_auth_secret() {
    if [ -z "${DEV_AUTH_SECRET:-}" ]; then
        smoke_load_dev_auth_secret || true
    fi
    if [ -z "${DEV_AUTH_SECRET:-}" ]; then
        cat >&2 <<'EOF'

❌ DEV_AUTH_SECRET is not set — refusing to continue (fail closed).

The dev auth endpoint requires the X-Dev-Auth-Secret header, so no dev token can
be minted without the shared secret. Export it in the shell that runs this smoke
test, or put it in a .env file (cwd, repo root, or scripts/smoke/.env):

    export DEV_AUTH_SECRET='<value the backend was started with>'

The secret is never hardcoded and must never be committed or passed on the
command line.
EOF
        return 1
    fi
    return 0
}

# Mint a dev token. The secret travels through a curl config on stdin so it does
# not appear in the process argument list. Prints the token, or returns 1.
smoke_dev_auth_token() {
    local api_base="$1" tenant_id="$2" user_id="$3"
    local path="${SMOKE_DEV_AUTH_TOKEN_PATH}"
    local response token
    response="$(curl -sS --config - 2>/dev/null <<EOF
url = "${api_base}${path}"
request = "POST"
header = "Content-Type: application/json"
header = "X-Dev-Auth-Secret: ${DEV_AUTH_SECRET:-}"
data = "{\"tenantId\":\"${tenant_id}\",\"userId\":\"${user_id}\"}"
EOF
)" || return 1
    token="$(printf '%s' "$response" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)"
    [ -n "$token" ] || return 1
    printf '%s' "$token"
}
