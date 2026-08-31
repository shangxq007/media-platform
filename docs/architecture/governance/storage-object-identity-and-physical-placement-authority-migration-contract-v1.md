# Storage object identity and physical placement authority migration contract v1

Status: **FROZEN DECISION RECOVERY**

Owner: `HERMES_CONTROL_PLANE`

Authority owner: `STORAGE`

Evidence baseline: canonical base `bd919f958bea79e57fe7fcb1cf1396eca96a0e9d` / tree `9edb4c10d15e567bff7300190f156f21cf2d7cf0`; frozen H6 `c91b90f4127163d0b6d6f4f84e4190a3a083c652` / tree `14d616eb10ef7b212610549ff8d8ca3adb11d215`.

Contract token: `STORAGE_OBJECT_IDENTITY_AND_PHYSICAL_PLACEMENT_AUTHORITY_MIGRATION_CONTRACT_V1`

## 1. Decision and scope

This contract freezes `STORAGE_OBJECT_ID_IS_LOGICAL_STABLE_IDENTITY_V1`. A `StorageObjectId` is a stable logical identity issued and owned by Storage. It is independent of provider, bucket, object key, filesystem path, URI, region, locator, and backend. A physical value never becomes a logical ID by being wrapped in the type or copied into a column named `storage_object_id`.

It also freezes these factual-authority laws:

- `CURRENT_RAW_STRING_STORAGE_PROVIDER_IS_NOT_PROOF_OF_CANONICAL_LOGICAL_ID_ISSUANCE_V1`
- `PHYSICAL_PROVIDER_SPI_IS_NOT_LOGICAL_IDENTITY_AUTHORITY_V1`
- `STORAGE_APPLICATION_BOUNDARY_OWNS_LOGICAL_ID_ISSUANCE_V1`
- `PROVIDER_BACKEND_MAY_OWN_PLACEMENT_MECHANICS_NOT_LOGICAL_ID_SEMANTICS_V1`

Storage owns both logical object identity and physical placement authority. Other modules may retain, transport, compare, and reference a Storage-issued ID. They must not manufacture one from physical values, parse one as a physical location, or maintain a competing placement registry. Artifact remains authority for Artifact identity, integrity, lifecycle, and its reference to a Storage object; that reference does not make Artifact a Storage identity or placement owner.

This is a decision and migration contract. It authorizes no production, test, build, configuration, schema, migration, or runtime change. The implementation phases below require separately authorized tasks.

## 2. Vocabulary

- **Logical object identity**: `StorageObjectId`, opaque outside Storage and stable when bytes move or replicas change.
- **Physical placement**: the typed `StorageObjectLocation(providerId, namespace, opaqueLocator, providerVersionToken, region)` tuple. It is Storage-owned and is not identity.
- **Storage replica**: the typed semantic association `StorageReplicaRecord(replicaId, objectId, location, state, digest, length)`; the actual record names the last two fields `committedDigest` and `committedLength`.
- **Legacy physical-encoded ID**: a provider, bucket, object key, path, URI, locator, or backend coordinate stored where a logical `StorageObjectId` is expected.
- **Canonical logical ID**: an ID proven to have been issued or adopted by Storage, with corroborating Storage-owned evidence.
- **Ambiguous row**: a persisted value whose semantic class cannot be proved deterministically from collision-free evidence.
- **Adoption**: a migration-only Storage operation that imports, registers, or copies a proven physical placement and returns a Storage-issued logical ID and durable placement receipt.
- **Placement receipt**: immutable Storage evidence that a canonical object/replica/location exists and satisfies required integrity facts.
- **Migration journal**: Storage-owned, restartable saga state keyed deterministically to the source row and original value.
- **Compatibility**: temporary, explicitly owned migration behavior with entry and removal gates. It is never a second authority.

## 3. Mechanical current-state facts

Every claim below is normatively classified in [current-vs-target-fact-classification.tsv](storage-object-identity-placement-migration-v1/current-vs-target-fact-classification.tsv). The only allowed classifications are `CURRENT_CANONICAL_FACT`, `FROZEN_H6_FACT`, `TARGET_MODEL`, and `NOT_PRESENT`.

- **`CURRENT_CANONICAL_FACT`:** the canonical `StorageObjectId` Javadoc says logical identity is independent of physical location, provider, or storage backend. The typed `com.example.platform.storage.contract.identity.StorageObjectLocation` and `com.example.platform.storage.contract.validation.StorageValidationModel.StorageReplicaRecord` exist and separate typed placement and replica mechanics from object identity.
- **`CURRENT_CANONICAL_FACT`:** the raw current SPI is `com.example.platform.storage.contract.StorageProvider`. It exposes `String providerId()`, `String providerType()`, and `store`, `fetch`, `delete`, `exists`, and `metadata` over `String storageReferenceId`. Render's `com.example.platform.render.infrastructure.storage.S3StorageProvider` architecture-validation stub implements it. This is a physical/raw-string provider SPI and is not proof of canonical logical ID issuance.
- **`CURRENT_CANONICAL_FACT`:** the distinct typed current foundation/backend SPI is `com.example.platform.storage.contract.provider.StorageProvider`. It exposes typed `beginWrite`, `write`, `completeWrite`, `abortWrite`, `openRead`, `stat`, `copy`, and `delete`. `completeWrite` returns `com.example.platform.storage.contract.write.WriteSessionResult`, which contains a `StorageObjectId`; worker-fabric uses this SPI. These are current typed mechanics, not a materialized Storage application issuance boundary.
- **`CURRENT_CANONICAL_FACT`:** `InMemoryStorageProvider` and `AbstractOpenDalProvider` implement the typed SPI. Both generate `obj-<writeSessionId>` on completion, and OpenDAL keeps its physical mapping behind the backend. A backend-generated typed completion ID proves the current mechanic and target compatibility; it does not prove that canonical Storage application logical-identity issuance authority already exists.
- **`FROZEN_H6_FACT`:** the raw SPI, typed SPI, raw S3 stub, typed InMemory/OpenDAL providers, and worker-fabric orchestrator have identical blobs in canonical base `bd919f958bea79e57fe7fcb1cf1396eca96a0e9d` and frozen H6 `c91b90f4127163d0b6d6f4f84e4190a3a083c652`.
- **`CURRENT_CANONICAL_FACT`:** the V1 `storage_object` table has `id`, `provider_code`, `bucket`, and `object_key` in one row. A mechanical production scan found no repository that owns or uses this table as authority. It is migration input only until each row is classified; its name and string shape prove nothing.
- **`CURRENT_CANONICAL_FACT`:** the V1 `artifact_replica` table contains `artifact_id`, `replica_id`, `provider_id`, `storage_object_id`, `region`, `role`, `state`, and `created_at`. It has an Artifact FK, but no FK to `storage_object` and no semantic discriminator for its overloaded value. It cannot prove logical-versus-physical meaning.
- **`CURRENT_CANONICAL_FACT`:** no PostgreSQL instance was proven bound to canonical main. No running test container or unrelated database may be treated as production evidence. Consequently all persisted row counts remain `UNKNOWN` until M0 binds a database identity and an authorized observation reads it.
- **`TARGET_MODEL`:** a Storage-owned logical identity issuance/application boundary, a migration-only Storage adoption operation, and normalized Storage-owned identity/placement/replica/receipt/journal persistence are required.
- **`NOT_PRESENT`:** none of those three target authority materializations exists in either current snapshot. The current raw SPI, typed SPI, typed value/foundation records, provider implementations, backend-generated `obj-<writeSessionId>`, and worker-fabric use must not be promoted into evidence of that absent authority.

## 4. Writer and reader reality

The complete writer union is [writer-inventory.tsv](storage-object-identity-placement-migration-v1/writer-inventory.tsv). It proves three physical-to-logical constructors (`ProjectImportService`, `RenderArtifactStorageService`, `ClientExportService`) and two ambiguous writers (`ArtifactOutputCommitOrchestrator`, `ArtifactCatalogService`). `ArtifactOutputCommitOrchestrator` preserves the typed provider completion object ID, but that backend completion is not provenance for the absent Storage application issuance authority. H6's planned retirement of the catalog path does not erase the canonical-base fact.

The complete reader union is [reader-inventory.tsv](storage-object-identity-placement-migration-v1/reader-inventory.tsv). It proves four physical interpreters, two canonical consumers, two ambiguous persisted-value mappers, and three Storage-owner resolvers. An entry is classified by semantics, not merely by whether its class name says reader or whether it accepts a typed value.

The inventories are union evidence across the canonical base and frozen H6 object, not a claim that both revisions run simultaneously. Their paths are unique within each inventory, IDs are unique across all ledgers, and every row is classified. The validator fixes their arithmetic against the final tokens.

## 5. Authority and target semantic model

The authoritative mapping is [owner-matrix.tsv](storage-object-identity-placement-migration-v1/owner-matrix.tsv). Exactly one owner exists for each semantic:

1. The target Storage-owned logical identity issuance/application boundary owns issuance, adoption, resolution, and persistence of logical `StorageObjectId` values; the migration-only adoption operation is its only legacy-placement exception.
2. Storage owns persistence and lifecycle of physical placements and replicas.
3. Artifact owns its reference to a Storage-issued ID, never the referenced identity or its placement.
4. Web, Render, Identity, Worker, and other modules use the Storage application boundary. They do not interpret opaque IDs or project raw placement authority.
5. Provider and backend implementations, including OpenDAL, may own placement/write mechanics and submit placement results or receipts through Storage-owned ports. They are not logical identity semantic owners or issuance authorities.

The target reuses `StorageObjectLocation` and `StorageReplicaRecord`. Storage persistence must represent logical objects independently of zero-to-many normalized placements/replicas, preserve typed provider/namespace/locator/version/region fields, enforce reference integrity, and retain digest/length/state needed for reconciliation. A generic JSON placement column is forbidden because it would discard typed constraints and obscure authority.

## 6. Exact schema decision

The authoritative schema decision is [schema-feasibility.tsv](storage-object-identity-placement-migration-v1/schema-feasibility.tsv): `SCHEMA_MIGRATION_REQUIRED`.

The target needs all of the following before it can own runtime truth:

- a Storage-owned logical-object relation independent of placement;
- normalized Storage-owned placement and replica relations modeled by the existing typed semantics;
- an Artifact replica reference to a proven canonical Storage object with enforceable referential integrity;
- an explicit migration classification/evidence record rather than inference from string shape;
- durable adoption receipts and a restartable journal;
- constraints that prevent one logical object plus one placement row from becoming the accidental multi-replica authority.

The existing `storage_object` relation may be read as evidence during migration, but it is neither presumed canonical nor promoted unchanged. The existing `artifact_replica` relation must be classified and migrated. Exact DDL, names, indexes, rollout mechanics, and retention are implementation-phase work; they must preserve this semantic model.

## 7. Persisted-row classifier

The classifier is exactly [persisted-row-classification-feasibility.tsv](storage-object-identity-placement-migration-v1/persisted-row-classification-feasibility.tsv) and has three exhaustive outcomes: `CANONICAL_LOGICAL`, `LEGACY_PHYSICAL_ENCODED`, and `AMBIGUOUS`.

Classification uses a deterministic evidence precedence:

1. collision-free writer provenance tied to the row and producer operation;
2. provider and replica facts tied to the same row;
3. a `storage_object` relation only when its relationship and semantics are mechanically proven;
4. typed provider `stat` or equivalent backend lookup only as corroborating mechanics; it cannot establish logical identity authority without Storage-owned issuance/adoption and persistence evidence;
5. Storage-owned physical existence and integrity facts for a candidate placement;
6. producer/job evidence only when its mapping is mechanically collision-free.

String prefix, slash count, URI shape, `obj-` shape, column name, provider name, or successful parsing is never sufficient. Conflicting evidence produces `AMBIGUOUS`. Ambiguous rows fail closed and remain unchanged unless a deterministic repair source is introduced and journaled. The classifier's feasibility passes because every input has one of the three outcomes; it does not claim every row can be repaired automatically.

## 8. Migration-only Storage owner operation

Implementation must introduce a Storage-owned operation equivalent to:

```text
adoptLegacyPlacement(
  deterministicMigrationKey,
  expectedSourceRowIdentity,
  expectedOriginalValue,
  typedPhysicalPlacement,
  expectedDigest,
  expectedLength,
  provenanceEvidence
) -> { StorageObjectId, StorageReplicaId, StorageObjectLocation, placementReceipt, outcome }
```

This is the only boundary allowed to accept a legacy physical placement for identity migration. Storage validates ownership and existence, imports/registers/copies as required, creates normalized authority, and returns a canonical ID plus receipt. Repeating the same key with the same inputs returns the same result. Reusing the key with different inputs fails closed. Normal production writers must use the Storage-owned logical identity issuance/application boundary, which may orchestrate typed provider write mechanics; they cannot treat provider completion as semantic authority or call the migration operation after its removal gate.

Adoption is not renaming. The original physical string is never copied into the logical ID as the migration result. If provider limitations require a byte copy, the copy must complete and reconcile before the receipt is terminal.

## 9. Backfill transaction, failure, idempotency, and restart law

External storage and PostgreSQL cannot participate in one ACID transaction. Backfill is therefore a restartable saga, never a claim of atomic cross-system commit.

For each candidate row the worker derives a deterministic migration key from the bound database identity, stable table/primary key, original persisted value, and classifier evidence version. It locks the row or records a compare-and-set precondition on the original value, then creates or resumes the journal. The sequence is:

1. classify and persist the evidence fingerprint;
2. call Storage adoption for a legacy row, or verify Storage authority for a canonical row;
3. durably record the idempotent placement receipt;
4. reconcile ID, provider, replica, digest, length, and physical existence;
5. switch the Artifact replica reference to the logical ID using the original-value CAS;
6. verify referential integrity and read behavior;
7. mark the journal terminal.

Crash before adoption leaves the source unchanged and is retryable. Crash during adoption is recovered by the deterministic Storage key. Crash after adoption but before receipt persistence replays and receives the same receipt. Crash after receipt but before CAS resumes at CAS. A CAS miss means concurrent change: stop, reclassify, and never overwrite. Crash after CAS but before terminal marking reconciles the current reference and receipt, then completes idempotently.

No legacy placement is deleted during adoption or reference switch. Deletion is a separate M7 Storage-owned lifecycle decision after retention, replica, integrity, zero-count, and recovery gates. A failure never causes a guessed ID, premature reference change, automatic legacy deletion, or fallback into physical parsing.

## 10. Exact M0–M7 state machine and ordering decision

The normative state machine is [migration-state-machine.tsv](storage-object-identity-placement-migration-v1/migration-state-machine.tsv). Every state M0 through M7 is required and ordered by its numeric `order`.

Reader resolution is deliberately established in M2 before writer cutover in M3. Canonical logical IDs already exist, but legacy readers parse typed values as bucket/key or URI. Cutting writers first would create additional logical IDs that those readers could misinterpret. M2 makes canonical reads Storage-owned, confines classified legacy resolution to the migration boundary, and makes ambiguous rows fail closed. Only then can M3 stop every legacy/ambiguous writer safely.

M4 adopts and backfills; M5 switches references; M6 removes compatibility and enables permanent guards; M7 closes the journal and allows Storage lifecycle cleanup. State advancement is monotonic and gate-driven. A state may be retried, but a failed gate cannot be waived by elapsed time or a feature flag.

## 11. Temporary compatibility lifecycle

Temporary compatibility is exactly [temporary-compatibility-removal-ledger.tsv](storage-object-identity-placement-migration-v1/temporary-compatibility-removal-ledger.tsv). Each item has a state-bound entry condition, Storage owner, narrow scope, objective removal criterion, and zero-count gate. Compatibility does not write two authorities and does not let product code parse physical values.

The compatibility implementation must expose metrics for entry count, successful use, failure, unresolved mismatch, and removal-gate count. A build/runtime manifest must name the item and state. An undeclared item fails closed. At M6 every item is removed from normal runtime, not merely disabled indefinitely.

Writer cutover rejects any new physical-encoded or ambiguous identity after M3. Reader cutover sends canonical IDs only to Storage resolution; the classified legacy resolver exists only until M5. There is no permanent dual write, read fallback, or dual authority.

## 12. Exit gates and measured unknowns

No row count is inferred in this decision recovery. Once a canonical database is proven and an authorized implementation observes it, progression requires all of these measured gates:

- candidate rows = canonical logical + legacy physical encoded + ambiguous, with unclassified zero;
- physical-to-logical writers, ambiguous writers, and unclassified writers are zero after M3;
- new legacy physical rows after writer cutover are zero;
- canonical rows have verified Storage object/placement facts;
- legacy active references and ambiguous active references are zero before M6;
- journal in-flight, failed-unreviewed, and receipt mismatches are zero;
- physical parsers outside Storage/migration boundary are zero;
- temporary compatibility, permanent fallback, dual write, and dual authority are zero;
- all nine guards have first demonstrated their future RED fixture and then pass GREEN without it.

Counts are tied to the bound database identity, candidate query version, evidence version, and observation timestamp. A count from any other database or container is invalid.

## 13. Rollback and no-rollback decisions

Before M3, observe-only classifiers and reader routing may roll back without data conversion. During M4–M5, a journal step retries or compensates locally; the Artifact reference may be restored only by CAS while the exact legacy placement remains retained and verified. Storage adoption is not blindly undone because it may have created a durable object or replica used by a completed retry.

After M6 there is no rollback to legacy physical parsing, dual writes, fallback, or dual authority. Corrections move forward under Storage ownership. After M7, identity and authority do not roll back; byte recovery uses approved backup/replica lifecycle mechanisms. The contract never promises atomic rollback across PostgreSQL and external storage.

## 14. H6 preservation and hardening follow-up

Frozen H6 is evidence and is not modified by this task. This migration preserves its laws: raw storage coordinates remain absent from product projections; unscoped Artifact enumeration and old Artifact access callers remain closed; old storage-URI DTO use and compatibility wrappers remain closed; Artifact, Timeline, and Storage do not gain competing Artifact authority; Storage does not become canonical media/Artifact authority; and every relevant surface remains classified.

H6's Artifact identity, lifecycle, integrity, scoped access, redacted summary/event, and typed Artifact-pin decisions remain intact. Storage ownership of object identity and placement complements rather than displaces Artifact authority.

`H6_FOLLOWUP_ENCAPSULATION_HARDENING`: the H6 `ArtifactRepository.StorageMaintenanceRecord` is public and carries raw `storageObjectId` as a string into `ArtifactStorageMaintenanceQueryAdapter`. A later bounded H6 follow-up must encapsulate that infrastructure record and replace the raw physical maintenance string with a Storage-owned, non-product migration/maintenance reference or typed port. Until then it is classified as an ambiguous reader; it must not become a product projection. No H6 source edit is authorized here.

## 15. Implementation phase boundaries

- **Phase A — schema and observation (M0–M1):** separately authorize DDL, repository, classifier, journal, database binding, and observe-only metrics.
- **Phase B — reader safety (M2):** separately authorize Storage resolver ports and removal of non-Storage ID parsing, with compatibility bounded by the ledger.
- **Phase C — writer cutover (M3):** separately authorize each inventoried writer migration to the Storage application issuance boundary and new-write rejection.
- **Phase D — backfill and switch (M4–M5):** separately authorize the Storage adoption operation, worker, CAS, reconciliation, quarantine, and production execution plan.
- **Phase E — closure (M6–M7):** separately authorize compatibility deletion, permanent guards, evidence closure, retention, and Storage-owned cleanup.

Each phase freezes an exact candidate SHA before its own verification and supplies machine-readable counts. This document is not implementation authorization.

## 16. Future guard obligations

All required controls and their executable future RED mutations are normative in [future-guard-red-control-plan.tsv](storage-object-identity-placement-migration-v1/future-guard-red-control-plan.tsv). A guard is not accepted until its forbidden fixture demonstrably fails, the fixture is removed, and the unchanged guard passes. The guards cover physical-value construction, ID parsing, Artifact/Web placement authority, dual write, compatibility fallback, raw product projection, and unclassified writers/readers.

The exact required set is:

- `BAN_PHYSICAL_VALUE_STORAGE_OBJECT_ID_CONSTRUCTION_OUTSIDE_STORAGE`
- `BAN_STORAGE_OBJECT_ID_URI_PARSING_OUTSIDE_MIGRATION_BOUNDARY`
- `BAN_ARTIFACT_DIRECT_PHYSICAL_PLACEMENT_AUTHORITY`
- `BAN_WEB_DIRECT_PHYSICAL_PLACEMENT_AUTHORITY`
- `BAN_PERMANENT_DUAL_WRITE`
- `BAN_PERMANENT_COMPATIBILITY_FALLBACK`
- `BAN_RAW_STORAGE_LOCATION_PRODUCT_PROJECTION`
- `BAN_UNCLASSIFIED_STORAGE_IDENTITY_WRITER`
- `BAN_UNCLASSIFIED_STORAGE_IDENTITY_READER`

## 17. Frozen final tokens

```text
CANONICAL_MIGRATION_CONTRACT_COUNT=1
DUAL_AUTHORITY_CONTRACT_COUNT=0
STORAGE_OBJECT_ID_MODEL=STORAGE_OBJECT_ID_IS_LOGICAL_STABLE_IDENTITY_V1
STORAGE_OBJECT_ID_OWNER=STORAGE
PHYSICAL_PLACEMENT_OWNER=STORAGE
CURRENT_RAW_STRING_STORAGE_PROVIDER_TREATED_AS_LOGICAL_ID_ISSUER_COUNT=0
PHYSICAL_REFERENCE_TO_LOGICAL_ID_AUTHORITY_COUNT=0
PROVIDER_BACKEND_LOGICAL_ID_AUTHORITY_COUNT=0
DUAL_STORAGE_IDENTITY_AUTHORITY_COUNT=0
UNCLASSIFIED_CURRENT_VS_TARGET_FACT_COUNT=0
PHYSICAL_TO_LOGICAL_ID_WRITER_COUNT=3
CANONICAL_LOGICAL_ID_WRITER_COUNT=0
AMBIGUOUS_WRITER_COUNT=2
WRITER_INVENTORY_TOTAL=5
WRITER_UNCLASSIFIED_COUNT=0
LOGICAL_ID_TO_PHYSICAL_READER_COUNT=4
CANONICAL_LOGICAL_ID_CONSUMER_COUNT=2
AMBIGUOUS_READER_COUNT=2
STORAGE_OWNER_RESOLVER_COUNT=3
READER_INVENTORY_TOTAL=11
READER_UNCLASSIFIED_COUNT=0
UNCLASSIFIED=0
CANONICAL_LOGICAL_ROW_COUNT=UNKNOWN
LEGACY_PHYSICAL_ENCODED_ROW_COUNT=UNKNOWN
AMBIGUOUS_PERSISTED_ROW_COUNT=UNKNOWN
PERSISTED_ROW_CLASSIFICATION_FEASIBILITY=PASS
SCHEMA_FEASIBILITY_DECISION=SCHEMA_MIGRATION_REQUIRED
GENERIC_JSON_PLACEMENT_ALLOWED=NO
TEMPORARY_MIGRATION_COMPATIBILITY=ALLOWED_IF_REQUIRED
PERMANENT_DUAL_WRITE_ALLOWED=NO
PERMANENT_READ_FALLBACK_ALLOWED=NO
PERMANENT_DUAL_AUTHORITY_ALLOWED=NO
NEW_LEGACY_PHYSICAL_ROWS_ALLOWED_AFTER_WRITER_CUTOVER=NO
MIGRATION_STATE_FIRST=M0
MIGRATION_STATE_LAST=M7
MIGRATION_STATE_COUNT=8
FUTURE_GUARD_COUNT=9
H6_SOURCE_EDIT_COUNT=0
H6_FOLLOWUP_ENCAPSULATION_HARDENING=REQUIRED
```

The nine ledgers and the non-production validator are part of this frozen contract. Prose cannot override a ledger row or final token; an inconsistency fails validation and requires a new governed decision.
