-- =====================================================================
-- Artifact and Provenance V1 — New Tables
-- =====================================================================
-- Forward-only, PostgreSQL 16 compatible, non-destructive, deterministic.
-- These tables implement the unified Artifact and Provenance model on top
-- of the existing Unified Storage Semantics V1.
--
-- Note: The existing `artifact` table (render-system) is NOT modified.
-- These new tables use the `media_artifact` prefix to avoid conflicts.
--
-- This migration is located in db/artifact-migration/ to avoid conflicting
-- with the mainline V1 consolidated migration. Apply separately.

-- ---------------------------------------------------------------------
-- 1. media_artifact
-- Primary identity table for artifacts.
-- ---------------------------------------------------------------------
create table media_artifact (
    id varchar(128) primary key,
    tenant_id varchar(64) not null,
    content_digest varchar(128) not null,
    byte_length bigint not null,
    media_type varchar(32) not null,
    artifact_kind varchar(32) not null,
    state varchar(32) not null,
    schema_version int not null default 1,
    created_at timestamp not null,

    -- CHECK constraints for valid enum values
    constraint chk_media_artifact_state check (state in (
        'REGISTERING', 'AVAILABLE', 'QUARANTINED', 'DELETING', 'DELETED', 'FAILED'
    )),
    constraint chk_media_artifact_kind check (artifact_kind in (
        'SOURCE_MEDIA', 'DERIVED_MEDIA', 'PROXY', 'THUMBNAIL', 'WAVEFORM',
        'SUBTITLE', 'TRANSCRIPT', 'ANALYSIS_RESULT', 'GENERATED_MEDIA',
        'RENDER_MASTER', 'DELIVERY_RENDITION', 'MANIFEST'
    )),
    constraint chk_media_artifact_media_type check (media_type in (
        'VIDEO', 'AUDIO', 'IMAGE', 'TEXT', 'BINARY', 'MULTIPLEX', 'MANIFEST'
    )),
    constraint chk_media_artifact_schema_version check (schema_version >= 1),
    constraint chk_media_artifact_byte_length check (byte_length >= 0)
);

create index ix_media_artifact_tenant_id on media_artifact(tenant_id);
create index ix_media_artifact_content_digest on media_artifact(content_digest);
create index ix_media_artifact_tenant_state on media_artifact(tenant_id, state);
create index ix_media_artifact_tenant_kind on media_artifact(tenant_id, artifact_kind);
create index ix_media_artifact_created_at on media_artifact(tenant_id, created_at desc);

-- ---------------------------------------------------------------------
-- 2. media_artifact_replica_binding
-- Binds artifacts to storage replicas.
-- ---------------------------------------------------------------------
create table media_artifact_replica_binding (
    id varchar(128) primary key,
    artifact_id varchar(128) not null,
    storage_object_id varchar(64) not null,
    storage_replica_id varchar(64) not null,
    provider_id varchar(64) not null,
    replica_role varchar(32) not null,
    region varchar(64) not null,
    created_at timestamp not null,

    -- CHECK constraint for valid replica roles
    constraint chk_replica_role check (replica_role in (
        'PRIMARY', 'SECONDARY', 'CACHE', 'ARCHIVE', 'DELIVERY'
    )),
    -- Unique binding: an artifact cannot be bound to the same replica twice
    constraint uq_artifact_replica unique (artifact_id, storage_replica_id)
);

create index ix_artifact_replica_binding_artifact_id on media_artifact_replica_binding(artifact_id);
create index ix_artifact_replica_binding_storage_replica_id on media_artifact_replica_binding(storage_replica_id);
create index ix_artifact_replica_binding_provider_id on media_artifact_replica_binding(provider_id);

-- ---------------------------------------------------------------------
-- 3. media_artifact_provenance_edge
-- DAG edges representing derivation relationships between artifacts.
-- ---------------------------------------------------------------------
create table media_artifact_provenance_edge (
    id varchar(128) primary key,
    tenant_id varchar(64) not null,
    parent_artifact_id varchar(128) not null,
    child_artifact_id varchar(128) not null,
    relation_type varchar(32) not null,
    operation_id varchar(128) not null,
    operation_version int not null default 1,
    attempt_id varchar(128) not null,
    request_digest varchar(256) not null,
    result_digest varchar(256) not null,
    created_at timestamp not null,

    -- CHECK constraints
    constraint chk_provenance_relation_type check (relation_type in (
        'GENERATED_FROM', 'TRANSCODED_FROM', 'EXTRACTED_FROM', 'COMPOSED_FROM',
        'ANALYZED_FROM', 'UPGRADED_FROM', 'DENOISED_FROM', 'SUBTITLED_FROM',
        'RENDERED_FROM'
    )),
    constraint chk_provenance_operation_version check (operation_version >= 1),
    -- Prevent self-reference at the database level
    constraint chk_provenance_no_self_ref check (parent_artifact_id != child_artifact_id),
    -- Prevent duplicate semantic edges
    constraint uq_provenance_semantic_edge unique (
        parent_artifact_id, child_artifact_id, relation_type, operation_id, attempt_id
    )
);

create index ix_provenance_edge_tenant_id on media_artifact_provenance_edge(tenant_id);
create index ix_provenance_edge_parent on media_artifact_provenance_edge(parent_artifact_id);
create index ix_provenance_edge_child on media_artifact_provenance_edge(child_artifact_id);
create index ix_provenance_edge_operation on media_artifact_provenance_edge(operation_id);
create index ix_provenance_edge_tenant_relation on media_artifact_provenance_edge(tenant_id, relation_type);

-- ---------------------------------------------------------------------
-- 4. media_artifact_commit_idempotency
-- Idempotency records for artifact commits.
-- ---------------------------------------------------------------------
create table media_artifact_commit_idempotency (
    id varchar(128) primary key,
    tenant_id varchar(64) not null,
    idempotency_key varchar(256) not null,
    artifact_id varchar(128) not null,
    storage_object_id varchar(64) not null,
    storage_replica_id varchar(64) not null,
    canonical_request_digest varchar(256) not null,
    created_at timestamp not null,

    -- Unique constraint: same tenant + idempotency_key = same commit
    constraint uq_commit_idempotency unique (tenant_id, idempotency_key)
);

create index ix_commit_idempotency_tenant_key on media_artifact_commit_idempotency(tenant_id, idempotency_key);
create index ix_commit_idempotency_artifact_id on media_artifact_commit_idempotency(artifact_id);
create index ix_commit_idempotency_created_at on media_artifact_commit_idempotency(created_at desc);
