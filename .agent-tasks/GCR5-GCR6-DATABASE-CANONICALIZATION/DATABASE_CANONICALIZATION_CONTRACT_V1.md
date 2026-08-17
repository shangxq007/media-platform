# DATABASE_CANONICALIZATION_CONTRACT_V1

Milestone: GCR5_GCR6_DATABASE_CANONICALIZATION
Base: 6905160103e38c7adf8d057b7cea7caf68453dd7

Frozen bounded items (from repository reality — Phase A):

C1. PostgreSQL is canonical relational persistence, not domain authority.
C2. Exactly one pre-release Flyway V1 defines the entire canonical schema.
C3. No active V2+ incremental pre-release migrations.
C4. Every canonical relational table has one persistence owner aligned with
    domain authority.
C5. Same-context mandatory relationships are structurally enforced where
    practical — FK added for: timeline_revision.project_id → project(id);
    timeline_revision.parent_revision_id → timeline_revision(id);
    timeline_snapshot.project_id → project(id);
    timeline_revision.snapshot_id → timeline_snapshot(id);
    artifact_pin.revision_id → timeline_revision(id);
    artifact_pin.project_id → project(id);
    render_job.project_id → project(id).
C6. Cross-context semantic validation remains application/domain owned.
C7. Tenant ownership fails closed: timeline_revision.tenant_id / timeline_snapshot.tenant_id
    are KEPT — they are ACTIVE tenant-guard columns (TimelineRevisionRepository:132
    tenant guard, TimelineSnapshotService, TimelineRevisionSaveService:118), not
    decorative. No cross-tenant reference can be structurally represented through
    revision/project FKs (single-column id FKs; tenant binding follows the parent
    chain — same-context by construction).
C8. Project ownership constraints follow actual domain scoping (FK on project_id
    for canonical same-context tables; no blanket rule beyond that).
C9. Canonical historical state must not be destructively cascaded: change
    media_stream.media_asset_id ON DELETE CASCADE → RESTRICT.
    Other cascades (navigation_policy UI projection, media_probe_observation,
    project_import_metadata process state) remain — projection/process state.
C10. Operational absolute time uses the documented canonical boundary: domain
     java.time.Instant; persistence timestamp (UTC) via explicit LocalDateTime(UTC)
     conversion. storage_object.last_verified_at keeps timestamptz (already
     tz-explicit). No 270-column schema churn (greenfield, no live data; Java
     boundary already absolute-correct).
C11. Timeline MediaTime remains entirely separate from operational timestamps
     (already true: 0 media-time columns; lives in JSON/domain).
C12. Canonical required fields have correct NOT NULL; no sentinel values found.
C13. Canonical uniqueness assumptions DB-enforced only when semantic (existing
     UNIQUEs kept: artifact tenant-digest, pin per revision, revision per project).
C14. jOOQ regenerated from canonical V1 and exactly matches DB schema.
C15. Generated jOOQ source is projection-only and never edited manually.
C16. Raw canonical SQL references match current V1 (verified: RevisionCommandApply
     column lists align).
C17. Test schema fixtures must not preserve legacy database shapes (verified:
     fixtures match V1 shapes; re-verify after V1 changes).
C18. No compatibility tables/columns/views/triggers survive solely for unpublished
     old designs: DELETE platform-app/src/main/resources/db/artifact-migration/
     (unreferenced media_artifact legacy migration, currently shipped in jar).
     docs/ddl-postgresql.sql is an explicitly-marked non-authoritative archived
     document — kept as documentation (not active schema).
C19. Database triggers/functions enforce structural integrity only: KEEP
     trg_svd_snapshot_immutable (append-only canonical fact protection). No new
     domain-semantic triggers.
C20. GCR-1/GCR-2 authority guarantees remain regression-protected.

## Phase A exit criteria (all met)

TABLE_INVENTORY_COMPLETE = YES (157 canonical tables classified)
UNCLASSIFIED_TABLE_COUNT = 0
ALL_CANONICAL_WRITERS_IDENTIFIED = YES (single writer per canonical table)
ALL_RAW_SQL_PATHS_CLASSIFIED = YES
CONSTRAINT_GAP_MATRIX_COMPLETE = YES (8 gaps)
OPERATIONAL_TIME_INVENTORY_COMPLETE = YES (270 timestamp + 1 timestamptz)
JOOQ_REALITY_COMPLETE = YES (158 generated files; parity to be proven)
TEST_SCHEMA_FIXTURE_REALITY_COMPLETE = YES
CONFLICT_MATRIX_COMPLETE = YES (UNRESOLVED = 0)

## Phase B result

READY_FOR_GCR5_GCR6_DATABASE_CANONICALIZATION_IMPLEMENTATION = YES
BLOCKERS = NONE
ARCHITECTURE_ESCALATION = NONE
