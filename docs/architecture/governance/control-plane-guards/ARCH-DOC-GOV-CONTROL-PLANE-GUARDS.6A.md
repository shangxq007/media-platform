# ARCH-DOC-GOV-CONTROL-PLANE-GUARDS.6A

## Control-Plane Governance Implementation

**Task Identity:** ARCH-DOC-GOV-CONTROL-PLANE-GUARDS.6A  
**Status:** ACTIVE  
**Base Commit:** `417afb83db54f54443e20cd382e753570d64c80e`  
**Parent Series:** ARCH-DOC-GOV-CONTROL-PLANE-GUARDS  
**Supersedes:** .5-series exploratory work  

---

## 1. Host Environment

| Parameter | Value |
|-----------|-------|
| Distribution | openSUSE Leap 16.0 |
| Kernel | 6.12.0-160000.35-default |
| Init system | systemd 257 (257.13+suse.128.g9e8b5afe0f) |
| Runtime UID | 1000 (user), gid 100 (users) |
| Python | 3.13.13 |
| Workspace | `/home/user` |

All paths in this document are absolute on the host unless prefixed with `~/` (shorthand for `/home/user`).

---

## 2. Service Topology

### 2.1 hermes-gateway.service (existing)

- **Type:** system unit  
- **User:** user (UID 1000)  
- **PID at task start:** 2946513  
- **Role:** Hermes Agent gateway — receives chat/tool requests, dispatches to providers  
- **Relevant to .6A:** Gateway is the primary control-plane actor that delegates tool calls; governance guards intercept or audit those calls  

### 2.2 hermes-approved-skills.service (existing)

- **Type:** system unit  
- **Status:** active  
- **Role:** Mounts approved skill directories as read-only bind mounts into the agent workspace  
- **Backing store:** `/var/lib/hermes/approved-skills/` (root:root, immutable content-addressed)  

### 2.3 hermes-receipt-writer.service (NEW in .6A)

- **Type:** system unit  
- **User:** root (dedicated service credential via `LoadCredential`)  
- **Role:** Long-running daemon that accepts governance receipt submissions over a Unix socket, validates schema, and writes to the root receipt store  
- **Security:** Systemd sandbox directives (see §5)  
- **Credential:** `hermes-receipt-writer.cred` loaded via `LoadCredential=` in unit file  

---

## 3. Existing Infrastructure (pre-.6A)

### 3.1 Libexec scripts

Location: `/usr/local/libexec/hermes/`

| Script | Purpose |
|--------|---------|
| `mount-approved-skills.sh` | Bind-mount approved skill dirs read-only into workspace |
| `umount-approved-skills.sh` | Tear down bind mounts cleanly |
| `verify-approved-skills.sh` | Verify integrity of approved skill content against backing store |
| `verify-backing-store.sh` | Verify backing store hash chain |

### 3.2 Approved skills backing store

- Path: `/var/lib/hermes/approved-skills/`  
- Ownership: root:root  
- Content: kanban + java skills (and others as added)  
- Access: Read-only bind mounts created by `mount-approved-skills.sh`  

### 3.3 Receipts directory (pre-existing, empty)

- Path: `/var/lib/hermes/receipts/`  
- Ownership: root:root, mode 0700  
- State at .6A start: **empty** — directory existed but no receipt infrastructure  

---

## 4. New Components Implemented by .6A

### 4.1 Root Receipt Store

**Path:** `/var/lib/hermes/receipts/`

Subdirectory structure:

```
/var/lib/hermes/receipts/
├── review/          # Pending governance review receipts
├── verification/    # Skill integrity verification receipts
├── control-plane/   # Control-plane operation receipts (mount, umount, delegate)
├── rejected/        # Rejected or failed governance actions
└── indexes/         # Index files for receipt lookup and audit trails
```

Ownership: root:root, mode 0700 (top-level). Subdirectories inherit restrictive permissions. Only the receipt writer daemon (running as root with credential) writes here.

### 4.2 Receipt Writer Daemon

**Script:** `/usr/local/libexec/hermes/hermes_receipt_writer.py`  
**Language:** Python 3.13  
**Protocol:** Unix domain socket  
**Behavior:**

1. Listens on a systemd-activated Unix socket
2. Accepts JSON receipt submissions from authorized clients
3. Validates receipt schema (required fields: `type`, `timestamp`, `actor`, `action`, `evidence`)
4. Classifies receipt into appropriate subdirectory by type
5. Writes receipt as timestamped JSON file with atomic rename
6. Updates index in `indexes/`

### 4.3 Receipt Submission Client

**Script:** `/usr/local/libexec/hermes/submit_governance_receipt.py`  
**Language:** Python 3.13  
**Usage:** Called by control-plane scripts to submit receipts after governance actions  
**Authentication:** Uses credential file path from `CREDENTIALS_DIRECTORY` environment variable (set by systemd `LoadCredential`)  

### 4.4 Control-Plane Scripts

Location: `/usr/local/libexec/hermes/`

| Script | Purpose |
|--------|---------|
| `install-control-plane-guards.sh` | Install all .6A components (units, scripts, directories) |
| `verify-control-plane-guards.sh` | Verify installation integrity (file hashes, unit status, socket) |
| `rollback-control-plane-guards.sh` | Rollback .6A installation to pre-.6A state |
| `test-control-plane-guards.sh` | End-to-end test: submit receipt, verify written, verify index |

### 4.5 Systemd Unit: hermes-receipt-writer.service

```ini
[Unit]
Description=Hermes Governance Receipt Writer
After=network.target

[Service]
Type=notify
ExecStart=/usr/bin/python3 /usr/local/libexec/hermes/hermes_receipt_writer.py
User=root
LoadCredential=hermes-receipt-writer.cred

# Sandbox directives
ProtectSystem=strict
ReadWritePaths=/var/lib/hermes/receipts
PrivateTmp=yes
NoNewPrivileges=yes
ProtectHome=yes
RestrictNamespaces=yes
SystemCallArchitectures=native

[Install]
WantedBy=multi-user.target
```

### 4.6 Service Credential

- Name: `hermes-receipt-writer.cred`  
- Delivery: Via systemd `LoadCredential=` directive  
- Contents: Shared secret or token that the receipt writer validates against incoming submissions  
- Access: Only visible to the service process via `$CREDENTIALS_DIRECTORY`  

### 4.7 Systemd Sandbox Directives

The receipt writer service runs with hardened systemd directives:

| Directive | Effect |
|-----------|--------|
| `ProtectSystem=strict` | Filesystem is read-only except explicitly allowed paths |
| `ReadWritePaths=/var/lib/hermes/receipts` | Only receipt store is writable |
| `PrivateTmp=yes` | Isolated /tmp |
| `NoNewPrivileges=yes` | Cannot gain privileges via setuid, etc. |
| `ProtectHome=yes` | /home is inaccessible |
| `RestrictNamespaces=yes` | Cannot create namespaces |
| `SystemCallArchitectures=native` | Blocks foreign-arch syscalls |

---

## 5. Governance Debt Closures

### 5.1 ROOT-RECEIPT-DEBT-001 — CLOSED

**Problem:** No root-owned receipt store existed for governance audit trail.  
**Resolution:** Created `/var/lib/hermes/receipts/` with full subdirectory structure and writer daemon.  
**Evidence:** Receipt store populated by `test-control-plane-guards.sh`.

### 5.2 UMOUNT-DEBT-001 — CLOSED

**Problem:** `umount-approved-skills.sh` lacked verification that mounts were actually torn down.  
**Resolution:** Script updated to verify `/proc/mounts` after umount attempt; receipts submitted on success/failure.  

### 5.3 SAME-UID-GATEWAY-DEBT-001 — CLOSED

**Problem:** Gateway runs as UID 1000 (same as agent workspace), creating a confused-deputy risk where gateway-originated tool calls have full user-file access.  
**Resolution:** Control-plane governance intercepts tool calls; receipts record actor identity and action. Full isolation deferred to .7 (see §7).  

### 5.4 DELEGATE-TOOL-DEBT-001 — CLOSED

**Problem:** The `delegate` tool (spawning sub-agents) had no governance receipt or audit trail.  
**Resolution:** Delegate operations now submit control-plane receipts recording delegator, delegate target, and action scope.  

### 5.5 HOST-REBOOT-DEBT-001 — PENDING (.7)

**Problem:** No automated recovery of bind mounts and receipt writer after host reboot.  
**Status:** Systemd units are `WantedBy=multi-user.target` (will start on boot), but post-reboot verification and mount restoration is **deferred to .7**.  

---

## 6. Architecture Boundaries

### 6.1 What .6A Implements

- Root receipt store with structured subdirectories
- Receipt writer daemon with systemd sandbox
- Receipt submission client with credential authentication
- Control-plane install/verify/rollback/test scripts
- Governance debt closures (§5.1–§5.4)
- Systemd unit for receipt writer

### 6.2 What .6A Does NOT Implement

- **V5 gateway isolation** — V5 is QUARANTINED; no V5-specific changes
- **Frontend governance** — Frontend integration is PAUSED
- **Full UID separation** — Gateway still runs as UID 1000; full namespace/UID isolation is .7 scope
- **Host reboot recovery** — Automated post-reboot verification deferred to .7
- **Receipt encryption** — Receipts are plaintext JSON on disk; encryption at rest is future work
- **Remote receipt shipping** — No off-host forwarding; local-only for now

### 6.3 Frozen Boundaries

| Boundary | Status | Notes |
|----------|--------|-------|
| V5 gateway | QUARANTINED | Do not modify V5-specific code paths |
| Frontend | PAUSED | No frontend changes in .6 or .7 |
| Approved-skills backing store | IMMUTABLE | Content-addressed; only append new skills |
| Receipt store ownership | root:root | Never chown to user; credential-based access only |

---

## 7. Scope: .6A vs .7

| Concern | .6A | .7 |
|---------|-----|-----|
| Receipt store | Created, populated | Indexed, queryable |
| Receipt writer | Daemon + credential | Hardening, rate limiting |
| Gateway UID isolation | Receipts only | Namespace/UID separation |
| Host reboot | Units installed | Full recovery + verification |
| Bind mount integrity | Per-operation receipt | Continuous monitoring |
| Delegate governance | Receipt on delegate | Pre-delegate policy check |

---

## 8. Verification

After installation, run:

```bash
/usr/local/libexec/hermes/verify-control-plane-guards.sh
```

Expected output: all checks pass (file hashes, unit active, socket listening, test receipt roundtrip).

For full end-to-end test:

```bash
/usr/local/libexec/hermes/test-control-plane-guards.sh
```

---

## 9. Rollback

To remove all .6A components:

```bash
/usr/local/libexec/hermes/rollback-control-plane-guards.sh
```

This stops the receipt writer service, removes the unit file, removes libexec scripts added by .6A, and preserves the receipt store (manual cleanup required if desired).

---

## 10. Document Governance

This document is the **authoritative** reference for ARCH-DOC-GOV-CONTROL-PLANE-GUARDS.6A. Changes require:

1. Update to this file with version bump in frontmatter
2. Receipt submitted to `control-plane/` documenting the change
3. Verification script re-run to confirm consistency

**Document Version:** 1.0  
**Last Updated:** 2026-07-17  
**Author:** ARCH-DOC-GOV-CONTROL-PLANE-GUARDS.6A task execution  
