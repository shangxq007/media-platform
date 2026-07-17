---
metadata_schema_version: 1
document_id: "architecture-governance-source-of-truth-arch-doc-gov-source-of-truth-matrix.2"
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

# ARCH-DOC-GOV Source of Truth Matrix — Phase .2

## Purpose

Define authoritative source hierarchy for all architecture domains, eliminating "which file wins" ambiguity.

## Authority Layer Model

8-layer hierarchy (L0-L7):
- L0: User Approval (highest)
- L1: Governance Decisions
- L2: Canonical Contracts
- L3: Executable Contracts
- L4: Runtime State
- L5: Supporting Documentation
- L6: Evidence
- L7: Historical Record

## Conflict Priority

Explicit user approval > governance decision > canonical contract > executable contract > runtime observation > documentation > evidence > historical

Exceptions:
1. Implementation cannot auto-override frozen contract (DRIFT)
2. Frozen migration bytes separate from canonical schema intent
3. API runtime behavior cannot override canonical intent
4. Evidence has NO design authority

## Resolved Decisions

1. Kanban done ≠ accepted — only L0 user approval determines acceptance
2. Memory is NOT an approval source — audit/context only
3. .agent-tasks is never canonical — evidence only
4. Executable contract wins over documentation at same layer
5. Frozen rules override all proposed changes until governance closeout
6. Live Skill state backed by L0 approval — not independently authoritative

## Unresolved Domains

| Domain | Status | Resolution Phase |
|--------|--------|-----------------|
| Render Output Model | QUARANTINED | .7 |
| Artifact DAG | QUARANTINED | .7 |
| Database Schema | PARTIALLY_RESOLVED | .3 |
| API Contracts | PARTIALLY_RESOLVED | .3 |
| Frontend Architecture | BLOCKED | .3 |
| Agent/Gateway Control Plane | PARTIALLY_RESOLVED | .6A |

## Phase .3 Inputs

Canonical contracts needed for:
- Render job retry semantics
- Database target schema
- API contract verification
- Frontend technology decision
- Execution lifecycle

## Phase .6/.6A Guard Inputs

6 governance debts mapped to .6A:
- Root receipt creation
- umount error reporting
- Same-UID risk documentation
- Host reboot verification
- Tool restriction documentation
- Post-task hook mitigation

## V5 Gate

V5 remains QUARANTINED until governance .1-.7 closes. 3 conflicts block V5 (C-002, C-009, C-021).

## Non-Authoritative Sources

10 categories explicitly excluded from authority:
.agent-tasks, Memory, holographic memory, Kanban acceptance, receipts, forensics, runtime logs, quarantined V5, superseded ADRs, stale target-state
