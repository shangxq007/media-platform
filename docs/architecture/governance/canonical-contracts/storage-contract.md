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
