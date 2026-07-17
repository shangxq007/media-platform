# Protected Path and Mount Policy

## Task: ARCH-DOC-GOV-CONTROL-PLANE-GUARDS.6A

## Protected Paths

### Skill Directories

| Path | Owner | Mode | Protection |
|------|-------|------|------------|
| ~/.hermes/skills/software-development/kanban-multi-agent-orchestration/ | root:root | 0444 (files), 0555 (dirs) | ro bind mount |
| ~/.hermes/skills/software-development/java-test-repair/ | root:root | 0444 (files), 0555 (dirs) | ro bind mount |

Backing store: /var/lib/hermes/approved-skills/<name>/<tree-hash>/ (root:root 0444/0555)

### Memory

| Path | Owner | Mode | Protection |
|------|-------|------|------------|
| ~/.hermes/state.db | user:users | 0644 | Not root-protected (gateway needs rw) |

Note: Memory is NOT root-protected because the gateway process (UID 1000) requires read-write access for session management.

### Governance Configuration

| Path | Owner | Mode | Protection |
|------|-------|------|------------|
| /etc/systemd/system/hermes-gateway.service | root:root | 0644 | root-owned |
| /etc/systemd/system/hermes-approved-skills.service | root:root | 0644 | root-owned |
| /etc/systemd/system/hermes-receipt-writer.service | root:root | 0644 | root-owned |
| /usr/local/libexec/hermes/ | root:root | 0755 | root-owned |

### Receipt Store

| Path | Owner | Mode | Protection |
|------|-------|------|------------|
| /var/lib/hermes/receipts/ | root:root | 0750 | root-only write |
| /var/lib/hermes/receipts/review/ | root:root | 0750 | root-only write |
| /var/lib/hermes/receipts/verification/ | root:root | 0750 | root-only write |
| /var/lib/hermes/receipts/control-plane/ | root:root | 0750 | root-only write |
| /var/lib/hermes/receipts/rejected/ | root:root | 0750 | root-only write |
| /var/lib/hermes/receipts/indexes/ | root:root | 0750 | root-only write |

## Mount Policy

### Current Mounts

Both skill directories use btrfs ro mounts from /dev/mapper/system-root.

Options: ro, relatime, ssd, discard=async, space_cache=v2

### Mount Requirements

- Source must be root-owned backing store
- Target must be existing skill directory
- Options must include: bind, ro
- nosuid, nodev recommended where applicable
- noexec NOT applied (skill dirs may contain scripts)

### Mount Failure Behavior

ExecStartPre verify script checks all required mounts before gateway start.
Missing mount → gateway activation FAILS (fail-closed).

### Unmount Failure Behavior

umount failure must produce non-zero exit code.
Must not delete source/target while still mounted.
Must not report rollback complete on umount failure.

## Allowlist

### Read-Write (UID 1000)

- ~/.hermes/ (except protected skill paths)
- ~/.hermes/sessions/
- ~/.hermes/logs/
- /tmp/ (standard)

### Read-Only (UID 1000)

- ~/.hermes/skills/software-development/kanban-multi-agent-orchestration/
- ~/.hermes/skills/software-development/java-test-repair/

### No Access (UID 1000)

- /var/lib/hermes/receipts/ (root:root 0750)
- /etc/systemd/system/hermes-*.service (root:root 0644)
