#!/usr/bin/env bash
# VERSION_COMPATIBILITY_GOVERNANCE_FOUNDATION_V1 / TYPED-ARTIFACT-API-GOVERNANCE-001 —
# API contract governance gate.
#
# Scope: structural lint (Spectral 6.14.3, pinned in contracts/package.json) + machine-gated
# breaking-change detection (oasdiff v1.28.0, pinned and vendored).
#
# Breaking-change detection contract (TYPED-ARTIFACT-API-GOVERNANCE-001):
#   * Detection never greps human-readable text. It uses oasdiff's own verdict twice over: the
#     process exit code (`--fail-on ERR`: 0 = no error-level change, 1 = error-level breaking
#     change) and the structured report (`--format json`, each finding carries `level`:
#     1 info / 2 warning / 3 error). Case, colour, wording or column changes in oasdiff output
#     can therefore no longer turn a break into a silent pass.
#   * A document oasdiff cannot load (exit >= 100, or "failed to load" on stderr) is a
#     FAIL-CLOSED gate error — never treated as a detected break and never treated as safe.
#   * A verdict report that cannot be read (unparseable JSON, non-list JSON, non-integer level, or an
#     empty/crashed reader) is likewise a FAIL-CLOSED gate error: the reader emits the "-1 -1 -1"
#     sentinel and verdict_fails_closed() refuses to turn it into a pass.
#   * The gate proves its own detection on every run against two checked-in controls plus the
#     unreadable-report control:
#       - contracts/http/media-api/openapi.nonbreaking.yaml (additive)   -> must be NON-breaking;
#       - contracts/http/media-api/openapi.breaking.yaml    (intentional) -> must be BREAKING.
#       - an unparseable verdict report                                   -> must fail closed.
#     Each control must also be a real difference from the base (`oasdiff diff` non-empty), so a
#     control that degenerates into "identical to base" fails the self-test instead of passing it.
#
# Governance pair: contracts/http/media-api/openapi.base.yaml (frozen baseline) vs the candidate
# document. The candidate is the checked-in contracts/http/media-api/openapi.candidate.yaml, or the
# file named by API_CONTRACT_CANDIDATE_FILE (the acceptance checks drive a known non-breaking and a
# known breaking candidate through this same code path via that variable).
#
#   Acceptance / regression runs (each must exit with the shown verdict):
#     bash scripts/check-api-contract-governance.sh
#       -> reports the checked-in governance pair truthfully (a breaking candidate fails the gate)
#     API_CONTRACT_CANDIDATE_FILE=contracts/http/media-api/openapi.nonbreaking.yaml \
#       bash scripts/check-api-contract-governance.sh    # non-breaking candidate => 0 FAIL, exit 0
#     API_CONTRACT_CANDIDATE_FILE=contracts/http/media-api/openapi.breaking.yaml \
#       bash scripts/check-api-contract-governance.sh    # breaking candidate     => >=1 FAIL, exit 1
#
# oasdiff binary provisioning:
#   * Pinned, vendored binary at scripts/tools/oasdiff (linux/amd64, v1.28.0). Before any
#     comparison the gate verifies the binary's `--version` and its SHA-256 against the pin in
#     scripts/tools/oasdiff.sha256. No manual provisioning step is required.
#   * Re-provision / other linux/amd64 hosts: bash scripts/tools/download-oasdiff.sh [destination]
#     (same pin; tarball and extracted-binary digests verified).
#   * Pre-provisioned binary elsewhere: OASDIFF_BIN=/path/to/oasdiff (the pinned --version is still
#     enforced; the digest is reported).
#   See scripts/tools/oasdiff.sha256 for the pinned URL, platform, version and digests.
set -u
cd "$(dirname "$0")/.."
if [ ! -d contracts/node_modules ]; then
  (cd contracts && npm install --no-audit --no-fund >/dev/null 2>&1)
fi

BASE_YAML="contracts/http/media-api/openapi.base.yaml"
CANDIDATE_YAML="${API_CONTRACT_CANDIDATE_FILE:-contracts/http/media-api/openapi.candidate.yaml}"
NONBREAKING_YAML="contracts/http/media-api/openapi.nonbreaking.yaml"
BREAKING_YAML="contracts/http/media-api/openapi.breaking.yaml"
OASDIFF_VENDORED="$PWD/scripts/tools/oasdiff"
OASDIFF_PIN="$PWD/scripts/tools/oasdiff.sha256"

PASS=0; FAIL=0
ck() { if [ "$1" = "0" ]; then PASS=$((PASS+1)); echo "   PASS: $2"; else FAIL=$((FAIL+1)); echo "   FAIL: $2"; fi; }

echo "== Spectral 6.14.3 (pinned) =="
SPECTRAL="$PWD/contracts/node_modules/.bin/spectral"
"$SPECTRAL" --version >/dev/null 2>&1; ck $? "spectral available"
"$SPECTRAL" lint contracts/http/media-api/openapi.base.yaml \
  --ruleset contracts/governance/api-style.yaml >/tmp/spectral-out.txt 2>&1
ck $? "spectral lint passes (base)"

echo "== Checked-in runtime OpenAPI authority =="
python3 - <<'PY'
import json
from pathlib import Path

p = Path("docs/api/openapi-preview-current.json")
doc = json.loads(p.read_text(encoding="utf-8"))
assert doc.get("openapi", "").startswith("3.1."), doc.get("openapi")
assert isinstance(doc.get("paths"), dict) and doc["paths"], "paths missing"
operations = 0
for path, item in doc["paths"].items():
    assert path.startswith("/"), path
    for method, operation in item.items():
        if method.lower() not in {"get", "put", "post", "delete", "options", "head", "patch", "trace"}:
            continue
        operations += 1
        assert operation.get("operationId"), f"operationId missing: {method} {path}"
assert operations > 0
print(f"authority artifact: {p} openapi={doc['openapi']} operations={operations}")
PY
ck $? "checked-in runtime artifact is valid OpenAPI 3.1.x with operationIds"

python3 scripts/api/verify-composition-openapi.py
ck $? "composition controller, candidate contract and runtime export agree"

# ---------------------------------------------------------------------------
# oasdiff binary: resolve, verify against the pin, fail closed.
# ---------------------------------------------------------------------------
echo "== oasdiff binary (pinned v1.28.0, vendored) =="
PIN_VERSION="$(sed -n 's/^version:[[:space:]]*//p' "$OASDIFF_PIN" | head -1)"
PIN_BINARY_SHA="$(sed -n 's/^sha256_binary:[[:space:]]*//p' "$OASDIFF_PIN" | head -1)"
if [ -z "$PIN_VERSION" ] || [ -z "$PIN_BINARY_SHA" ]; then
  echo "   FAIL: pin file unreadable or incomplete: $OASDIFF_PIN"
  FAIL=$((FAIL+1))
  echo ""
  echo "API-GOVERNANCE-GATE: $PASS PASS, $FAIL FAIL"
  exit $FAIL
fi

OASDIFF="${OASDIFF_BIN:-$OASDIFF_VENDORED}"
OASDIFF_USABLE=1
if [ ! -x "$OASDIFF" ]; then
  OASDIFF_USABLE=0
  ck 1 "oasdiff binary missing/not executable at $OASDIFF (provision with: bash scripts/tools/download-oasdiff.sh)"
else
  OASDIFF_VERSION="$("$OASDIFF" --version 2>/dev/null | awk '{print $NF}')"
  OASDIFF_SHA="$(sha256sum "$OASDIFF" | awk '{print $1}')"
  if [ "$OASDIFF_VERSION" != "$PIN_VERSION" ]; then
    OASDIFF_USABLE=0
    ck 1 "oasdiff version mismatch: got '${OASDIFF_VERSION:-<none>}', pinned '$PIN_VERSION'"
  elif [ -n "${OASDIFF_BIN:-}" ]; then
    echo "   note: OASDIFF_BIN override in use (sha256=$OASDIFF_SHA)"
    if [ "$OASDIFF_SHA" = "$PIN_BINARY_SHA" ]; then
      echo "   note: override matches the pinned digest"
    fi
    ck 0 "oasdiff $OASDIFF_VERSION available (operator-provided path; version pin enforced)"
  elif [ "$OASDIFF_SHA" = "$PIN_BINARY_SHA" ]; then
    ck 0 "vendored oasdiff $OASDIFF_VERSION verified (version + sha256 match the pin)"
  else
    OASDIFF_USABLE=0
    ck 1 "vendored oasdiff sha256 mismatch: got $OASDIFF_SHA, pinned $PIN_BINARY_SHA"
  fi
fi

# Structured readers: verdicts come from the JSON report, never from grepping text.
#
# A verdict that cannot be read is a GATE ERROR, never a pass: the reader prints the sentinel
# "-1 -1 -1" for unparseable JSON, non-list JSON, or any entry whose level is not an integer, and
# verdict_fails_closed() below turns that sentinel (and any non-numeric count) into a failure.
oasdiff_levels() {  # <json-file> -> "errors warnings infos" ("-1 -1 -1" when unreadable)
  python3 - "$1" <<'PY'
import json
import sys

try:
    data = json.load(open(sys.argv[1], encoding="utf-8"))
except Exception:
    print("-1 -1 -1")
    raise SystemExit(0)
if not isinstance(data, list):
    print("-1 -1 -1")
    raise SystemExit(0)
try:
    levels = [int(entry.get("level", 0)) for entry in data]
except Exception:
    # A level that is not an integer makes the whole report unreadable -> fail-closed sentinel.
    print("-1 -1 -1")
    raise SystemExit(0)
print(sum(1 for level in levels if level >= 3),
      sum(1 for level in levels if level == 2),
      sum(1 for level in levels if level == 1))
PY
}

# verdict_fails_closed <load-fail> <errors> -> exit 0 when the pair must fail the gate.
# Fail-closed cases: the document did not load, the reader printed its negative sentinel, or the
# count is not even a number (reader crashed / empty output). Only a real, non-negative count of
# error-level findings is evaluated normally — a clean 0 is still accepted.
verdict_fails_closed() {
  [ "$1" = "1" ] && return 0
  case "$2" in
    ''|*[!0-9-]*) return 0 ;;
  esac
  [ "$2" -lt 0 ] && return 0
  return 1
}

oasdiff_diff_nonempty() {  # <json-file> -> exit 0 when the structural diff is non-empty
  python3 - "$1" <<'PY'
import json
import sys

try:
    data = json.load(open(sys.argv[1], encoding="utf-8"))
except Exception:
    raise SystemExit(1)
raise SystemExit(0 if data else 1)
PY
}

# oasdiff_compare <base> <revision> <tag>; sets RC, LOAD_FAIL, ERRORS, WARNINGS, INFOS.
oasdiff_compare() {
  "$OASDIFF" breaking --fail-on ERR --format json "$1" "$2" \
      >"/tmp/oasdiff-$3.json" 2>"/tmp/oasdiff-$3.err"
  RC=$?
  LOAD_FAIL=0
  if [ "$RC" -ge 100 ] || grep -qi 'failed to load' "/tmp/oasdiff-$3.err"; then
    LOAD_FAIL=1
  fi
  read -r ERRORS WARNINGS INFOS < <(oasdiff_levels "/tmp/oasdiff-$3.json")
}

if [ "$OASDIFF_USABLE" = "1" ]; then
  echo "== oasdiff self-test: non-breaking control must be accepted =="
  "$OASDIFF" diff --format json "$BASE_YAML" "$NONBREAKING_YAML" \
      >/tmp/oasdiff-nonbreaking-diff.json 2>/dev/null
  oasdiff_compare "$BASE_YAML" "$NONBREAKING_YAML" nonbreaking
  if [ "$LOAD_FAIL" = "0" ] && [ "$RC" = "0" ] && [ "$ERRORS" = "0" ] \
      && oasdiff_diff_nonempty /tmp/oasdiff-nonbreaking-diff.json; then
    ck 0 "non-breaking control accepted ($ERRORS error, $WARNINGS warning, $INFOS info; documents differ)"
  else
    ck 1 "non-breaking control mis-handled (load_fail=$LOAD_FAIL rc=$RC errors=$ERRORS)"
    sed -n '1,3p' /tmp/oasdiff-nonbreaking.err
  fi

  echo "== oasdiff self-test: intentional-breaking control must be detected =="
  oasdiff_compare "$BASE_YAML" "$BREAKING_YAML" breaking
  if [ "$LOAD_FAIL" = "0" ] && [ "$RC" = "1" ] && [ "$ERRORS" -ge 1 ]; then
    ck 0 "intentional breaking change detected ($ERRORS error, $WARNINGS warning, $INFOS info)"
    # Display only — the verdict above is the exit code + JSON level counts.
    "$OASDIFF" breaking --fail-on ERR "$BASE_YAML" "$BREAKING_YAML" 2>/dev/null \
      | grep -m1 '^error' || true
  else
    ck 1 "intentional breaking change NOT detected (load_fail=$LOAD_FAIL rc=$RC errors=$ERRORS)"
    sed -n '1,3p' /tmp/oasdiff-breaking.err
  fi

  echo "== oasdiff self-test: unreadable verdict report must fail closed =="
  printf 'not-a-json-verdict-report\n' >/tmp/oasdiff-unreadable.json
  read -r UNREADABLE_ERRORS UNREADABLE_WARNINGS UNREADABLE_INFOS \
    < <(oasdiff_levels /tmp/oasdiff-unreadable.json)
  if [ "$UNREADABLE_ERRORS" = "-1" ] && [ "$UNREADABLE_WARNINGS" = "-1" ] \
      && [ "$UNREADABLE_INFOS" = "-1" ] \
      && verdict_fails_closed 0 "$UNREADABLE_ERRORS" \
      && ! verdict_fails_closed 0 0; then
    ck 0 "unreadable verdict report is fail-closed (sentinel $UNREADABLE_ERRORS; a clean 0-error verdict is still accepted)"
  else
    ck 1 "unreadable verdict report handling is wrong (sentinel '$UNREADABLE_ERRORS $UNREADABLE_WARNINGS $UNREADABLE_INFOS')"
  fi

  echo "== oasdiff breaking: base vs candidate (candidate must not break the frozen base) =="
  echo "   candidate: $CANDIDATE_YAML"
  if [ ! -f "$CANDIDATE_YAML" ]; then
    ck 1 "candidate document not found: $CANDIDATE_YAML"
  else
    oasdiff_compare "$BASE_YAML" "$CANDIDATE_YAML" candidate
    if verdict_fails_closed "$LOAD_FAIL" "$ERRORS"; then
      ck 1 "candidate verdict is unusable: document unloadable or report unreadable (fail-closed; load_fail=$LOAD_FAIL errors=${ERRORS:-<none>})"
      sed -n '1,3p' /tmp/oasdiff-candidate.err
    elif [ "$ERRORS" -gt 0 ]; then
      ck 1 "candidate introduces $ERRORS error-level breaking change(s) vs base ($WARNINGS warning, $INFOS info)"
      echo "   first breaking findings:"
      "$OASDIFF" breaking --fail-on ERR "$BASE_YAML" "$CANDIDATE_YAML" 2>/dev/null \
        | grep -m3 '^error' || true
    else
      ck 0 "candidate is non-breaking vs base ($ERRORS error, $WARNINGS warning, $INFOS info)"
    fi
  fi
else
  ck 1 "oasdiff unavailable/uncertified: self-test and breaking-change detection not run (fail-closed)"
fi

echo ""
echo "API-GOVERNANCE-GATE: $PASS PASS, $FAIL FAIL"
exit $FAIL
