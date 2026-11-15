#!/usr/bin/env bash
# Regression test for the CIPG1 / CIPG2EG1 credential-residue scan.
#
# Guards the two properties the fix must keep together:
#   1. credential-shaped tokens are still detected (no weakening), and
#   2. legitimate identifiers that merely contain "sk-" (for example the
#      `render-task-<64 hex>` Artifact ids recorded in the BMF R1 reports) are not reported.
#
# The pattern is read from scripts/guards/credential-pattern.sh — the same file the architecture
# guard sources — so this test cannot diverge from the real check. All samples are synthetic.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
. "$ROOT_DIR/scripts/guards/credential-pattern.sh"

FAILED=0
CHECKS=0
pass() { printf 'PASS  %s\n' "$1"; CHECKS=$((CHECKS + 1)); }
fail() { printf 'FAIL  %s\n' "$1" >&2; FAILED=1; CHECKS=$((CHECKS + 1)); }

# pipefail would surface git grep's "no match" status (1) as a pipeline failure, so every count runs
# in a subshell with pipefail disabled. The pipeline shape is identical to the architecture guard.
count_in() {
    ( set +o pipefail
      git grep -InE "$CREDENTIAL_RESIDUE_PATTERN" -- "$@" 2>/dev/null \
          | grep -vE "$CREDENTIAL_RESIDUE_EXCLUSIONS" | wc -l | tr -d ' ' )
}
count_in_repo() {
    local repo="$1"; shift
    ( set +o pipefail
      git -C "$repo" grep -InE "$CREDENTIAL_RESIDUE_PATTERN" -- "$@" 2>/dev/null \
          | grep -vE "$CREDENTIAL_RESIDUE_EXCLUSIONS" | wc -l | tr -d ' ' )
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
git -C "$TMP" init -q
git -C "$TMP" config user.email test@example.invalid
git -C "$TMP" config user.name test

ARTIFACT_ID="render-task-$(printf 'a%.0s' $(seq 1 64))"
printf '%s\n' "| Render master | \`$ARTIFACT_ID\` |" > "$TMP/artifact-id.md"
printf '%s\n' "$ARTIFACT_ID" > "$TMP/bare-artifact-id.md"
printf '%s\n' "prefix-disk-$(printf 'b%.0s' $(seq 1 32))" > "$TMP/disk-name.md"
printf '%s\n' "| \`render-task-$(printf 'f%.0s' $(seq 1 64))\` |" > "$TMP/second-artifact-id.md"

SK_TOKEN="sk-$(printf 'c%.0s' $(seq 1 32))"
printf '"%s"\n' "$SK_TOKEN" > "$TMP/quoted.md"
printf 'API_KEY=%s\n' "$SK_TOKEN" > "$TMP/assignment.md"
printf '%s\n' "$SK_TOKEN" > "$TMP/line-start.md"
printf 'token: ghp_%s\n' "$(printf 'd%.0s' $(seq 1 36))" > "$TMP/github.txt"
printf 'aws: AKIA%s\n' "$(printf 'E%.0s' $(seq 1 16))" > "$TMP/aws.txt"
printf 'slack: xoxb-%s\n' "$(printf '1%.0s' $(seq 1 12))" > "$TMP/slack.txt"
git -C "$TMP" add -A
git -C "$TMP" commit -qm fixtures

echo "== negative: identifiers that merely contain sk- =="
bad=0
for file in artifact-id.md bare-artifact-id.md second-artifact-id.md disk-name.md; do
    n=$(count_in_repo "$TMP" "$file")
    if [ "$n" != "0" ]; then fail "$file must not match (got $n)"; bad=$((bad + 1)); fi
done
[ "$bad" = "0" ] && pass "render-task-<hex>, a second bare artifact id and disk-<hex> are not credentials"

echo "== positive: credential-shaped tokens still fail =="
missing=0
for file in quoted.md assignment.md line-start.md github.txt aws.txt slack.txt; do
    n=$(count_in_repo "$TMP" "$file")
    if [ "$n" -lt 1 ]; then fail "$file must match (got $n)"; missing=$((missing + 1)); fi
done
[ "$missing" = "0" ] && pass "quoted, assigned, line-start sk- tokens plus ghp_/AKIA/xoxb samples are detected"

echo "== exit-code semantics: residue > 0 fails, residue == 0 passes =="
if [ "$(count_in_repo "$TMP" quoted.md)" -gt 0 ]; then
    pass "residue count is non-zero for a positive sample (the guard's zero-check fails closed)"
else
    fail "residue count must be non-zero for a positive sample"
fi
if [ "$(count_in_repo "$TMP" artifact-id.md)" = "0" ]; then
    pass "residue count is zero for the artifact-id sample (the guard's zero-check passes)"
else
    fail "residue count must be zero for the artifact-id sample"
fi

echo "== current tree: BMF R1 reports stay in scan scope and no longer match =="
for report in docs/architecture/BmfR1FinalCloseout.md docs/architecture/BmfR1FinalTestMatrix.md; do
    if ! git ls-files --error-unmatch "$report" >/dev/null 2>&1; then
        fail "$report is not tracked (it must stay in scan scope)"
        continue
    fi
    if ! grep -q 'render-task-' "$report"; then
        fail "$report no longer contains the artifact ids this test protects"
        continue
    fi
    n=$(count_in "$report")
    [ "$n" = "0" ] && pass "$report is scanned, contains render-task ids and reports no residue" \
        || fail "$report still reports $n credential residue(s)"
done

echo "== current tree: whole repository scan is clean =="
total=$(count_in '*.java' '*.kt' '*.sql' '*.md' '*.sh' '*.yml' '*.yaml' '*.json' '*.kts' '*.gradle')
[ "$total" = "0" ] && pass "repository credential residue = 0" || fail "repository credential residue = $total"

echo ""
if [ "$FAILED" = "0" ]; then
    echo "CREDENTIAL_RESIDUE_PATTERN_TEST: PASS ($CHECKS checks)"
    exit 0
fi
echo "CREDENTIAL_RESIDUE_PATTERN_TEST: FAIL" >&2
exit 1
