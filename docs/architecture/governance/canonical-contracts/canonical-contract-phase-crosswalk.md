---
metadata_schema_version: 1
document_id: "architecture-governance-canonical-contracts-canonical-contract-phase-crosswalk"
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

# Canonical Contract Phase Crosswalk

## .4 Normalization and Archival

| Document | Action | Contract Reference |
|----------|--------|-------------------|
| docs/architecture/target-state/ | Mark HISTORICAL | render-output-contract |
| docs/render/adr/ADR-001-007 | Consolidate or separate from architecture/adr/ | platform-kernel-contract |
| ADR-016b | Renumber to unique ID | execution-contract |
| ADR-026 PROPOSED | Disambiguate from ACCEPTED version | render-output-contract |
| docs/ddl-postgresql.sql | Mark L5 supporting | schema-intent-contract |
| handoff/current-phase.md | Mark L5 supporting | platform-kernel-contract |
| ER diagram with updated_at | Update or mark target-state | schema-intent-contract |

## .5 Metadata and Lifecycle

| Object | Missing Metadata |
|--------|-----------------|
| All ADRs | owner, status, last-reviewed |
| Architecture documents | owner, version, supersedes |
| API contracts | owner, version, review cadence |
| Schema docs | owner, version, retention |

## .6 Automated Guards

| Contract | Guard Type | Implementation |
|----------|-----------|----------------|
| Platform Kernel | Module boundary check | scripts/check-architecture-drift.sh |
| RenderJob | Retry semantics guard | New guard needed |
| Schema Intent | V1-V4 immutability guard | New guard needed |
| API | Contract alignment smoke test | New guard needed |

## .6A Control-Plane Guards

| Gap | Guard Type |
|-----|-----------|
| Root receipt lifecycle | Receipt creation and verification |
| umount failure masking | Error reporting |
| Same-UID alternate gateway | Documentation + mount protection |
| Host reboot verification | Reboot test |
| Delegate tool restriction | Documentation |
| Post-task mutation | Read-only mount |

## .7 Closeout

| Item | Acceptance Evidence |
|------|-------------------|
| V5 unblock | User approval + governance closeout |
| Frontend gate | Backend contracts stable |
| All guards passing | Automated verification |
