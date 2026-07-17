---
metadata_schema_version: 1
document_id: "architecture-governance-canonical-contracts-render-output-contract"
title: ""
artifact_type: "UNKNOWN"
domain: ""
authority_class: "CANONICAL_CANDIDATE"
lifecycle_state: "CANDIDATE"
acceptance_state: "NOT_ACCEPTED"
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

# Render Output Contract

**Contract ID:** render-output
**Authority Status:** CANONICAL_CANDIDATE_REQUIRING_APPROVAL
**Implementation Alignment:** NOT_IMPLEMENTED
**Frozen Rules:** F-009, F-010, F-013

## RO-001: Render Output Identity
Render output **MUST** be uniquely identifiable per RenderJob attempt.

## RO-002: Output Item Semantics
Each output item **MUST** have a defined type and content boundary.

## RO-003: Idempotency Intent
Output commit **MUST** support idempotent semantics.

## RO-004: Commit Boundary
Output commit **MUST** be atomic per RenderJob.

## RO-005: Storage/Product Realization
Output **MUST** connect to StorageReference and optionally to Product.

## Authority Status
This contract is **CANONICAL_CANDIDATE_REQUIRING_APPROVAL**. Key semantics (RenderOutputCommit, RenderOutputItem) have not been formally approved.

## Quarantined Historical Implementation
- Commit 60d4ac5: QUARANTINED, NOT_ACCEPTED, NOT_AUTHORITY
- Contains V5 migration and RenderOutputCommit/Item repositories
- MUST NOT be treated as canonical

## V5 Gate
V5 remains blocked (F-013). 3 gaps block V5:
- GAP-001: RenderOutputCommit/Item not approved
- GAP-002: V5 migration quarantined
- GAP-004: render_job.updated_at missing

## Artifact DAG
- Postponed indefinitely (F-009)
- Extension-layer design only (F-010)

## Change Authority
- USER_EXPLICIT_APPROVAL
- ADR_ACCEPTANCE
- SCHEMA_MIGRATION_REVIEW
