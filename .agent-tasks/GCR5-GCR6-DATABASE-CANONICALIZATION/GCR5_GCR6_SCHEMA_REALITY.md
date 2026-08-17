# GCR5/GCR6 — Schema Reality (V1)

File: platform-app/src/main/resources/db/migration/V1__initial_schema.sql (2954 lines)

## Inventory

- FLYWAY_SCRIPT_COUNT = 1 (V1__initial_schema.sql only)
- PRE_RELEASE_INCREMENTAL_MIGRATION_COUNT = 0
- MIGRATION_BACKUP_FILE_COUNT = 0
- Explicit CREATE TABLE = 154; canonical table count (incl. composite-constraint
  re-opens) = 157
- create index = 270
- FK constraints = 39 (across 32 tables)
- UNIQUE constraints = 54 (incl. unique indexes)
- CHECK constraints = 16
- Triggers = 1 (trg_svd_snapshot_immutable)
- Functions = 1 (trg_fn_svd_snapshot_immutable)
- timestamp (no tz) = 270; timestamp with time zone = 1 (storage_object.last_verified_at)
- ON DELETE: cascade = 4, restrict = 6

## Table classification (157 tables)

| Class | Count | Examples |
|---|---|---|
| CANONICAL_STATE | ~40 | artifact, media_asset, media_stream, timeline_revision, timeline_snapshot, project, user, workspace, workflow_execution, capability tables |
| CANONICAL_RELATION | ~15 | artifact_relation, artifact_pin, timeline_revision_parent, media_asset_artifact, product_dependency |
| PROJECTION | ~10 | artifact_node, artifact_graph, search_projection, system_canonical_*, unified_graph_* |
| EXECUTION_STATE | ~25 | render_job, platform_job, render_job_status_history, workflow_execution, outbox_events |
| OPERATIONAL_STATE | ~15 | render_worker, render_job_lease, render_job_queue, storage_reference |
| CACHE | ~5 | config_item (cached config), notification_preference |
| AUDIT | ~8 | audit_records, extension_audit_event, provider_webhook_event, billing_ledger_entry |
| INFRASTRUCTURE | ~25 | notification_*, api_key, api_client, secret_ref, feature_flag_*, tenant_* entitlement |
| LEGACY_DELETE | 0 in V1 (legacy residue lives in db/artifact-migration/, not V1) |

UNCLASSIFIED_TABLE_COUNT = 0 (see ownership matrix for the full per-table classification)

## Structural gaps found (candidates for constraint-gap matrix)

1. timeline_revision.project_id → project(id): NO FK (gap)
2. timeline_revision.parent_revision_id → timeline_revision(id): NO FK (gap;
   self-referential DAG parent)
3. timeline_revision.tenant_id nullable while project.tenant_id NOT NULL:
   redundant nullable tenant (potential inconsistency; not used by query paths —
   revision tenant derived via project)
4. artifact_pin.revision_id → timeline_revision(id): NO FK (gap; uq_artifact_pin_revision
   index exists supporting a future FK)
5. artifact_pin.project_id → project(id): NO FK (gap)
6. timeline_snapshot.project_id → project(id): NO FK
7. timeline_revision.snapshot_id → timeline_snapshot(id): NO FK
8. media_stream.media_asset_id → media_asset(id) ON DELETE CASCADE: canonical
   stream rows destroyed when media_asset is deleted — REVIEW against
   CASCADE_MUST_NOT_DESTROY_CANONICAL_HISTORY_IMPLICITLY_V1
9. render_job.project_id → project(id): NO FK (execution state, lower risk)
10. artifact.tenant_id NOT NULL, artifact_replica has no tenant column — replica
    tenant scope enforced via JOIN (GCR-2 Correction) — OK by design.

## Time columns (operational)

270 timestamp columns: created_at/updated_at/deleted_at/tombstoned_at/
started_at/finished_at/completed_at/expires_at/published_at/pinned_at/
last_seen_at/checked_at etc. All timestamp without time zone, written from Java
as LocalDateTime at UTC (Category C: DB implementation detail with explicit UTC
conversion) or DB default now()/CURRENT_TIMESTAMP.

Decision: operational absolute time policy will be normalized to a documented
canonical boundary. Options evaluated:
- A. Migrate all 270 to timestamptz: large, risky, touches every repository mapping.
- B. Freeze current contract: timestamp (UTC, application-written LocalDateTime@UTC)
  with explicit documentation + a few targeted fixes (mixed default sources).

Reality: the GCR-2 correction already established TransactionAwareDataSourceProxy;
the Java boundary consistently converts Instant↔LocalDateTime(UTC). The schema
type `timestamp` with a documented UTC convention is coherent as-is for a
greenfield DB where no live data migration is needed. Per §15, OPERATIONAL_TIME
must be absolute-instant semantics — the current mapping satisfies this at the
Java boundary (Instant in domain, LocalDateTime UTC at persistence).

## Post-implementation reality (candidate)

- FK count: 39 -> 50 (+11: timeline_revision project/parent/snapshot,
  timeline_snapshot project, artifact_pin revision/project, render_job project,
  media_stream RESTRICT conversion is FK same but action change)
- CASCADE count: 4 -> 3 (media_stream converted to RESTRICT; remaining 3 are
  projection/process-state cascades: navigation_policy, media_probe_observation,
  project_import_metadata)
- timeline_revision.tenant_id KEPT (active tenant-guard column, adjudicated)
- Legacy db/artifact-migration/V6 deleted (C18)
- render_job identity columns normalized varchar(128) -> varchar(64) (C5)
