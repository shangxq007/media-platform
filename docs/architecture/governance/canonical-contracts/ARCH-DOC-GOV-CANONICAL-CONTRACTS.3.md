---
metadata_schema_version: 1
document_id: "architecture-governance-canonical-contracts-arch-doc-gov-canonical-contracts.3"
title: ""
artifact_type: "UNKNOWN"
domain: ""
authority_class: "CANONICAL_ACCEPTED"
lifecycle_state: "ACTIVE"
acceptance_state: "NOT_APPLICABLE"
owner: "architecture-governance"
document_version: null
created_at: null
last_reviewed_at: "2026-07-17"
review_cadence_days: null
supersedes: []
superseded_by: []
canonical_contracts: []
source_of_truth_domains: []
retention_class: "PERMANENT"
generated: false
generated_by: null
do_not_edit: false
requires_explicit_approval: false
blocks_v5: false
---

# Canonical Contract Program Overview — ARCH-DOC-GOV .3

## Purpose

Define canonical architecture contracts that establish authoritative semantics for all platform domains, eliminating ambiguity across ADRs, documentation, code, and runtime.

## Authority Model

| Layer | Description | Override Rule |
|-------|-------------|---------------|
| L0 | User Approval | Highest — explicit user message with full hashes |
| L1 | Governance Decision | ADR, frozen rules, this registry |
| L2 | Canonical Contract | This document and its referenced contracts |
| L3 | Executable Contract | Code interfaces, DTOs, migrations, guards |
| L4 | Runtime State | Live Skills, systemd, mounts, gateway |
| L5 | Supporting Documentation | Roadmaps, runbooks, README |
| L6 | Evidence | .agent-tasks, receipts, forensics |
| L7 | Historical Record | Superseded docs, quarantined V5 |

## Normative Keywords

- **MUST / MUST NOT**: Unviolable normative requirements
- **SHOULD / SHOULD NOT**: Must be followed unless documented approved exception exists
- **MAY**: Optional behavior, not a mandatory implementation requirement

## Contract Status Model

### Authority Status
- `FROZEN_ACCEPTED` — immutable, user or governance accepted
- `GOVERNANCE_CANONICAL` — governance-accepted canonical contract
- `CANONICAL_CANDIDATE_REQUIRING_APPROVAL` — new semantics requiring user approval
- `DEFERRED_EXTENSION` — postponed, extension-layer only
- `QUARANTINED` — blocked, not accepted

### Implementation Alignment
- `ALIGNED` — implementation matches contract
- `PARTIALLY_ALIGNED` — partial alignment
- `KNOWN_IMPLEMENTATION_DRIFT` — documented drift
- `NOT_IMPLEMENTED` — no implementation yet
- `NOT_APPLICABLE` — contract does not require implementation
- `UNVERIFIED` — alignment not yet checked

## Change Approval Model

| Approval | Description |
|----------|-------------|
| USER_EXPLICIT_APPROVAL | User message with full hashes |
| ADR_ACCEPTANCE | Accepted ADR |
| GOVERNANCE_REVIEW | Governance review cycle |
| CODE_REVIEW_AND_TESTS | Standard code review + CI |
| SCHEMA_MIGRATION_REVIEW | Database migration review |
| CONTROL_PLANE_ROOT_CHANGE | Root-privileged system change |
| OPERATIONS_APPROVAL | DevOps approval |
| NO_CHANGE_ALLOWED_FROZEN | Immutable |

## Contract Registry Summary

| Contract | Status | Alignment | V5 Blocker |
|----------|--------|-----------|------------|
| Platform Kernel | GOVERNANCE_CANONICAL | ALIGNED | No |
| Product | GOVERNANCE_CANONICAL | ALIGNED | No |
| Timeline | GOVERNANCE_CANONICAL | ALIGNED | No |
| RenderJob | GOVERNANCE_CANONICAL | KNOWN_DRIFT | No |
| Execution | GOVERNANCE_CANONICAL | ALIGNED | No |
| Provider | GOVERNANCE_CANONICAL | PARTIALLY_ALIGNED | No |
| Storage | GOVERNANCE_CANONICAL | ALIGNED | No |
| Schema Intent | GOVERNANCE_CANONICAL | KNOWN_DRIFT | Yes |
| Render Output | CANDIDATE_REQUIRING_APPROVAL | NOT_IMPLEMENTED | Yes |
| API | GOVERNANCE_CANONICAL | PARTIALLY_ALIGNED | No |
| Control-Plane Governance | GOVERNANCE_CANONICAL | PARTIALLY_ALIGNED | No |

## Known Implementation Drift

| Contract | Drift | Resolution |
|----------|-------|-----------|
| RenderJob | retry() may reset same row | .4 normalization |
| Schema Intent | render_job.updated_at missing from V1-V4 | .7 V5 migration |
| API | Runtime false-positive behavior | .4 normalization |
| Provider | Selection behavior not fully aligned | .4 normalization |

## V5 Gate

V5 remains blocked until .1-.7 closeout. 3 gaps block V5 (GAP-001, GAP-002, GAP-004).

## Frontend Gate

Frontend implementation paused until backend contracts stabilize. This is an execution gate, not a permanent architecture rule.

## Phase Crosswalk

- **.4 Normalization**: Fix stale docs, add canonical references, mark historical
- **.5 Lifecycle**: Add owner/status/version metadata
- **.6 Guards**: Repository-level architecture guards
- **.6A Guards**: Control-plane guards (OS identity, mounts, receipts)
- **.7 Closeout**: Final acceptance, V5 unblock conditions
