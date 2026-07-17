#!/bin/bash
# verify-hermes-control-plane.sh — Post-install verification
set -euo pipefail

echo "=== CONTROL-PLANE VERIFICATION ==="
PASS=0
FAIL=0

check() {
    local desc="$1"
    shift
    if "$@" >/dev/null 2>&1; then
        echo "  PASS: $desc"
        ((PASS++))
    else
        echo "  FAIL: $desc"
        ((FAIL++))
    fi
}

echo "--- Receipt Store ---"
check "receipt store exists" test -d /var/lib/hermes/receipts
check "receipt store owner root:root" test "$(stat -c '%U:%G' /var/lib/hermes/receipts)" = "root:root"
check "receipt store mode 0750" test "$(stat -c '%a' /var/lib/hermes/receipts)" = "750"
check "review dir exists" test -d /var/lib/hermes/receipts/review
check "verification dir exists" test -d /var/lib/hermes/receipts/verification
check "control-plane dir exists" test -d /var/lib/hermes/receipts/control-plane
check "rejected dir exists" test -d /var/lib/hermes/receipts/rejected
check "indexes dir exists" test -d /var/lib/hermes/receipts/indexes

echo "--- Receipt Writer ---"
check "receipt writer unit exists" test -f /etc/systemd/system/hermes-receipt-writer.service
check "receipt writer unit owner root" test "$(stat -c '%U:%G' /etc/systemd/system/hermes-receipt-writer.service)" = "root:root"
check "receipt writer active" systemctl is-active --quiet hermes-receipt-writer.service
check "receipt writer enabled" systemctl is-enabled --quiet hermes-receipt-writer.service
check "receipt writer script exists" test -f /usr/local/bin/hermes_receipt_writer.py
check "submit client exists" test -f /usr/local/bin/submit_governance_receipt.py

echo "--- Socket ---"
check "socket exists" test -S /var/run/hermes-receipt-writer/receipt-writer.sock
check "socket owner root" test "$(stat -c '%U:%G' /var/run/hermes-receipt-writer/receipt-writer.sock)" = "root:root"

echo "--- Credential ---"
check "credential exists" test -f /usr/local/libexec/hermes/receipt-writer-credential
check "credential mode 0600" test "$(stat -c '%a' /usr/local/libexec/hermes/receipt-writer-credential)" = "600"

echo "--- Access Control ---"
check "UID 1000 cannot write receipt store" ! sudo -u user test -w /var/lib/hermes/receipts 2>/dev/null
check "UID 1000 can read indexes" sudo -u user test -r /var/lib/hermes/receipts/indexes 2>/dev/null || true

echo "--- Systemd Hardening ---"
UNIT=/etc/systemd/system/hermes-receipt-writer.service
check "NoNewPrivileges" grep -q "NoNewPrivileges=yes" "$UNIT"
check "ProtectSystem=strict" grep -q "ProtectSystem=strict" "$UNIT"
check "PrivateTmp" grep -q "PrivateTmp=yes" "$UNIT"
check "CapabilityBoundingSet=" grep -q "CapabilityBoundingSet=$" "$UNIT" || grep -q "CapabilityBoundingSet=\s*$" "$UNIT"
check "LockPersonality" grep -q "LockPersonality=yes" "$UNIT"

echo "--- Existing Services ---"
check "gateway active" systemctl is-active --quiet hermes-gateway.service
check "approved-skills active" systemctl is-active --quiet hermes-approved-skills.service
check "skill mounts present" mount | grep -q "kanban-multi-agent-orchestration"

echo "--- Script Syntax ---"
check "receipt writer python syntax" python3 -m py_compile /usr/local/bin/hermes_receipt_writer.py
check "submit client python syntax" python3 -m py_compile /usr/local/bin/submit_governance_receipt.py

echo ""
echo "============================================"
echo "Results: $PASS passed, $FAIL failed"
echo "============================================"
exit $FAIL
