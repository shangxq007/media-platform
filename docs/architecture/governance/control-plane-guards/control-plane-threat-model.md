# Control-Plane Threat Model

This document describes the threat landscape for Hermes control-plane governance — the mechanisms that protect skill integrity, governance receipts, memory state, and automated guard infrastructure from tampering or bypass.

## 1. Assets Protected

| Asset | Location | Criticality |
|-------|----------|-------------|
| Persistent governance receipts | `~/.hermes/governance/receipts/` | **High** — tamper-evident record of every verification event |
| Skill directories (kanban, java-test-repair, etc.) | `~/.hermes/skills/` | **High** — executable procedure definitions |
| Memory / state database | `~/.hermes/memory/state.db` | **High** — persistent session memory, conversation history |
| Governance configuration | `~/.hermes/governance/config/` | **Critical** — policies, baseline hashes, guard schedules |
| Automated guard baselines | `~/.hermes/governance/baselines/` | **High** — expected-good snapshots for drift detection |
| Systemd service units | `/etc/systemd/system/hermes-*` | **Medium** — daemon lifecycle, restart policies |

## 2. Threat Actors

### 2.1 Ordinary Agent Process (UID 1000)

The normal Hermes agent runtime. Has full read/write to the user's home directory. This is the *intended* operator — but also the actor most likely to accidentally (or through prompt injection) mutate governance artifacts.

### 2.2 Alternate Gateway Process (Same UID)

A second Hermes instance, coding agent, or any process running as the same user. Shares UID-level filesystem permissions, so POSIX DAC provides zero isolation. May be invoked by multi-agent orchestration (kanban workers, Codex, Claude Code).

### 2.3 Delegated Agent Sessions

Sub-agents spawned by the primary agent (e.g., `claude-code`, `codex`, `kilo-code`). They inherit the parent's UID and can access the full `~/.hermes/` tree. Their skill consumption is governed by the parent's skill manifest, but they may resolve skills independently.

### 2.4 Curator Process

The Hermes curator — background maintenance that consolidates, prunes, and validates skills. Runs under the same UID. Trusted but must not be able to skip verification gates.

### 2.5 External Network

Remote endpoints, package registries, web content. Not directly relevant to filesystem-level threats but relevant for supply-chain vectors (malicious skill content fetched from remote sources).

## 3. Threat Enumeration

### T1: Direct Receipt Store Write by Ordinary User

**Description:** The agent (or any same-UID process) writes fabricated receipts to `~/.hermes/governance/receipts/` without actually performing verification.

**Impact:** Governance history becomes unreliable. Auditors cannot distinguish genuine from forged verification events.

**Mitigation:**
- Receipt files are write-once: naming convention includes immutable timestamp + content hash.
- Automated guard compares receipt hashes against baseline on each cycle.
- Read-only bind mount on the receipt directory (`root-skill-protection` pattern) when deployed in hardened mode.

### T2: Receipt Overwrite / Tampering

**Description:** An existing receipt file is modified after creation (content change, timestamp alteration).

**Impact:** Evidence chain integrity breaks. Historical verification events lose evidentiary value.

**Mitigation:**
- `chattr +i` (immutable flag) applied to receipt files after creation.
- Guard baseline stores SHA-256 of each receipt; drift triggers alert.
- Optional append-only log forwarding to external storage.

### T3: Symlink Traversal Escape

**Description:** A symlink placed inside a governed directory points outside the expected subtree, allowing reads/writes to arbitrary paths.

**Impact:** Skill directories or receipt stores could be redirected to attacker-controlled locations.

**Mitigation:**
- Guard scripts resolve symlinks via `realpath` before any operation.
- `O_NOFOLLOW` semantics used where available.
- Automated baseline scan flags symlinks in governed directories.

### T4: Review-before-Verification Bypass

**Description:** The agent creates a receipt claiming verification occurred without actually running the verification steps (e.g., skipping code review, test execution).

**Impact:** Skills or code enter production without passing quality gates.

**Mitigation:**
- Receipt structure requires structured evidence fields (tool output hashes, exit codes).
- Guard validates that evidence fields are non-empty and internally consistent.
- Curator periodically re-verifies a random sample of receipts.

### T5: Same-UID Gateway Impersonation

**Description:** A rogue process running as UID 1000 impersonates the Hermes gateway (same socket path, same config directory).

**Impact:** Agent sessions connect to a malicious gateway that can intercept prompts, inject responses, or exfiltrate memory.

**Mitigation:**
- Gateway socket path is PID-locked; stale sockets are cleaned on startup.
- `state.db` is opened with SQLite file-locking (WAL mode) preventing concurrent corruption.
- Process identity verified via `/proc/self` cross-check.

### T6: Skill Content Mutation

**Description:** After a skill passes governance review, its content (SKILL.md, scripts, templates) is modified.

**Impact:** Runtime behavior diverges from reviewed/approved behavior. A skill that passed review may execute entirely different logic.

**Mitigation:**
- Baseline stores SHA-256 of every file in each governed skill directory.
- Guard runs periodic diff against baseline; any mutation triggers `DRIFT` alert.
- Read-only bind mount on skill directories in hardened deployments.
- `hermes curator audit` re-hashes all skills against baseline.

### T7: Memory / Governance Config Mutation

**Description:** `state.db` or governance configuration files are modified outside of approved channels.

**Impact:** Agent memory is corrupted, governance policies are weakened or disabled, guard schedules are altered.

**Mitigation:**
- `state.db` integrity checked via `PRAGMA integrity_check` on guard cycle.
- Governance config files included in baseline; drift triggers alert.
- Backup snapshot taken before each guard cycle.

### T8: Service Credential Theft

**Description:** API keys, tokens, or service credentials stored in Hermes config are accessed by an unauthorized same-UID process.

**Impact:** Lateral movement to external services, data exfiltration, cost abuse.

**Mitigation:**
- Credentials stored with `0600` permissions (effective only against other UIDs, not same-UID).
- Environment variable injection preferred over file-based secrets where possible.
- Key rotation schedule enforced by governance policy.

### T9: Mount Failure Silence

**Description:** A read-only bind mount intended to protect a governed directory silently fails (kernel version incompatibility, namespace issue), leaving the directory writable without alerting.

**Impact:** All protections relying on the mount are silently absent.

**Mitigation:**
- Mount status verified after every bind-mount operation (`mountpoint -q`).
- Guard checks mount status as first step; missing mount is a `CRITICAL` alert, not a warning.
- Pre-flight check in `hermes computer-use doctor` includes mount verification.

### T10: Delegated Agent Skill Drift

**Description:** A delegated coding agent (Codex, Claude Code) resolves skills from a different version or path than the parent agent intended.

**Impact:** Delegated work follows outdated or unapproved procedures.

**Mitigation:**
- Parent agent passes explicit skill manifest to delegated sessions.
- Baseline checksums shared with delegated context packs.
- Post-delegation audit compares consumed skill versions against parent baseline.

## 4. Residual Risks

| Risk | Acceptance Rationale |
|------|---------------------|
| Same-UID process can read all files | POSIX DAC provides no intra-user isolation. Requires Linux user namespaces or containers for full isolation — out of scope for current deployment. |
| Kernel-level bypass of bind mounts | `CAP_SYS_ADMIN` or namespace manipulation can undo bind mounts. Accepted for single-user workstation deployments. |
| TOCTOU in guard verification | Time-of-check-to-time-of-use gap exists between hash verification and runtime use. Mitigated by short guard cycles but not eliminable. |
| Prompt injection via external content | Agent may be tricked into mutating governed files via malicious web content. Mitigated by guard cycles detecting post-hoc drift. |
| Credential exposure in process listing | Same-UID processes can read `/proc/<pid>/environ`. Accepted risk; use secret-manager integration for high-security deployments. |

## 5. Delegated-Tool Containment

**Native tool restriction is UNAVAILABLE.** The Hermes agent framework does not currently support per-tool or per-capability permission scoping for delegated agents. A delegated agent session inherits the full tool set of its parent.

**Filesystem containment is used instead.** Delegated agents operate within a scoped working directory. Governance artifacts (receipts, baselines, config) reside outside typical working directories (`~/.hermes/governance/`), providing a degree of separation. However, this is a convention, not an enforcement boundary — a determined or confused delegated agent can still traverse to governance paths.

Future work should explore:
- Linux user-namespace isolation per delegated session.
- Seccomp-BPF filters restricting `openat` to scoped paths.
- AppArmor/SELinux profiles for agent processes.
- Capability-based tool scoping in the agent framework.
