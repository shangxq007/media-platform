#!/usr/bin/env bash
# TYPED-ARTIFACT-API-GOVERNANCE-FIX-001 — reproducible controls for the API contract gate.
#
# Proves, from a clean checkout, that scripts/check-api-contract-governance.sh still:
#
#   1. accepts the checked-in governance pair (base vs candidate) with ZERO findings;
#   2. accepts an additive candidate (openapi.nonbreaking.yaml);
#   3. rejects an intentionally breaking candidate (openapi.breaking.yaml);
#   4. FAILS CLOSED when the verdict report cannot be read, proved with a version-certified
#      oasdiff stand-in (it answers `--version` with the pinned version, produces valid reports for
#      the two self-test controls, and refuses to produce a parseable report for the candidate).
#
# Control 4 is the regression control for the reviewed P2 defect: before the hardening the gate
# printed "candidate is non-breaking vs base (-1 error, ...)" and exited 0.
#
# Usage: bash scripts/test/api-contract-governance-gate-controls.sh
# Exits 0 only when all four controls behave as required.
set -u
cd "$(dirname "$0")/../.."

ROOT="$PWD"
CANDIDATE="contracts/http/media-api/openapi.candidate.yaml"
NONBREAKING="contracts/http/media-api/openapi.nonbreaking.yaml"
BREAKING="contracts/http/media-api/openapi.breaking.yaml"

PASS=0
FAIL=0
ok() { if [ "$1" = "0" ]; then PASS=$((PASS+1)); echo "   PASS: $2"; else FAIL=$((FAIL+1)); echo "   FAIL: $2"; fi; }

echo "== control 1: checked-in governance pair =="
OUT="$(bash scripts/check-api-contract-governance.sh 2>&1)"
RC=$?
echo "$OUT" | tail -2
if [ "$RC" = "0" ] && ! echo "$OUT" | grep -q 'FAIL:'; then
  ok 0 "checked-in pair accepted with zero findings (exit 0)"
else
  ok 1 "checked-in pair not clean (exit=$RC)"
fi

echo "== control 2: additive candidate must be accepted =="
OUT="$(API_CONTRACT_CANDIDATE_FILE="$NONBREAKING" bash scripts/check-api-contract-governance.sh 2>&1)"
RC=$?
if [ "$RC" = "0" ]; then
  ok 0 "additive candidate accepted (exit 0)"
else
  ok 1 "additive candidate rejected (exit=$RC)"
  echo "$OUT" | tail -5
fi

echo "== control 3: intentional breaking candidate must be rejected =="
OUT="$(API_CONTRACT_CANDIDATE_FILE="$BREAKING" bash scripts/check-api-contract-governance.sh 2>&1)"
RC=$?
if [ "$RC" != "0" ] && echo "$OUT" | grep -q 'error-level breaking change'; then
  ok 0 "breaking candidate rejected (exit=$RC, error-level finding reported)"
else
  ok 1 "breaking candidate NOT rejected (exit=$RC)"
  echo "$OUT" | tail -5
fi

echo "== control 4: unreadable verdict report must fail closed (version-certified stand-in) =="
STANDIN_DIR="$(mktemp -d)"
trap 'rm -rf "$STANDIN_DIR"' EXIT
cat >"$STANDIN_DIR/oasdiff-standin" <<'STANDIN'
#!/usr/bin/env bash
# Version-certified oasdiff stand-in for the P2 regression control.
#   --version            -> the pinned version (the gate enforces the version string)
#   diff --format json   -> a non-empty structural diff (self-test control 1 precondition)
#   breaking … nonbreaking.yaml -> valid report, no error-level findings, exit 0
#   breaking … breaking.yaml    -> valid report, one error-level finding, exit 1
#   breaking … candidate        -> UNPARSEABLE output with exit 0 (the defect's shape)
if [ "${1:-}" = "--version" ]; then
  echo "oasdiff version 1.28.0"
  exit 0
fi
args=" $* "
# Structural-diff precondition of self-test control 1: must be a non-empty report.
if [ "${1:-}" = "diff" ]; then
  printf '[{"level":1,"id":"endpoint-added"}]\n'
  exit 0
fi
case "$args" in
  *nonbreaking.yaml*)
    printf '[]\n'
    exit 0 ;;
  *breaking.yaml*)
    printf '[{"level":3,"id":"api-path-removed-without-deprecation"}]\n'
    exit 1 ;;
esac
printf 'this is not a json verdict report\n'
exit 0
STANDIN
chmod +x "$STANDIN_DIR/oasdiff-standin"

OUT="$(OASDIFF_BIN="$STANDIN_DIR/oasdiff-standin" bash scripts/check-api-contract-governance.sh 2>&1)"
RC=$?
echo "$OUT" | grep -E 'unreadable verdict report|candidate verdict is unusable' | head -2
if [ "$RC" != "0" ] \
    && echo "$OUT" | grep -q 'candidate verdict is unusable' \
    && ! echo "$OUT" | grep -q 'candidate is non-breaking'; then
  ok 0 "unreadable verdict report fails closed (exit=$RC)"
else
  ok 1 "unreadable verdict report did NOT fail closed (exit=$RC)"
  echo "$OUT" | tail -6
fi

echo ""
echo "API-CONTRACT-GATE-CONTROLS: $PASS PASS, $FAIL FAIL"
exit "$FAIL"
