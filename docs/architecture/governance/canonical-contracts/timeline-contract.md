---
metadata_schema_version: 1
document_id: "architecture-governance-canonical-contracts-timeline-contract"
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

# Timeline Contract

**Contract ID:** timeline
**Authority Status:** GOVERNANCE_CANONICAL
**Implementation Alignment:** ALIGNED
**Frozen Rules:** F-002, F-011

## TL-001: Timeline Identity
Timeline **MUST** be the canonical media composition object.

## TL-002: TimelineRevision Immutability
TimelineRevision **MUST** be an immutable canonical snapshot. Once created, a revision **MUST NOT** be modified.

## TL-003: Revision Creation
New revisions **MUST** be created as new objects, never by mutating existing ones.

## TL-004: Render Input Relation
TimelineRevision **MUST** serve as the canonical render input.

## TL-005: Timeline Git Priority
Timeline Git **MUST** have priority over Artifact DAG caching (F-011).

## Future Capabilities (Deferred)
- Timeline Git diff/merge/conflict
- These are future capabilities, NOT current implementations

## Change Authority
- ADR_ACCEPTANCE
