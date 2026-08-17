# GCR-2: Artifact Reference Persistence and Reproducibility Authority

Status: CLOSED_PENDING_CHATGPT_FINAL_REVIEW
Milestone: GCR2_ARTIFACT_REFERENCE_PERSISTENCE_AND_REPRODUCIBILITY_AUTHORITY

## Legal Base

- LEGAL_BASE_SHA = `3a6d2618151bc2702356bdf27bd0d174946456d4`
- LEGAL_BASE_TREE = `35af2f8a426d5398ea9584ee881f6a5103a16e2e`

## Repository Reality Findings (Phase A)

1. **Original artifact table was insufficient**: the pre-GCR-2 `artifact` table was
   URI-centric (`storage_uri` NOT NULL) with no `content_digest`, no `tenant_id`,
   and a render-oriented shape — it could not serve as canonical Artifact identity
   or integrity authority.
2. **Storage/artifact double-write**: both artifact-module (`ArtifactCatalogRepository`)
   and storage-module (`ArtifactRepository`) wrote the SAME `artifact` table with
   different shapes — two parallel persistence authorities for one logical entity.
3. **Shared-kernel `ArtifactRef`** was a legacy composite (id + uri + format) that
   was never constructed in production (always `List.of()`), yet occupied the
   capability boundary and blocked typed identity.
4. **Timeline pin validation (#14) was unwired**: `TimelineSourceReferenceValidator`
   existed but was not called from any production path; `MediaStreamSourceBinding`
   already carried ArtifactId + ContentDigest pins.

## Frozen Contract

`ARTIFACT_AUTHORITY_CONTRACT_V1` (C1–C20) — full text in
`.agent-tasks/GCR2-ARTIFACT-AUTHORITY/ARTIFACT_AUTHORITY_CONTRACT_V1.md` (frozen,
no weakening). 13 conflicts adjudicated in `GCR2_CONFLICT_MATRIX.md`.

Bounded refinements during implementation (contract-faithful):

- `ArtifactCommitRequest` gained nullable `renderJobId` / `projectId` provenance
  trace fields (schema already carried the nullable columns; trace is never
  identity/lookup).
- `ArtifactPin` FK to `artifact` enforced at DB level (phantom pins rejected).
- Render execution moved outside write transactions
  (`RENDER_EXECUTION_OUTSIDE_WRITE_TRANSACTION_V1`) — a latent self-deadlock
  exposed when jOOQ began participating in Spring transactions.

## ContentDigest Move

- Rehomed: storage-module → shared-kernel `shared.digest.ContentDigest`.
- 40 explicit imports + 5 fully-qualified inline references migrated.
- Dependency result: timeline-module no longer depends on storage-module for
  ContentDigest (TIMELINE_TO_STORAGE_DEPENDENCY_FOR_CONTENT_DIGEST_ONLY = 0).
- CONTENT_DIGEST_SINGLE_TYPE_COUNT = 1.

## ArtifactRef Retirement

- Deleted `shared-kernel/.../shared/capability/ArtifactRef.java`.
- `PluginExecutionResult.artifactRefs: List<ArtifactRef>` → `List<ArtifactId>`.
- K1 guard flipped to assert retirement; extension AR-PRV2-07 updated.
- SHARED_KERNEL_ARTIFACT_REF_TYPE_COUNT = 0, PRODUCTION_USAGE_COUNT = 0.

## Canonical Artifact Model

`artifact` table (canonical record):

- `id` (ArtifactId — stable logical identity)
- `tenant_id` NOT NULL
- `content_digest` (ContentDigest — immutable byte-integrity assertion)
- `byte_length`, `media_type`, `artifact_kind`, `state`, `schema_version`
- `project_id` / `render_job_id` nullable (provenance trace, not identity)
- `storage_uri` REMOVED (STORAGE_URI_AS_ARTIFACT_IDENTITY_COUNT = 0)

## Replica Model

`artifact_replica` (artifact_id, replica_id, provider_id, storage_object_id,
region, role, state, created_at) — PK (artifact_id, replica_id), FK → artifact.

- Multiple replicas = one logical Artifact (identity stable across replica
  changes).
- Role enum: PRIMARY / SECONDARY / CACHE / ARCHIVE / DELIVERY.
- Storage location is NOT identity.

## Pin Model

`artifact_pin` (pin_id, revision_id, project_id, artifact_id, content_digest,
pinned_at) — UNIQUE(revision_id, artifact_id), FK → artifact.

- `artifact_pin` is a historical-protection / reproducibility projection.
- It is NOT a Timeline semantic authority
  (ARTIFACT_PIN_TIMELINE_SEMANTIC_AUTHORITY_COUNT = 0): it stores no track/clip/
  source-range/timeline-range structure. Timeline revision payload remains the
  canonical semantic truth.

## Catalog

`ArtifactCatalogService` / `ArtifactCatalogRepository` are projection-only:

- Repository = read-only projection over the canonical `artifact` table.
- Service routes canonical commits through `ArtifactCommitService` when a checksum
  is present; otherwise the entry stays in-memory (projection-only, cannot
  masquerade as persisted canonical existence).
- ARTIFACT_CATALOG_CANONICAL_AUTHORITY_COUNT = 0,
  ARTIFACT_CATALOG_DIRECT_CANONICAL_WRITE_COUNT = 0.

## Storage

Storage is the physical data plane:

- `StorageCatalogService` / `StorageCatalogPort` = provider/data-plane authority
  (providerCodes etc.), no canonical Artifact writes.
- storage-module `ArtifactRepository` (dual-write) DELETED.
- STORAGE_ARTIFACT_CANONICAL_AUTHORITY_COUNT = 0,
  STORAGE_ARTIFACT_LIFECYCLE_DECISION_AUTHORITY_COUNT = 0.

## Render

Render is Artifact producer/consumer through Artifact-owned services:

- `RenderArtifactStorageService`: uploads via BlobStorage, computes SHA-256,
  commits canonical Artifact via ArtifactCommitService (with renderJobId/projectId
  trace).
- `RenderArtifactQueryService`: queries via ArtifactQueryService + catalog
  projection; reads bytes via BlobStorage/replica.
- No direct artifact-table authority, no URI identity, no Render Artifact
  lifecycle authority.

## Timeline Reference-Integrity Validation (Roadmap #14 closure)

New revision flow (TimelineRevisionService.recordRevision, single @Transactional):

1. Canonical E1b gate
2. Artifact existence + tenant + digest validation
   (TimelineArtifactPinExtractor → TimelineArtifactPinValidator → ArtifactQueryService)
3. Revision insert
4. artifact_pin registration (same transaction)
5. CAS/current-revision update (same transaction)
6. Commit

- EXISTENCE_VALIDATION = PASS, TENANT_VALIDATION = PASS, DIGEST_VALIDATION = PASS
- Timeline may depend on Artifact-facing query contracts only (timeline → artifact
  module edge, documented in ModularityTest). No cycle
  (ARTIFACT_TO_TIMELINE_DEPENDENCY_COUNT = 0).

## Atomicity

- SUCCESSFUL_REVISION ⇒ ALL_REQUIRED_PROTECTION_PINS_EXIST
- PIN_REGISTRATION_FAILURE ⇒ full transaction rollback
  (revision row = 0, current-revision advance = 0, partial pin writes = 0)

Defect found and fixed by the hard failure-injection test
(`Gcr2PinRegistrationFailureRollbackTest`): jOOQ DSLContext was built from a bare
DataSource, so jOOQ connections bypassed Spring transaction management — the
`@Transactional` annotation alone did not provide rollback. Both DSLContext beans
now wrap `TransactionAwareDataSourceProxy` (datasource-module + platform-app).

## GC / Deletion Rules

- Pinned Artifact logical delete → REJECT
- Pinned last usable replica delete → REJECT
- Pinned multi-replica policy → conservative REJECT (documented bounded policy)
- Phantom pin → rejected by DB FK
- GC: pinned artifacts skipped; old unpinned tombstoned artifacts may purge
  (state → DELETED); young artifacts retained

## Database Consolidation

- FLYWAY_SCRIPT_COUNT_BEFORE = 7 → AFTER = 1
- PRE_RELEASE_INCREMENTAL_MIGRATION_COUNT_BEFORE = 6 → AFTER = 0
- MIGRATION_BACKUP_FILE_COUNT = 0
- V2–V7 semantic effects folded into canonical `V1__initial_schema.sql` as final
  forms (timeline_revision_ref, apply_command incl. command_domain,
  timeline_revision_parent, project_revision_counter,
  source_visual_description_snapshot, deferrable FKs, ownership FK/UNIQUEs,
  snapshot composite PK, immutable trigger) — no migration archaeology.
- EMPTY_POSTGRES_V1_BOOTSTRAP = PASS, CANONICAL_V1_TABLE_COUNT = 157

## jOOQ Parity

- JOOQ_VERSION = 3.19.30, regeneration self-contained
  (ENVIRONMENT_SPECIFIC_MIRROR_CANONICAL_AUTHORITY = NO)
- GENERATED_TABLE_COUNT = 157, DB_TABLE_COUNT = 157
- JOOQ_SCHEMA_PARITY = EXACT (MISSING = [], STALE = [])

## Tests

Final counts (authoritative clean run):

- WHOLE_REPOSITORY_SUITES / TESTS / FAILURES / ERRORS / SKIPPED — see evidence
  `tests/` and the final report.
- New GCR-2 test groups:
  - A1–A5 Artifact identity (`ArtifactIdentityTest`)
  - T1–T5 pin validator (`TimelineArtifactPinValidatorTest`)
  - T6/T7 revision+pin integration incl. hard failure-injection rollback
    (`TimelineRevisionServiceTest` additions, `Gcr2PinRegistrationFailureRollbackTest`)
  - R1–R3/R5 + S2 protection (`ArtifactPinProtectionTest`)
  - GC pin-aware tests (`ArtifactGcServiceTest`)
- Removed tests (authority-following): storage `ArtifactRepositoryTest`
  (dual-write authority deleted), storage `ContentDigestTest` (type moved to
  shared-kernel).
- Updated tests: migration-count assertions 7 → 1 (single-V1 rule),
  StorageOwnershipArchitectureTest (ContentDigest ownership follows move),
  render characterization tests (catalog mock instance), extension AR-PRV2-07
  (ArtifactRef → List<ArtifactId>), K1 guard (ArtifactRef retired).

## Gates

GCR1_GUARD, GCR2_GUARD, JOOQ_FOUNDATION, MODULITH, ARCHITECTURE_DRIFT,
MAP_DRIFT, MAP_DETERMINISM, FLYWAY_SINGLE_V1, EMPTY_POSTGRES_BOOTSTRAP,
JOOQ_PARITY, BOOTJAR, PFIRR1, CREDENTIAL_SCAN, MANIFEST_REALITY_CHECK,
GREENFIELD_RESIDUE — all PASS (see evidence `guards/` and final report).

## Semantic Preservation

GCR-2 changed acceptance of invalid NEW revisions (pin reference-integrity);
it did NOT rewrite valid canonical Timeline semantics:

- Timeline canonical serialization / content hash / SourceBinding semantics /
  exact MediaTime / semantic diff / semantic merge / Timeline patch /
  revision graph / current-revision CAS / render revision pinning / AudioMix /
  TextElement / OperationPlan — PASS (no canonical rewrites; GCR-1 regression
  guard green).

## Greenfield Residue

LEGACY_ARTIFACT_DOMAIN_TYPE_COUNT = 0, COMPATIBILITY_ARTIFACT_WRAPPER_COUNT = 0,
DEPRECATED_ARTIFACT_ALIAS_COUNT = 0, DUAL_ARTIFACT_READ_COUNT = 0,
DUAL_ARTIFACT_WRITE_COUNT = 0, STORAGE_URI_AS_ARTIFACT_IDENTITY_COUNT = 0,
ACTIVE_PARALLEL_ARTIFACT_AUTHORITY_COUNT = 0, SHARED_KERNEL_ARTIFACT_REF_TYPE_COUNT = 0,
OLD_STORAGE_ARTIFACT_REPOSITORY_COUNT = 0, PRE_RELEASE_INCREMENTAL_MIGRATION_COUNT = 0,
MIGRATION_BACKUP_FILE_COUNT = 0.

## Manifest

`.agent-tasks/GCR2-ARTIFACT-AUTHORITY/GCR2_ARTIFACT_AUTHORITY_MANIFEST.tsv`:
TOTAL 57 = KEEP 45 / EVOLVE 7 / DELETE 1 / REPLACE 1 / MOVE 1 / SPLIT 2;
UNCLASSIFIED = 0, FINAL_LOCATION_MISMATCH = 0, FINAL_AUTHORITY_MISMATCH = 0.

## Candidate

- GCR2_IMPLEMENTATION_CANDIDATE_SHA / TREE — recorded in the final Hermes report
  (self-reference not embedded by design).
- Ancestry: 3a6d2618 → candidate (linear, append-forward; no merge/rebase/squash).

## Final FCV

GCR2_ARTIFACT_AUTHORITY_FINAL_FCV = PASS (evidence `fcv/`; A–J domains verified).
