---
metadata_schema_version: 1
document_id: "architecture-governance-canonical-contracts-storage-contract"
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

# Storage Contract

**Contract ID:** storage
**Authority Status:** GOVERNANCE_CANONICAL
**Implementation Alignment:** ALIGNED

## ST-001: StorageRuntime
StorageRuntime **MUST** be the canonical storage abstraction.

## ST-002: StorageReference
StorageReference **MUST** provide tenant-scoped, authorized access to stored artifacts.

## ST-003: Materialization
Materialization **MUST** be explicit and observable.

## ST-004: Checksum
Content integrity **MUST** be verified via checksum.

## ST-005: Authorization
Presign authorization **MUST** occur before any external access.

## ST-006: OpenDAL Boundary
OpenDAL **MAY** be used as an implementation option but has limits. StorageRuntime **MUST** remain the canonical abstraction.

## Change Authority
- ADR_ACCEPTANCE
- CODE_REVIEW_AND_TESTS
