# GCR5/GCR6 — Repository Reality Recovery

Base: 6905160103e38c7adf8d057b7cea7caf68453dd7 (tree c2ede6513937220c4a1138a6a9d7edfd852324a2)

## Database interaction paths

| Path | Location | Classification |
|---|---|---|
| DSLContext (typed jOOQ) | all modules via typed-schema-module | CANONICAL_PERSISTENCE |
| JdbcTemplate / NamedParameterJdbcTemplate | platform-app, workflow-module, media-module (tests), identity-access | CANONICAL_PERSISTENCE / TEST_FIXTURE |
| Raw jOOQ execute("...") | timeline-module RevisionCommandApplyService (apply_command, timeline_revision_ref, timeline_snapshot, timeline_revision, timeline_revision_parent) | CANONICAL_PERSISTENCE |
| Flyway V1 | platform-app/src/main/resources/db/migration/V1__initial_schema.sql | CANONICAL |
| Test schema fixtures | media-module ITs, render-module ITs, workflow-module test, platform-app OIDC tests, artifact-module testutil | TEST_FIXTURE |
| Legacy migration residue | platform-app/src/main/resources/db/artifact-migration/V6__artifact_and_provenance_v1.sql | LEGACY_DELETE (unreferenced, media_artifact design) |
| Non-authoritative DDL doc | docs/ddl-postgresql.sql (explicitly marked NOT SOURCE OF TRUTH) | DOCUMENTATION (keep) |

## Canonical table writers (from ownership matrix)

Every canonical domain table has exactly one production writer aligned with its
domain authority. No competing canonical writers found (GCR-2 eliminated the
storage dual-write; media_artifact legacy migration is dead code, not a writer).

CANONICAL_WRITER_CONFLICT_COUNT_BEFORE = 0

## Reality-correction decisions (post-recovery)

- timeline_revision.tenant_id / timeline_snapshot.tenant_id KEPT: active tenant-guard
  columns used by TimelineRevisionRepository/TimelineSnapshotService/TimelineRevisionSaveService.
- render_job FK declared via ALTER after project table (forward-reference ordering).
- artifact_pin revision/project FKs declared via ALTER after timeline_revision (forward FK).
- docs/ddl-postgresql.sql is explicitly-marked non-authoritative archived documentation — KEPT.

## Key reality facts

- V1: 154 explicit CREATE TABLE + 3 composite-constraint follow-ups (artifact_relation
  FKs, media_stream UNIQUEs, source_visual_description_snapshot composite FKs) =
  157 canonical tables (matches GCR-2 count; jOOQ generated 158 files incl. non-table records).
- FLYWAY_SCRIPT_COUNT = 1, PRE_RELEASE_INCREMENTAL_MIGRATION_COUNT = 0.
- 270 `timestamp` (no timezone) columns vs 1 `timestamp with time zone`
  (storage_object.last_verified_at). Java side: LocalDateTime + explicit UTC
  offset conversion (Category C — DB implementation detail with explicit UTC).
- Timeline MediaTime is NOT stored in any DB timestamp column (0 hits; lives in
  JSON payloads / domain objects). No conflation found.
- 1 trigger + 1 function: trg_svd_snapshot_immutable (source_visual_description_snapshot
  append-only protection). Structural integrity trigger — KEEP.
- 4 ON DELETE CASCADE: navigation_policy (UI projection), media_stream→media_asset
  (canonical relationship — REVIEW), media_probe_observation→media_asset (probe
  observations), project_import_metadata→project (process state). media_stream
  cascade is the only one touching canonical domain state.
- 39 FK constraints across 32 tables. timeline_revision itself has ZERO FK
  constraints (project_id → project missing; parent_revision_id self-FK missing;
  tenant_id nullable while project.tenant_id NOT NULL).
- artifact_pin: FK artifact_id → artifact(id) RESTRICT present; revision_id →
  timeline_revision(id) FK MISSING (unique index uq_artifact_pin_revision exists);
  project_id → project(id) FK MISSING.
- artifact_replica: FK artifact_id → artifact(id) present.
- jOOQ generated 158 files from V1 — parity to be proven with empty-PG regen.

## Writers identified

| Table | Writer | Module | Type |
|---|---|---|---|
| artifact | ArtifactRepository.insert/insertRaw | artifact-module | CANONICAL |
| artifact_replica | ArtifactRepository.insertReplica | artifact-module | CANONICAL |
| artifact_pin | ArtifactPinRepository | artifact-module | CANONICAL |
| artifact_relation | ArtifactRelationRepository.save | artifact-module | CANONICAL |
| artifact_node/graph | JooqArtifactGraphRepository (render) | render-module | PROJECTION |
| timeline_revision (+parent/ref/snapshot/apply_command) | TimelineRevisionService.recordRevision / RevisionCommandApplyService | timeline-module | CANONICAL |
| media_asset | media-module | media-module | CANONICAL |
| media_stream | media-module | media-module | CANONICAL |
| workflow_* | UserWorkflowDefinitionJdbcRepository | workflow-module | CANONICAL |
| project / user / workspace | identity-access-module | identity-access | CANONICAL |

ALL_CANONICAL_WRITERS_IDENTIFIED = YES
