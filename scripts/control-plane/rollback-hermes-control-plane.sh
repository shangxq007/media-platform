#!/bin/bash
# rollback-hermes-control-plane.sh — Rollback control-plane installation
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
    echo "ERROR: Must run as root" >&2
    exit 1
fi

BACKUP_DIR="${1:-}"
if [[ -z "$BACKUP_DIR" ]]; then
    echo "Usage: sudo bash rollback-hermes-control-plane.sh /var/backups/hermes-control-plane/<timestamp>"
    exit 1
fi

if [[ ! -d "$BACKUP_DIR" ]]; then
    echo "ERROR: Backup directory not found: $BACKUP_DIR" >&2
    exit 1
fi

echo "[$(date)] === CONTROL-PLANE ROLLBACK ==="
echo "[$(date)] Backup: $BACKUP_DIR"

# Phase 1: Stop receipt writer
echo "[$(date)] Stopping receipt writer..."
systemctl stop hermes-receipt-writer.service 2>/dev/null || true
systemctl disable hermes-receipt-writer.service 2>/dev/null || true

# Phase 2: Remove receipt writer unit
echo "[$(date)] Removing receipt writer unit..."
rm -f /etc/systemd/system/hermes-receipt-writer.service

# Phase 3: Remove receipt writer scripts
echo "[$(date)] Removing receipt writer scripts..."
rm -f /usr/local/bin/hermes_receipt_writer.py
rm -f /usr/local/bin/submit_governance_receipt.py

# Phase 4: Remove socket directory
echo "[$(date)] Removing socket directory..."
rm -rf /var/run/hermes-receipt-writer

# Phase 5: Restore backup (units only, preserve receipts)
echo "[$(date)] Restoring backed-up units..."
for unit in "$BACKUP_DIR"/*.service; do
    if [[ -f "$unit" ]]; then
        cp "$unit" /etc/systemd/system/
        echo "[$(date)] Restored: $(basename "$unit")"
    fi
done

# Phase 6: Reload
echo "[$(date)] Reloading systemd..."
systemctl daemon-reload

echo ""
echo "============================================"
echo "[$(date)] ROLLBACK COMPLETE"
echo "============================================"
echo "Note: Persistent receipts preserved at /var/lib/hermes/receipts/"
echo "Note: Credential preserved at /usr/local/libexec/hermes/receipt-writer-credential"
echo "============================================"
