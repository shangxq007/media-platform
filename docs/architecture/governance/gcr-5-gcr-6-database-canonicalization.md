# GCR-5 / GCR-6 Database Canonicalization — Publication

Status: CLOSED_PENDING_CHATGPT_FINAL_REVIEW
Milestone: GCR5_GCR6_DATABASE_CANONICALIZATION

## Base

- BASE_SHA = 6905160103e38c7adf8d057b7cea7caf68453dd7
- BASE_TREE = c2ede6513937220c4a1138a6a9d7edfd852324a2

## Reality recovery

- Baseline: 157 canonical tables (154 CREATE TABLE + composite re-opens + "user"),
  270 `timestamp` + 1 `timestamptz`, 39 FK, 54 UNIQUE, 16 CHECK, 1 trigger, 1 function
- Writer ownership: single canonical writer per domain table (artifact-module owns
  artifact/replica/pin/relation; timeline-module owns revision/snapshot/ref/parent/
  apply_command; media-module owns media_asset/media_stream; identity-access owns
  project/user/workspace; render owns render_job execution state)
- Constraint findings: 8 (FK gaps: timeline_revision project/parent/snapshot,
  timeline_snapshot project, artifact_pin revision/project, render_job project,
  media_stream cascade)
- Tenant ownership finding: 1 (timeline_revision.tenant_id nullable) — adjudicated
  KEEP as ACTIVE tenant-guard column (used by TimelineRevisionRepository:132,
  TimelineSnapshotService, TimelineRevisionSaveService:118)
- Project ownership gaps: 4 (all resolved via FK)
- Operational time: 270 timestamp (UTC boundary, Category C) + 1 timestamptz —
  no Category A/D misuse; Timeline MediaTime lives in JSON/domain, 0 timestamp columns
- Raw SQL: RevisionCommandApplyService column lists match V1 (STALE = 0)
- Test fixtures: match V1 shapes (LEGACY_FIXTURE = 0)
- Legacy residue: db/artifact-migration/V6 (unreferenced media_artifact design) — DELETED
- docs/ddl-postgresql.sql: explicitly-marked non-authoritative archived document — KEPT

## Contract

DATABASE_CANONICALIZATION_CONTRACT_V1 (C1–C20) frozen. PostgreSQL = canonical
relational persistence, NOT domain authority; jOOQ = typed projection, NOT domain
model; one canonical V1; no V2+ pre-release migrations; same-context structural
integrity DB-enforced; cross-context semantic validity domain-owned; operational
time != Timeline MediaTime; GCR1/GCR2 authority preserved.

## Implementation (final V1 structural changes)

- timeline_revision: +FK project_id → project(id), +self-FK parent_revision_id →
  timeline_revision(id), +FK snapshot_id → timeline_snapshot(id) — all RESTRICT
- timeline_snapshot: +FK project_id → project(id) — RESTRICT
- artifact_pin: +FK revision_id → timeline_revision(id), +FK project_id →
  project(id) — RESTRICT (declared via ALTER after timeline_revision, forward FK)
- render_job: +FK project_id → project(id); project_id/timeline_snapshot_id
  varchar(128) → varchar(64) (identity type normalization)
- media_stream: media_asset_id ON DELETE CASCADE → RESTRICT (C9 historical safety)
- Legacy db/artifact-migration/ deleted (C18)
- timeline_revision.tenant_id / timeline_snapshot.tenant_id KEPT (active guards)

## Database (empty PG bootstrap, final V1)

- EMPTY_POSTGRES_V1_BOOTSTRAP = PASS (157 tables)
- FINAL_FK_COUNT = 50 (39 → 50)
- FINAL_CASCADE_COUNT = 3 (4 → 3; remaining: navigation_policy UI projection,
  media_probe_observation, project_import_metadata process state)
- FINAL_TRIGGER_COUNT = 1 (trg_svd_snapshot_immutable — structural append-only)
- FINAL_FUNCTION_COUNT = 1

## jOOQ

- JOOQ_VERSION = 3.19.30, JOOQ_REGENERATION = PASS
- DB_TABLE_COUNT = 157, JOOQ_TABLE_COUNT = 157
- MISSING_TABLES = [], STALE_TABLES = []
- JOOQ_SCHEMA_PARITY = EXACT
- Generated files reflect all new FKs (Keys.java) and varchar(64) identity types

## Tests

- WHOLE_REPOSITORY = 909 suites / 7167 tests / 0 failures / 0 errors / 43 skipped
- Delta vs baseline (907/7156/43): +2 suites, +11 tests — all additions:
  Gcr5Gcr6DatabaseStructuralIntegrityTest (8), Gcr5Gcr6OperationalTimeRoundTripTest (3)
- NO_UNEXPLAINED_TEST_DELETION = PASS; NO_ASSERTION_WEAKENING = PASS
- T7 atomicity regression (Gcr2PinRegistrationFailureRollbackTest) = PASS (adapted
  to new project FK by seeding canonical project state — not weakened)
- Targeted structural tests = PASS (same-owner valid, unknown project/parent/
  revision reject, media_asset delete protection)

## Operational time

- OPERATIONAL_TIME_ABSOLUTE_SEMANTICS = PASS (domain Instant; persistence UTC via
  explicit LocalDateTime(UTC) boundary)
- NON_UTC_JVM_ROUND_TRIP = PASS (-Duser.timezone=America/Los_Angeles: absolute
  Instant round-trip equal; pre/post DST-transition 2s exact)
- UNCLASSIFIED_TIME_SEMANTICS_COUNT = 0
- TIMELINE_MEDIA_TIME_AS_OPERATIONAL_TIMESTAMP_COUNT = 0
- TIMELINE_MEDIA_TIME_SEPARATION = PASS

## Gates

GCR1 = PASS, GCR2 = PASS, GCR2_CORRECTION_V1 = PASS, GCR5_GCR6_GUARD = PASS,
JOOQ_FOUNDATION = PASS, MODULITH = PASS, ARCHITECTURE_DRIFT = PASS (224),
MAP_DRIFT = PASS (41/23/3), MAP_DETERMINISM = PASS (3x byte-identical
4a5f7a9f4ca4a8cd997962f1728de31968dfef648d2360da2db95d203fa9f144),
BOOTJAR = PASS, PFIRR1 = PASS, CREDENTIAL_SCAN = 0, GREENFIELD_RESIDUE = PASS
(12/12 zero), MANIFEST_REALITY_CHECK = PASS

## Candidate

- CANDIDATE_SHA = edf9b97b5d4c0722c9afd832bec4bb9c42e0af67
- CANDIDATE_TREE = d6b9e1bd4be6ffca96d7c07b2924935aea8c1a13
- Ancestry: 69051601 → edf9b97b (single commit, linear, no merge/rebase/squash)
- Candidate contains NO publication claims (hard governance requirement)

## Final FCV

GCR5_GCR6_DATABASE_CANONICALIZATION_FINAL_FCV = PASS (27/27) — run against the
frozen candidate edf9b97b before this publication was created.

## Deferred findings

- DEFERRED_FINDING: ArtifactCatalogService may return an ArtifactCatalogEntry
  after canonical commit failure (Timeline validation uses canonical
  ArtifactQueryService so projection-only entries cannot masquerade). Owner:
  artifact. Risk: low. Recommended: future milestone.
- DEFERRED_FINDING: ArtifactLifecycleService retains URI-era reference scanning
  (ArtifactCatalogEntry.storageUri). Owner: artifact. Risk: low. Recommended:
  future milestone.
