---
metadata_schema_version: 1
document_id: "architecture-governance-canonical-contracts-api-contract"
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

# API Contract

**Contract ID:** api
**Authority Status:** GOVERNANCE_CANONICAL
**Implementation Alignment:** PARTIALLY_ALIGNED

## AP-001: Canonical API Intent
OpenAPI specification **MUST** define canonical API intent. Runtime behavior **MUST NOT** override canonical intent.

## AP-002: DTO/Controller Executable Contract
DTOs and controllers **MUST** align with canonical API intent.

## AP-003: Validation Contract
Input validation **MUST** be explicit and consistent.

## AP-004: Tenant/Project Scoping
API endpoints **MUST** enforce tenant and project scoping.

## AP-005: False-Positive Prohibition
Runtime endpoint returning COMPLETED while persisted state is not completed **MUST** be treated as contract violation.

## Known Drift
- Runtime endpoint may return false-positive COMPLETED status

## Preview Contract
Preview endpoints **MUST** be explicitly marked as preview, not stable API.

## Change Authority
- CODE_REVIEW_AND_TESTS
- ADR_ACCEPTANCE
