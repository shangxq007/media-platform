# Delegated-Tool Containment Policy

## Task: ARCH-DOC-GOV-CONTROL-PLANE-GUARDS.6A

## Native Delegated-Tool Restriction

```
status: UNAVAILABLE
```

The current Hermes platform does not provide native tool-level restrictions that prevent a delegated agent session from calling specific tools (e.g., skill_manage, memory write). This is a platform limitation.

## Containment Strategy

Since native tool restriction is unavailable, containment is achieved through host-level controls:

### 1. Filesystem Write Restrictions

- Skill directories: root-owned, read-only bind mount
- Receipt store: root:root 0750, UID 1000 cannot write
- Systemd units: root:root 0644, UID 1000 cannot modify

### 2. Receipt-Store Isolation

- Only the receipt writer daemon (running under systemd with credentials) can write receipts
- Direct file writes to /var/lib/hermes/receipts/ by UID 1000 are rejected by filesystem permissions

### 3. Service Credential Protection

- Receipt writer uses systemd LoadCredential
- Credential file readable only by root and the service
- UID 1000 processes cannot read the credential

### 4. Systemd Capability Restrictions

- Receipt writer: CapabilityBoundingSet= (empty)
- No CAP_SYS_ADMIN, no mount capability from within service

### 5. Mount Syscall Restriction

- Skill mounts are managed by root-owned systemd services
- UID 1000 cannot mount/unmount

### 6. Audit Logging

- Receipt writer logs all submissions (success and failure)
- Logs go to systemd journal (root-readable)

## What This Does NOT Prevent

- A delegated agent session can still call skill_manage() — the call succeeds but the filesystem write is blocked by ro mount
- A delegated agent session can still call memory tools — memory is not root-protected
- A delegated agent session can modify files in ~/.hermes/ that are not protected

## Residual Risk

The primary residual risk is that a delegated agent session can modify unprotected files (non-skill, non-receipt, non-systemd) and can still make memory writes. This is acceptable because:

1. Skill integrity is enforced by ro mounts (the most critical asset)
2. Receipt integrity is enforced by root ownership
3. Memory is operational state, not governance evidence
4. Full containment would require native tool policy (not available)

## Honesty Requirement

This policy must NOT claim that host sandbox provides complete model-layer tool restriction. The containment is filesystem-level only. Model-layer tool calls are NOT restricted by the host.
