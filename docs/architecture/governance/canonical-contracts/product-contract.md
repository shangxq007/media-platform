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
