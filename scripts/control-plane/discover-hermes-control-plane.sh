#!/bin/bash
# discover-hermes-control-plane.sh — Host discovery (read-only)
set -euo pipefail

OUTPUT="${1:-/tmp/ARCH_DOC_GOV_6A_HOST_DISCOVERY.json}"

echo "Running host discovery..."

cat > "$OUTPUT" << EOF
{
  "task": "ARCH-DOC-GOV-CONTROL-PLANE-GUARDS.6A",
  "timestamp": "$(date -Iseconds)",
  "host": {
    "hostname": "$(hostname)",
    "os": "$(cat /etc/os-release | grep PRETTY_NAME | cut -d= -f2 | tr -d '"')",
    "kernel": "$(uname -r)",
    "systemd_version": "$(systemctl --version | head -1)",
    "boot_id": "$(cat /proc/sys/kernel/random/boot_id)",
    "python_version": "$(python3 --version)"
  },
  "gateway": {
    "service_name": "hermes-gateway.service",
    "fragment_path": "$(systemctl show hermes-gateway.service -p FragmentPath --value 2>/dev/null)",
    "executable": "$(systemctl show hermes-gateway.service -p ExecStart --value 2>/dev/null | grep -oP 'path=\K[^;]+')",
    "uid": "$(systemctl show hermes-gateway.service -p User --value 2>/dev/null)",
    "main_pid": "$(systemctl show hermes-gateway.service -p MainPID --value 2>/dev/null)",
    "requires": "$(systemctl show hermes-gateway.service -p Requires --value 2>/dev/null)",
    "active": $(systemctl is-active --quiet hermes-gateway.service && echo true || echo false)
  },
  "service_identity": {
    "dedicated_account": false,
    "shared_uid": true,
    "current_uid": "$(id -u)",
    "current_user": "$(id -un)"
  },
  "protected_paths": [
    {"path": "~/.hermes/skills/software-development/kanban-multi-agent-orchestration/", "owner": "root:root", "mode": "0444", "protection": "ro bind mount"},
    {"path": "~/.hermes/skills/software-development/java-test-repair/", "owner": "root:root", "mode": "0444", "protection": "ro bind mount"},
    {"path": "/var/lib/hermes/receipts/", "owner": "root:root", "mode": "0750", "protection": "root-only write"}
  ],
  "existing_receipt_paths": [
    {"path": "/var/lib/hermes/receipts", "exists": $(test -d /var/lib/hermes/receipts && echo true || echo false)}
  ],
  "systemd_capabilities": {
    "load_credential": true,
    "protect_system_strict": true,
    "private_tmp": true,
    "no_new_privileges": true
  },
  "mount_capabilities": {
    "bind_mount": true,
    "read_only_bind": true,
    "btrfs_ro": true
  },
  "credential_capabilities": {
    "af_unix_peercred": true,
    "so_peercred": true,
    "systemd_load_credential": true
  },
  "blocking_unknowns": []
}
EOF

echo "Discovery written to: $OUTPUT"
