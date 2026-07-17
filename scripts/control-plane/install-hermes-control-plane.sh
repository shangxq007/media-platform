#!/bin/bash
# install-hermes-control-plane.sh — Install control-plane governance components
#
# Usage:
#   sudo bash install-hermes-control-plane.sh [--dry-run] [--config PATH]
#
# Must be run as root.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TIMESTAMP="$(date +%Y%m%d%H%M%S)"
BACKUP_DIR="/var/backups/hermes-control-plane/${TIMESTAMP}"

DRY_RUN=false
CONFIG_FILE=""

# Parse args
while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run) DRY_RUN=true; shift ;;
        --config) CONFIG_FILE="$2"; shift 2 ;;
        --help) head -20 "$0" | tail -18; exit 0 ;;
        *) echo "ERROR: Unknown argument: $1" >&2; exit 1 ;;
    esac
done

# Must be root
if [[ $EUID -ne 0 ]]; then
    echo "ERROR: Must run as root" >&2
    exit 1
fi

echo "[$(date)] === CONTROL-PLANE INSTALL ==="
echo "[$(date)] Dry-run: $DRY_RUN"
echo "[$(date)] Timestamp: $TIMESTAMP"

# --- Phase 1: Backup ---
echo "[$(date)] === PHASE 1: BACKUP ==="
mkdir -p "$BACKUP_DIR"

# Backup existing units
for unit in hermes-gateway.service hermes-approved-skills.service; do
    src="/etc/systemd/system/$unit"
    if [[ -f "$src" ]]; then
        cp "$src" "$BACKUP_DIR/"
        echo "[$(date)] Backed up: $unit"
    fi
done

# Backup existing scripts
if [[ -d /usr/local/libexec/hermes ]]; then
    cp -a /usr/local/libexec/hermes "$BACKUP_DIR/libexec-hermes/"
    echo "[$(date)] Backed up: /usr/local/libexec/hermes/"
fi

# Backup existing receipt store config
if [[ -d /var/lib/hermes ]]; then
    # Just record structure, don't copy receipt contents
    find /var/lib/hermes -maxdepth 2 -ls > "$BACKUP_DIR/var-lib-hermes-structure.txt" 2>/dev/null || true
    echo "[$(date)] Recorded: /var/lib/hermes structure"
fi

# Generate manifest
(cd "$BACKUP_DIR" && find . -type f -exec sha256sum {} \;) > "$BACKUP_DIR/MANIFEST.sha256"
echo "[$(date)] Backup manifest: $BACKUP_DIR/MANIFEST.sha256"

if $DRY_RUN; then
    echo ""
    echo "[$(date)] === DRY-RUN: WHAT WOULD BE DONE ==="
    echo ""
    echo "Files to create:"
    echo "  /var/lib/hermes/receipts/{review,verification,control-plane,rejected,indexes}/"
    echo "  /var/run/hermes-receipt-writer/"
    echo "  /etc/systemd/system/hermes-receipt-writer.service"
    echo "  /etc/systemd/system/hermes-receipt-writer.socket (optional)"
    echo "  /usr/local/libexec/hermes/receipt-writer-credential"
    echo "  /usr/local/bin/hermes_receipt_writer.py"
    echo "  /usr/local/bin/submit_governance_receipt.py"
    echo ""
    echo "Services to create/restart:"
    echo "  hermes-receipt-writer.service (new)"
    echo "  hermes-gateway.service (restart for credential)"
    echo ""
    echo "Directory ownership/modes:"
    echo "  /var/lib/hermes/receipts/ → root:root 0750"
    echo "  /var/lib/hermes/receipts/*/ → root:root 0750"
    echo "  /var/run/hermes-receipt-writer/ → root:root 0750"
    echo "  receipt-writer-credential → root:root 0600"
    echo ""
    echo "Backup: $BACKUP_DIR"
    echo ""
    echo "[$(date)] === DRY-RUN COMPLETE ==="
    exit 0
fi

# --- Phase 2: Create receipt store ---
echo "[$(date)] === PHASE 2: RECEIPT STORE ==="
mkdir -p /var/lib/hermes/receipts/{review,verification,control-plane,rejected,indexes}
chown -R root:root /var/lib/hermes/receipts
chmod 0750 /var/lib/hermes/receipts
chmod 0750 /var/lib/hermes/receipts/{review,verification,control-plane,rejected,indexes}
echo "[$(date)] Receipt store created"

# --- Phase 3: Create credential ---
echo "[$(date)] === PHASE 3: SERVICE CREDENTIAL ==="
CRED_DIR="/usr/local/libexec/hermes"
CRED_FILE="$CRED_DIR/receipt-writer-credential"
if [[ ! -f "$CRED_FILE" ]]; then
    # Generate random token
    openssl rand -hex 32 > "$CRED_FILE"
    chmod 0600 "$CRED_FILE"
    chown root:root "$CRED_FILE"
    echo "[$(date)] Credential created: $CRED_FILE"
else
    echo "[$(date)] Credential already exists: $CRED_FILE"
fi

# --- Phase 4: Install receipt writer ---
echo "[$(date)] === PHASE 4: RECEIPT WRITER ==="
cp "$SCRIPT_DIR/hermes_receipt_writer.py" /usr/local/bin/hermes_receipt_writer.py
chmod 0755 /usr/local/bin/hermes_receipt_writer.py
chown root:root /usr/local/bin/hermes_receipt_writer.py
echo "[$(date)] Receipt writer installed"

cp "$SCRIPT_DIR/submit_governance_receipt.py" /usr/local/bin/submit_governance_receipt.py
chmod 0755 /usr/local/bin/submit_governance_receipt.py
chown root:root /usr/local/bin/submit_governance_receipt.py
echo "[$(date)] Submission client installed"

# Create runtime directory
mkdir -p /var/run/hermes-receipt-writer
chown root:root /var/run/hermes-receipt-writer
chmod 0750 /var/run/hermes-receipt-writer
echo "[$(date)] Socket directory created"

# --- Phase 5: Install systemd unit ---
echo "[$(date)] === PHASE 5: SYSTEMD UNIT ==="
cat > /etc/systemd/system/hermes-receipt-writer.service << 'UNIT'
[Unit]
Description=Hermes Governance Receipt Writer
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/hermes_receipt_writer.py \
    --socket /var/run/hermes-receipt-writer/receipt-writer.sock \
    --receipt-store /var/lib/hermes/receipts
ExecReload=/bin/kill -HUP $MAINPID

# Security hardening
NoNewPrivileges=yes
ProtectSystem=strict
ProtectHome=read-only
PrivateTmp=yes
PrivateDevices=yes
ProtectKernelTunables=yes
ProtectKernelModules=yes
ProtectKernelLogs=yes
ProtectControlGroups=yes
RestrictNamespaces=yes
RestrictSUIDSGID=yes
LockPersonality=yes
MemoryDenyWriteExecute=no
CapabilityBoundingSet=
AmbientCapabilities=
SystemCallArchitectures=native
RestrictAddressFamilies=AF_UNIX AF_INET AF_INET6
UMask=0077

# Writable paths
ReadWritePaths=/var/lib/hermes/receipts /var/run/hermes-receipt-writer
ReadOnlyPaths=/usr/local/libexec/hermes /home/user/.hermes

# Credential
LoadCredential=receipt-writer-token:/usr/local/libexec/hermes/receipt-writer-credential

Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
UNIT

chmod 0644 /etc/systemd/system/hermes-receipt-writer.service
chown root:root /etc/systemd/system/hermes-receipt-writer.service
echo "[$(date)] Systemd unit installed"

# --- Phase 6: Reload and activate ---
echo "[$(date)] === PHASE 6: ACTIVATE ==="
systemctl daemon-reload
systemctl enable hermes-receipt-writer.service
systemctl start hermes-receipt-writer.service
sleep 2

if systemctl is-active --quiet hermes-receipt-writer.service; then
    echo "[$(date)] Receipt writer: ACTIVE"
else
    echo "[$(date)] ERROR: Receipt writer failed to start" >&2
    systemctl status hermes-receipt-writer.service --no-pager || true
    exit 1
fi

# --- Phase 7: Post-install verification ---
echo "[$(date)] === PHASE 7: VERIFICATION ==="

# Check socket exists
if [[ -S /var/run/hermes-receipt-writer/receipt-writer.sock ]]; then
    echo "[$(date)] Socket: EXISTS"
else
    echo "[$(date)] ERROR: Socket not created" >&2
    exit 1
fi

# Check receipt store permissions
RECV_PERMS=$(stat -c '%a %U:%G' /var/lib/hermes/receipts)
echo "[$(date)] Receipt store: $RECV_PERMS"

# Check credential
CRED_PERMS=$(stat -c '%a %U:%G' /usr/local/libexec/hermes/receipt-writer-credential)
echo "[$(date)] Credential: $CRED_PERMS"

# Check systemd unit
UNIT_PATH=$(systemctl show hermes-receipt-writer.service -p FragmentPath --value)
echo "[$(date)] Unit: $UNIT_PATH"

# Test write (as root, via socket)
echo "[$(date)] Testing receipt submission..."
TEST_RESULT=$(/usr/local/bin/submit_governance_receipt.py --json '{
    "task": "INSTALL-TEST",
    "receipt_type": "CONTROL_PLANE_REVIEW",
    "subject_commit": "0000000000000000000000000000000000000000",
    "subject_tree": "0000000000000000000000000000000000000000",
    "decision": "PASS",
    "run_id": "install-test-'"$TIMESTAMP"'",
    "worktree": "/tmp/install-test",
    "completed_at": '"$(date +%s)"'
}' 2>&1) || true
echo "[$(date)] Test result: $TEST_RESULT"

# Clean up test receipt
rm -f /var/lib/hermes/receipts/control-plane/install-test--control-plane-review--00000000000000.json 2>/dev/null || true

# Verify UID 1000 cannot write
echo "[$(date)] Testing UID 1000 write rejection..."
REJECT_TEST=$(sudo -u user touch /var/lib/hermes/receipts/test-write 2>&1) && {
    echo "[$(date)] FAIL: UID 1000 can write to receipt store!" >&2
    rm -f /var/lib/hermes/receipts/test-write
    exit 1
} || {
    echo "[$(date)] PASS: UID 1000 write rejected"
}

# Rebuild index
/usr/local/bin/hermes_receipt_writer.py --rebuild-index --receipt-store /var/lib/hermes/receipts
echo "[$(date)] Index rebuilt"

echo ""
echo "============================================"
echo "[$(date)] INSTALL COMPLETE"
echo "============================================"
echo "Backup: $BACKUP_DIR"
echo "Receipt store: /var/lib/hermes/receipts (root:root 0750)"
echo "Receipt writer: hermes-receipt-writer.service (active)"
echo "Socket: /var/run/hermes-receipt-writer/receipt-writer.sock"
echo "Credential: /usr/local/libexec/hermes/receipt-writer-credential"
echo "============================================"
