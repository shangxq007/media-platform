-- V6__create_project_import_metadata.sql
--
-- Create project_import_metadata table for storing imported shell metadata.
-- Part of P4-IMPORT-FROM-ZIP-2b: Timeline / Render / Spatial Plan Persistence.
--
-- This table stores JSON metadata from project-export-v1.zip imports.
-- It does NOT store media, signed URLs, or storage references.
-- All sensitive URLs are scrubbed before storage.

create table project_import_metadata (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(64) not null,
    import_id varchar(64) not null unique,
    source_project_id varchar(64),
    source_export_id varchar(64),
    schema_version varchar(32),
    timeline_json text,
    timeline_otio_json text,
    render_plan_json text,
    spatial_plan_json text,
    export_profiles_json text,
    effect_taxonomy_json text,
    applied_effects_json text,
    asset_mapping_json text,
    created_at timestamp not null default now(),

    constraint fk_import_metadata_project
        foreign key (project_id, tenant_id)
        references project(id, tenant_id)
        on delete cascade
);

create index idx_project_import_metadata_project_id
    on project_import_metadata(project_id);

create index idx_project_import_metadata_tenant_project
    on project_import_metadata(tenant_id, project_id);

create index idx_project_import_metadata_import_id
    on project_import_metadata(import_id);

create index idx_project_import_metadata_created_at
    on project_import_metadata(created_at);
