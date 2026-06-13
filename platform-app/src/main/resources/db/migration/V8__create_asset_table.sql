-- ============================================================
-- ASSET TABLE
-- ============================================================
-- Project assets (video, image, audio, subtitle files)
-- All storage keys are validated via StorageKeyPolicy

create table asset (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(128) not null,
    storage_key text not null,
    media_type varchar(32) not null,
    filename varchar(256),
    size_bytes bigint,
    checksum varchar(128),
    duration_ms bigint,
    width int,
    height int,
    created_at timestamp not null
);

create index ix_asset_tenant_project on asset(tenant_id, project_id);
create index ix_asset_tenant_created on asset(tenant_id, created_at desc);
