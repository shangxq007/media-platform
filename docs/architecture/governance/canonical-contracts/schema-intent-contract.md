---
metadata_schema_version: 1
document_id: "architecture-governance-canonical-contracts-schema-intent-contract"
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

# Schema Intent Contract

**Contract ID:** schema-intent
**Authority Status:** GOVERNANCE_CANONICAL
**Implementation Alignment:** KNOWN_IMPLEMENTATION_DRIFT
**Frozen Rule:** F-012

## SI-001: Three-Layer Schema Model
- **Historical migration authority**: Flyway V1-V4 bytes (FROZEN)
- **Canonical target schema intent**: post-governance schema contract
- **Deployed runtime schema**: actual physical state evidence

## SI-002: V1-V4 Frozen
Flyway V1-V4 migration bytes **MUST NOT** be modified (F-012).

## SI-003: Future Schema Correction
Schema corrections **MUST** use V5 or later, only after governance closeout.

## SI-004: render_job.updated_at
- Required by repository/test assumptions
- Missing from frozen V1-V4
- Future migration required
- Classification: SCHEMA_GAP

## SI-005: Stale Target-State Constraints
UNIQUE(render_job_id, output_type) may be inconsistent with accepted output semantics. Classification: STALE_TARGET_STATE.

## V5 Gate
V5 remains blocked until document governance .1-.7 closes.

## Change Authority
- SCHEMA_MIGRATION_REVIEW
- ADR_ACCEPTANCE
