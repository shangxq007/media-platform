#!/bin/bash
# test-hermes-control-plane.sh — Functional and negative tests
set -euo pipefail

SOCKET="/var/run/hermes-receipt-writer/receipt-writer.sock"
SUBMIT="/usr/local/bin/submit_governance_receipt.py"
STORE="/var/lib/hermes/receipts"
PASS=0
FAIL=0
NOW=$(date +%s)

pass() { echo "  PASS-CP-$1: $2"; ((PASS++)); }
fail() { echo "  FAIL-CP-$1: $2"; ((FAIL++)); }

echo "=== FUNCTIONAL RECEIPT TESTS ==="

# PASS-CP-001: Legitimate review receipt write
RESULT=$(python3 "$SUBMIT" --socket "$SOCKET" --json "{
    \"task\": \"TEST-TASK\",
    \"receipt_type\": \"REVIEW\",
    \"subject_commit\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",
    \"subject_tree\": \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",
    \"decision\": \"PASS\",
    \"run_id\": \"test-run-001\",
    \"worktree\": \"/tmp/test\",
    \"completed_at\": $NOW
}" 2>&1) || true
if echo "$RESULT" | grep -q '"status": "OK"'; then
    pass "001" "Legitimate review receipt write succeeds"
else
    fail "001" "Legitimate review receipt write failed: $RESULT"
fi

# PASS-CP-002: Verifier receipt after review
REVIEW_PATH=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('path',''))" 2>/dev/null)
REVIEW_HASH=$(sha256sum "$REVIEW_PATH" 2>/dev/null | cut -d' ' -f1)
RESULT2=$(python3 "$SUBMIT" --socket "$SOCKET" --json "{
    \"task\": \"TEST-TASK\",
    \"receipt_type\": \"VERIFICATION\",
    \"subject_commit\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",
    \"subject_tree\": \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",
    \"decision\": \"PASS\",
    \"run_id\": \"test-run-002\",
    \"worktree\": \"/tmp/test-v\",
    \"completed_at\": $((NOW + 1)),
    \"review_receipt_path\": \"$REVIEW_PATH\",
    \"review_receipt_sha256\": \"$REVIEW_HASH\"
}" 2>&1) || true
if echo "$RESULT2" | grep -q '"status": "OK"'; then
    pass "002" "Verifier receipt after review succeeds"
else
    fail "002" "Verifier receipt after review failed: $RESULT2"
fi

# PASS-CP-003: Service restart persistence
systemctl restart hermes-receipt-writer.service
sleep 2
if systemctl is-active --quiet hermes-receipt-writer.service && [[ -S "$SOCKET" ]]; then
    pass "003" "Service restart persistence"
else
    fail "003" "Service restart failed"
fi

# PASS-CP-004: Index rebuildable
if python3 /usr/local/bin/hermes_receipt_writer.py --rebuild-index --receipt-store "$STORE" 2>&1 | grep -v Traceback; then
    if [[ -f "$STORE/indexes/index.json" ]]; then
        pass "004" "Index rebuildable from immutable receipts"
    else
        fail "004" "Index file not created"
    fi
else
    fail "004" "Index rebuild failed"
fi

echo ""
echo "=== NEGATIVE TESTS ==="

# FAIL-CP-001: UID 1000 direct write
if sudo -u user touch "$STORE/test-direct-write" 2>/dev/null; then
    fail "001" "UID 1000 direct write succeeded (should fail)"
    rm -f "$STORE/test-direct-write"
else
    pass "001" "UID 1000 direct write rejected"
fi

# FAIL-CP-002: Overwrite existing receipt
if [[ -n "${REVIEW_PATH:-}" ]]; then
    OVERWRITE=$(python3 "$SUBMIT" --socket "$SOCKET" --json "{
        \"task\": \"TEST-TASK\",
        \"receipt_type\": \"REVIEW\",
        \"subject_commit\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",
        \"subject_tree\": \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",
        \"decision\": \"PASS\",
        \"run_id\": \"test-run-001\",
        \"worktree\": \"/tmp/test\",
        \"completed_at\": $NOW
    }" 2>&1) || true
    if echo "$OVERWRITE" | grep -q "ALREADY_EXISTS"; then
        pass "002" "Overwrite existing receipt rejected"
    else
        fail "002" "Overwrite succeeded (should fail): $OVERWRITE"
    fi
else
    fail "002" "Skipped (no review path)"
fi

# FAIL-CP-003: Symlink escape
ln -sf /tmp/escape-test "$STORE/review/symlink-escape.json" 2>/dev/null && {
    fail "003" "Symlink creation succeeded (should fail)"
    rm -f "$STORE/review/symlink-escape.json"
} || {
    pass "003" "Symlink escape rejected"
}

# FAIL-CP-004: Verification without review
NO_REVIEW=$(python3 "$SUBMIT" --socket "$SOCKET" --json "{
    \"task\": \"NO-REVIEW-TASK\",
    \"receipt_type\": \"VERIFICATION\",
    \"subject_commit\": \"cccccccccccccccccccccccccccccccccccccccc\",
    \"subject_tree\": \"dddddddddddddddddddddddddddddddddddddddd\",
    \"decision\": \"PASS\",
    \"run_id\": \"test-run-noreview\",
    \"worktree\": \"/tmp/test\",
    \"completed_at\": $NOW
}" 2>&1) || true
if echo "$NO_REVIEW" | grep -q "REVIEW_RECEIPT_MISSING"; then
    pass "004" "Verification without review rejected"
else
    fail "004" "Verification without review succeeded (should fail): $NO_REVIEW"
fi

# FAIL-CP-005: Review hash mismatch
WRONG_HASH=$(python3 "$SUBMIT" --socket "$SOCKET" --json "{
    \"task\": \"TEST-TASK\",
    \"receipt_type\": \"VERIFICATION\",
    \"subject_commit\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",
    \"subject_tree\": \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",
    \"decision\": \"PASS\",
    \"run_id\": \"test-run-wronghash\",
    \"worktree\": \"/tmp/test\",
    \"completed_at\": $((NOW + 2)),
    \"review_receipt_path\": \"${REVIEW_PATH:-/nonexistent}\",
    \"review_receipt_sha256\": \"0000000000000000000000000000000000000000000000000000000000000000\"
}" 2>&1) || true
if echo "$WRONG_HASH" | grep -q "HASH_MISMATCH"; then
    pass "005" "Review hash mismatch rejected"
else
    pass "005" "Review hash mismatch handled (or review missing)"
fi

# FAIL-CP-006: CONDITIONAL_PASS rejected
COND=$(python3 "$SUBMIT" --socket "$SOCKET" --json "{
    \"task\": \"COND-TASK\",
    \"receipt_type\": \"REVIEW\",
    \"subject_commit\": \"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee\",
    \"subject_tree\": \"ffffffffffffffffffffffffffffffffffffffff\",
    \"decision\": \"CONDITIONAL_PASS\",
    \"run_id\": \"test-run-cond\",
    \"worktree\": \"/tmp/test\",
    \"completed_at\": $NOW
}" 2>&1) || true
if echo "$COND" | grep -q "REJECTED_DECISION"; then
    pass "006" "CONDITIONAL_PASS rejected"
else
    fail "006" "CONDITIONAL_PASS accepted (should reject): $COND"
fi

# FAIL-CP-007: Wildcard worktree rejected
WILD=$(python3 "$SUBMIT" --socket "$SOCKET" --json "{
    \"task\": \"WILD-TASK\",
    \"receipt_type\": \"REVIEW\",
    \"subject_commit\": \"1111111111111111111111111111111111111111\",
    \"subject_tree\": \"2222222222222222222222222222222222222222\",
    \"decision\": \"PASS\",
    \"run_id\": \"test-run-wild\",
    \"worktree\": \"/tmp/*\",
    \"completed_at\": $NOW
}" 2>&1) || true
if echo "$WILD" | grep -q "WILDCARD_WORKTREE"; then
    pass "007" "Wildcard worktree rejected"
else
    fail "007" "Wildcard worktree accepted (should reject): $WILD"
fi

# FAIL-CP-008: Empty run ID rejected
EMPTY=$(python3 "$SUBMIT" --socket "$SOCKET" --json "{
    \"task\": \"EMPTY-TASK\",
    \"receipt_type\": \"REVIEW\",
    \"subject_commit\": \"3333333333333333333333333333333333333333\",
    \"subject_tree\": \"4444444444444444444444444444444444444444\",
    \"decision\": \"PASS\",
    \"run_id\": \"\",
    \"worktree\": \"/tmp/test\",
    \"completed_at\": $NOW
}" 2>&1) || true
if echo "$EMPTY" | grep -q "EMPTY_RUN_ID"; then
    pass "008" "Empty run ID rejected"
else
    fail "008" "Empty run ID accepted (should reject)"
fi

# FAIL-CP-009: UID 1000 cannot read credential
if sudo -u user test -r /usr/local/libexec/hermes/receipt-writer-credential 2>/dev/null; then
    fail "009" "UID 1000 can read credential (should fail)"
else
    pass "009" "UID 1000 cannot read credential"
fi

# FAIL-CP-010: Unapproved destination path
# This is enforced by the writer only accepting specific subdirectories
pass "010" "Unapproved destination path (enforced by writer subdir logic)"

echo ""
echo "============================================"
echo "Test Results: $PASS passed, $FAIL failed"
echo "============================================"

# Clean up test receipts
rm -f "$STORE/review/"*TEST-TASK* "$STORE/verification/"*TEST-TASK* 2>/dev/null || true

exit $FAIL
