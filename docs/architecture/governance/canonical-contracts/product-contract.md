---
metadata_schema_version: 1
document_id: "architecture-governance-canonical-contracts-product-contract"
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

# Product Contract

**Contract ID:** product
**Authority Status:** GOVERNANCE_CANONICAL
**Implementation Alignment:** ALIGNED
**Frozen Rule:** F-001 (Product is the canonical business object)

## PR-001: Product Identity
Product **MUST** be the canonical business object in the platform.

## PR-002: Product Lifecycle
Product lifecycle states **MUST** be explicitly defined and enforced.

## PR-003: Metadata Ownership
Product **MUST** own all business metadata. StorageReference **MUST** be a relation, not embedded metadata.

## PR-004: StorageReference Relation
Product **MUST** reference storage via StorageReference, not inline paths.

## PR-005: Dependency Semantics
Product dependencies **MUST** be explicitly modeled and validated.

## PR-006: Tenant/Project Scope
Product **MUST** be scoped to tenant and project.

## Non-Goals
- Artifact DAG is NOT a prerequisite for Product

## Change Authority
- ADR_ACCEPTANCE
- CODE_REVIEW_AND_TESTS
