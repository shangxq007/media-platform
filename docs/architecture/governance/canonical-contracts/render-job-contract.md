---
metadata_schema_version: 1
document_id: "architecture-governance-canonical-contracts-render-job-contract"
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

# RenderJob Contract

**Contract ID:** render-job
**Authority Status:** GOVERNANCE_CANONICAL
**Implementation Alignment:** KNOWN_IMPLEMENTATION_DRIFT
**Frozen Rules:** F-003, F-004, F-005, F-006

## RJ-001: RenderJob Identity
RenderJob **MUST** represent one immutable execution attempt. One row = one attempt.

## RJ-002: Immutability Boundary
Once created, a RenderJob row **MUST NOT** be mutated to represent a different attempt.

## RJ-003: Retry Creates New RenderJob
Retry **MUST** create a new RenderJob. Resetting the same row to PENDING is a **MUST NOT**.

## RJ-004: Attempt Ancestry
New RenderJobs from retry **SHOULD** reference the parent attempt for correlation.

## RJ-005: Canonical States
RenderJob states **MUST** be explicitly defined. Terminal states **MUST** be durable.

## RJ-006: FALLBACKING Excluded
FALLBACKING **MUST NOT** be part of the canonical RenderJob state model.

## RJ-007: RETRYING Excluded
RETRYING **MUST NOT** be part of the canonical RenderJob state model.

## RJ-008: Provider Selection
Provider selection **MUST** be deterministic and observable.

## Known Implementation Drift
- **RenderJobService.retry()** may reset the same row instead of creating a new RenderJob
- **canRetry** semantics unclear
- Classification: KNOWN_IMPLEMENTATION_DRIFT
- Resolution: .4 normalization

## Change Authority
- USER_EXPLICIT_APPROVAL
- ADR_ACCEPTANCE
- CODE_REVIEW_AND_TESTS
