#!/usr/bin/env bash
# Decode JWT payload (no signature verification — dev troubleshooting only).
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <access_token>" >&2
  exit 1
fi

TOKEN="$1"
PAYLOAD="$(echo "$TOKEN" | cut -d. -f2)"
# pad base64url
PAD=$(( (4 - ${#PAYLOAD} % 4) % 4 ))
PAYLOAD="${PAYLOAD}$(printf '=%.0s' $(seq 1 "$PAD"))"
echo "$PAYLOAD" | tr '_-' '/+' | base64 -d 2>/dev/null | python3 -m json.tool

echo ""
echo "Expected for Media Platform: tenantId, roles (or groups), optional platform_user_id"
