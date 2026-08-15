#!/usr/bin/env bash
# VERSION_COMPATIBILITY_GOVERNANCE_FOUNDATION_V1 — API contract governance gate.
# Spectral lint (pinned 6.14.3 via contracts/package.json) + oasdiff breaking-change
# checks (pinned binary v1.28.0, path via OASDIFF_BIN env or $PWD/scripts/tools/oasdiff).
# oasdiff: https://github.com/oasdiff/oasdiff/releases/download/v1.28.0/oasdiff_1.28.0_linux_amd64.tar.gz
set -u
cd "$(dirname "$0")/.."
if [ ! -d contracts/node_modules ]; then
  (cd contracts && npm install --no-audit --no-fund >/dev/null 2>&1)
fi
OASDIFF="${OASDIFF_BIN:-$PWD/scripts/tools/oasdiff}"
PASS=0; FAIL=0
ck() { if [ "$1" = "0" ]; then PASS=$((PASS+1)); echo "   PASS: $2"; else FAIL=$((FAIL+1)); echo "   FAIL: $2"; fi; }

echo "== Spectral 6.14.3 (pinned) =="
SPECTRAL="$PWD/contracts/node_modules/.bin/spectral"
"$SPECTRAL" --version >/dev/null 2>&1; ck $? "spectral available"
"$SPECTRAL" lint contracts/http/media-api/openapi.base.yaml \
  --ruleset contracts/governance/api-style.yaml >/tmp/spectral-out.txt 2>&1
ck $? "spectral lint passes (base)"

echo "== oasdiff breaking: base vs candidate (additive => non-breaking) =="
if [ -x "$OASDIFF" ]; then
  OUT=$("$OASDIFF" breaking contracts/http/media-api/openapi.base.yaml \
        contracts/http/media-api/openapi.candidate.yaml 2>&1)
  if [ $? -eq 0 ] && ! echo "$OUT" | grep -q '\[error\]\|error\['; then
    ck 0 "non-breaking additive diff accepted"
  else
    ck 1 "non-breaking diff rejected (unexpected)"
  fi

  echo "== oasdiff breaking: base vs intentional-breaking fixture (must detect) =="
  OUT2=$("$OASDIFF" breaking contracts/http/media-api/openapi.base.yaml \
         contracts/http/media-api/openapi.breaking.yaml 2>&1)
  if echo "$OUT2" | grep -q 'error'; then
    ck 0 "intentional breaking change detected by oasdiff"
    echo "$OUT2" | grep 'error' | head -1
  else
    ck 1 "breaking change NOT detected"
  fi
else
  ck 1 "oasdiff binary unavailable (set OASDIFF_BIN or install pinned v1.28.0)"
fi

echo ""
echo "API-GOVERNANCE-GATE: $PASS PASS, $FAIL FAIL"
exit $FAIL
