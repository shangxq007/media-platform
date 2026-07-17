---
metadata_schema_version: 1
document_id: "architecture-governance-canonical-contracts-provider-contract"
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

# Provider Contract

**Contract ID:** provider
**Authority Status:** GOVERNANCE_CANONICAL
**Implementation Alignment:** PARTIALLY_ALIGNED

## PV-001: Provider Identity
Provider **MUST** have a unique identity and explicit type.

## PV-002: Provider Types
- STUB: for testing, MUST NOT produce false production success
- POC: for proof-of-concept
- PRODUCTION: for production use
- HOLD: disabled, MUST NOT be dispatched
- DEPRECATED: end-of-life
- OPTIONAL: available but not default

## PV-003: Provider Status
Provider status **MUST** be observable and durable.

## PV-004: Registration
Provider **MUST** be registered before selection.

## PV-005: Selection
Disabled providers **MUST NOT** be dispatched. Selection failure **MUST** be durable and observable.

## PV-006: Production Enablement
Production providers **MUST** be explicitly enabled.

## Known Drift
- Provider selection behavior not fully aligned

## Change Authority
- ADR_ACCEPTANCE
- CODE_REVIEW_AND_TESTS
