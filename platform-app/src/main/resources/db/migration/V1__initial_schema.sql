-- Greenfield initial schema baseline.
-- Consolidated from development migrations V1-V4.
-- This is the single source of truth for the database schema.
-- Post-release: migrations append only (V2, V3, ...).

-- Initial PostgreSQL schema baseline.
-- This baseline is valid for pre-production/greenfield resettable environments.
-- Do not rewrite migration history for non-resettable production databases.

-- ============================================================
-- 1. CORE INFRASTRUCTURE
-- ============================================================
-- render_job, outbox_events, audit_records, schedules, config_item,
-- storage_object, cloud_resource_definition, secret_ref, app_datasource,
-- quota_definitions, notification_event, notification_template,
-- notification_delivery, notification_record

create table render_job (
    id varchar(64) primary key,
    project_id varchar(64) not null,
    timeline_snapshot_id varchar(64) not null,
    profile varchar(128) not null,
    status varchar(32) not null,
    created_at timestamp not null,
    ai_script text,
    artifact_uri text,
    error_message text,
    tenant_id varchar(64),
    pipeline_plan_json text,
    pipeline_execution_json text,
    base_job_id varchar(64),
    trace_id varchar(128)
);

create index ix_render_job_project_id on render_job(project_id);
create index ix_render_job_status on render_job(status);
create index ix_render_job_base_job_id on render_job(base_job_id);
create index ix_render_job_trace_id on render_job(trace_id);

create table outbox_events (
    id varchar(64) primary key,
    aggregate_type varchar(100) not null,
    aggregate_id varchar(100) not null,
    event_type varchar(150) not null,
    event_version int not null,
    payload text not null,
    status varchar(50) not null,
    created_at timestamp not null,
    published_at timestamp,
    retry_count int not null default 0,
    next_attempt_at timestamp,
    idempotency_key varchar(255),
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(100),
    max_retries INTEGER NOT NULL DEFAULT 3
);

create index ix_outbox_events_status_created_at on outbox_events(status, created_at);
create index ix_outbox_events_aggregate on outbox_events(aggregate_type, aggregate_id);
create index ix_outbox_events_idempotency_key on outbox_events(idempotency_key);
create index ix_outbox_events_next_attempt_at on outbox_events(next_attempt_at);
create index ix_outbox_events_locked_at ON outbox_events (locked_at) WHERE locked_at IS NOT NULL;

create table platform_job (
    id varchar(64) primary key,
    job_type varchar(64) not null,
    aggregate_type varchar(32) not null,
    aggregate_id varchar(64) not null,
    tenant_id varchar(64),
    project_id varchar(64),
    status varchar(32) not null default 'PENDING',
    required_mask int not null default 0,
    completed_mask int not null default 0,
    failed_mask int not null default 0,
    total_task_count int not null default 0,
    completed_task_count int not null default 0,
    failed_task_count int not null default 0,
    payload_json text,
    metadata_json text,
    created_at timestamp not null,
    updated_at timestamp,
    completed_at timestamp
);

create index ix_platform_job_aggregate on platform_job(aggregate_type, aggregate_id);
create index ix_platform_job_status on platform_job(status);

create table platform_task (
    id varchar(64) primary key,
    job_id varchar(64) not null references platform_job(id),
    task_type varchar(64) not null,
    capability varchar(64) not null,
    provider varchar(64),
    status varchar(32) not null default 'PENDING',
    attempt_count int not null default 0,
    max_attempts int not null default 3,
    result_ref varchar(256),
    result_json text,
    error_message text,
    bit_position int not null default 0,
    started_at timestamp,
    completed_at timestamp,
    created_at timestamp not null,
    updated_at timestamp,
    constraint uq_platform_task_job_bit unique(job_id, bit_position)
);

create index ix_platform_task_job on platform_task(job_id);
create index ix_platform_task_capability_status on platform_task(capability, status);

create table audit_records (
    id varchar(64) primary key,
    actor_type varchar(50) not null,
    actor_id varchar(100),
    action varchar(120) not null,
    resource_type varchar(120) not null,
    resource_id varchar(120),
    payload text,
    created_at timestamp not null,
    category varchar(50)
);

create index ix_audit_records_created_at on audit_records(created_at);
create index ix_audit_records_actor_id on audit_records(actor_id);
create index ix_audit_records_resource on audit_records(resource_type, resource_id);

create table schedules (
    id varchar(64) primary key,
    schedule_code varchar(100) not null,
    handler_code varchar(120) not null,
    enabled boolean not null,
    created_at timestamp not null
);

create index ix_schedules_schedule_code on schedules(schedule_code);

create table config_item (
    id bigint generated by default as identity primary key,
    namespace_key varchar(128) not null,
    config_key varchar(128) not null,
    value_json text not null,
    value_version int not null,
    updated_at timestamp not null,
    unique(namespace_key, config_key, value_version)
);

create index ix_config_item_namespace_key on config_item(namespace_key);

create table storage_object (
    id varchar(64) primary key,
    provider_code varchar(64) not null,
    bucket varchar(128) not null,
    object_key text not null,
    content_type varchar(255),
    checksum_sha256 varchar(128),
    file_size_bytes bigint,
    lifecycle_policy varchar(64),
    created_at timestamp not null
);

create index ix_storage_object_provider_code on storage_object(provider_code);
create index ix_storage_object_bucket on storage_object(bucket);

create table cloud_resource_definition (
    id varchar(64) primary key,
    provider_code varchar(64) not null,
    resource_type varchar(64) not null,
    logical_name varchar(128) not null,
    spec_json text not null,
    status varchar(32) not null,
    created_at timestamp not null
);

create table secret_ref (
    id varchar(64) primary key,
    namespace_key varchar(128) not null,
    secret_key varchar(128) not null,
    backend_type varchar(64) not null,
    backend_ref varchar(255) not null,
    created_at timestamp not null,
    unique(namespace_key, secret_key)
);

create table app_datasource (
    id varchar(64) primary key,
    datasource_code varchar(64) not null unique,
    datasource_kind varchar(32) not null,
    dialect varchar(32),
    jdbc_url text,
    secret_ref varchar(255),
    is_primary boolean not null,
    usage_role varchar(64) not null,
    created_at timestamp not null
);

create table quota_definitions (
    id varchar(64) primary key,
    quota_code varchar(80) not null,
    unit varchar(50) not null,
    created_at timestamp not null
);

create table notification_event (
    id varchar(64) primary key,
    event_type varchar(128) not null,
    subject_id varchar(128),
    payload text not null,
    created_at timestamp not null
);

create index ix_notification_event_event_type on notification_event(event_type);
create index ix_notification_event_created_at on notification_event(created_at);

create table notification_template (
    id bigint generated by default as identity primary key,
    template_code varchar(64) not null,
    channel varchar(32) not null,
    locale varchar(16) not null,
    version int not null,
    subject_template varchar(255),
    body_template text not null,
    unique(template_code, channel, locale, version)
);

create table notification_delivery (
    id varchar(64) primary key,
    event_id varchar(64) not null,
    channel varchar(32) not null,
    provider_code varchar(64) not null,
    status varchar(32) not null,
    request_payload text,
    response_payload text,
    attempt_count int not null,
    created_at timestamp not null
);

create index ix_notification_delivery_event_id on notification_delivery(event_id);
create index ix_notification_delivery_status on notification_delivery(status);

create table notification_record (
    id varchar(64) primary key,
    event_id varchar(64) not null,
    channel varchar(32) not null,
    provider_code varchar(64) not null,
    status varchar(32) not null,
    subject varchar(512),
    body text,
    metadata_json text,
    attempt_count int not null default 1,
    created_at timestamp not null
);

create index ix_notification_record_event_id on notification_record(event_id);
create index ix_notification_record_status on notification_record(status);

-- ============================================================
-- 2. IDENTITY & ACCESS
-- ============================================================
-- tenant, project, "user", api_key, workspace, workspace_member,
-- workspace_group, workspace_group_member, role, permission,
-- role_permission, user_role_assignment, group_role_assignment,
-- service_account, api_client

create table tenant (
    id varchar(64) primary key,
    name varchar(255) not null,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null
);

create table project (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    name varchar(255) not null,
    description text,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null,
    constraint fk_project_tenant foreign key (tenant_id) references tenant(id) on delete restrict,
    constraint uq_project_tenant_id unique (tenant_id, id)
);

create index ix_project_tenant_id on project(tenant_id);

-- GCR5/GCR6 (C5): render_job.project_id references project — declared here
-- because render_job precedes project in the script (forward FK).
alter table render_job
    add constraint fk_render_job_project
        foreign key (project_id) references project(id) on delete restrict;

create table "user" (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    username varchar(128) not null,
    email varchar(255) not null,
    role varchar(32) not null default 'MEMBER',
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null
);

create index ix_user_tenant_id on "user"(tenant_id);

create table api_key (
    id varchar(64) primary key,
    tenant_id varchar(64),
    fingerprint varchar(32) not null,
    hashed_key varchar(128) not null unique,
    principal varchar(255) not null,
    created_at timestamp not null,
    last_used_at timestamp,
    revoked_at timestamp
);

create index ix_api_key_fingerprint on api_key(fingerprint);

create table workspace (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    name varchar(255) not null,
    description text,
    plan_tier varchar(64) not null default 'FREE',
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null,
    updated_at timestamp not null
);

create index ix_workspace_tenant_id on workspace(tenant_id);

create table workspace_member (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    user_id varchar(64) not null,
    role varchar(64) not null,
    status varchar(32) not null default 'ACTIVE',
    joined_at timestamp not null,
    updated_at timestamp not null
);

create index ix_workspace_member_workspace_id on workspace_member(workspace_id);
create index ix_workspace_member_user_id on workspace_member(user_id);

create table workspace_group (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    name varchar(255) not null,
    description text,
    created_at timestamp not null
);

create index ix_workspace_group_workspace_id on workspace_group(workspace_id);

create table workspace_group_member (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    group_id varchar(64) not null,
    member_id varchar(64) not null,
    created_at timestamp not null
);

create index ix_workspace_group_member_group_id on workspace_group_member(group_id);
create index ix_workspace_group_member_member_id on workspace_group_member(member_id);

create table role (
    id varchar(64) primary key,
    role_key varchar(128) not null unique,
    name varchar(255) not null,
    description text,
    scope varchar(32) not null,
    created_at timestamp not null
);

create table permission (
    id varchar(64) primary key,
    permission_key varchar(128) not null unique,
    name varchar(255) not null,
    description text,
    resource_type varchar(128),
    created_at timestamp not null
);

create table role_permission (
    id varchar(64) primary key,
    role_id varchar(64) not null,
    permission_id varchar(64) not null,
    created_at timestamp not null
);

create index ix_role_permission_role_id on role_permission(role_id);

create table user_role_assignment (
    id varchar(64) primary key,
    tenant_id varchar(64),
    workspace_id varchar(64),
    user_id varchar(64) not null,
    role_id varchar(64) not null,
    assigned_by varchar(64),
    created_at timestamp not null
);

create index ix_user_role_assignment_user_id on user_role_assignment(user_id);
create index ix_user_role_assignment_workspace_id on user_role_assignment(workspace_id);

create table group_role_assignment (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    group_id varchar(64) not null,
    role_id varchar(64) not null,
    assigned_at timestamp not null
);

create index ix_group_role_assignment_group_id on group_role_assignment(group_id);

create table service_account (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    workspace_id varchar(64) not null,
    name varchar(255) not null,
    description text,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null
);

create index ix_service_account_workspace_id on service_account(workspace_id);

create table api_client (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    workspace_id varchar(64) not null,
    name varchar(255) not null,
    client_key_hash varchar(255) not null,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null
);

create index ix_api_client_workspace_id on api_client(workspace_id);

-- ============================================================
-- 3. MEDIA & RENDERING
-- ============================================================
-- artifact, artifact_replica, artifact_pin, artifact_relation,
-- render_job_status_history, timeline_snapshot, timeline_revision,
-- effect_pack, effect_pack_effect, client_export_session,
-- media_asset_metadata
--
-- GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1): `artifact` is the canonical
-- Artifact record. ArtifactId (id) is stable logical identity; content_digest
-- is immutable integrity; physical locations live in artifact_replica (0..N);
-- storage_uri is NOT identity and no longer lives on the canonical record.
-- render_job_id/project_id are nullable provenance trace (render-origin).

create table artifact (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(64),
    render_job_id varchar(64),
    content_digest varchar(128) not null,
    byte_length bigint not null,
    media_type varchar(64) not null,
    artifact_kind varchar(32) not null,
    state varchar(32) not null,
    schema_version int not null default 1,
    created_at timestamp not null,
    tombstoned_at timestamp,
    constraint uq_artifact_tenant_digest unique (tenant_id, content_digest, byte_length),
    constraint uq_artifact_tenant_id unique (tenant_id, id)
);

create index ix_artifact_render_job_id on artifact(render_job_id);
create index ix_artifact_project_id on artifact(project_id);
create index ix_artifact_state on artifact(state);
create index ix_artifact_content_digest on artifact(content_digest);

create table artifact_replica (
    artifact_id varchar(64) not null,
    replica_id varchar(64) not null,
    provider_id varchar(64) not null,
    storage_object_id varchar(64) not null,
    region varchar(64),
    role varchar(32) not null default 'PRIMARY',
    state varchar(32) not null default 'ACTIVE',
    created_at timestamp not null,
    constraint pk_artifact_replica primary key (artifact_id, replica_id),
    constraint fk_artifact_replica_artifact foreign key (artifact_id) references artifact(id) on delete restrict
);

create index ix_artifact_replica_storage on artifact_replica(storage_object_id);

create table artifact_pin (
    pin_id varchar(64) primary key,
    tenant_id varchar(64) not null,
    revision_id varchar(64) not null,
    project_id varchar(64) not null,
    artifact_id varchar(64) not null,
    content_digest varchar(128) not null,
    pinned_at timestamp not null,
    constraint fk_artifact_pin_artifact
        foreign key (tenant_id, artifact_id) references artifact(tenant_id, id) on delete restrict,
    constraint uq_artifact_pin_revision unique (tenant_id, project_id, revision_id, artifact_id)
);

create index ix_artifact_pin_artifact on artifact_pin(artifact_id);
create index ix_artifact_pin_revision on artifact_pin(revision_id);

create table artifact_relation (
    id varchar(64) primary key,
    source_artifact_id varchar(64) not null,
    target_artifact_id varchar(64) not null,
    relation_type varchar(64) not null,
    created_at timestamp not null,
    constraint fk_artifact_relation_source foreign key (source_artifact_id) references artifact(id) on delete restrict,
    constraint fk_artifact_relation_target foreign key (target_artifact_id) references artifact(id) on delete restrict
);

create index ix_artifact_relation_source on artifact_relation(source_artifact_id);
create index ix_artifact_relation_target on artifact_relation(target_artifact_id);

create table render_job_status_history (
    id varchar(64) primary key,
    job_id varchar(64) not null,
    from_status varchar(30),
    to_status varchar(30) not null,
    reason varchar(255),
    error_code varchar(100),
    occurred_at timestamp not null default now()
);

create index ix_rjsh_job_id on render_job_status_history(job_id);

create table timeline_snapshot (
    id varchar(64) primary key,
    project_id varchar(64) not null,
    tenant_id varchar(64) not null,
    payload_json text not null,
    schema_version varchar(32) not null default 'timeline-1.0',
    created_at timestamp not null default CURRENT_TIMESTAMP,
    content_hash varchar(64),
    revision_number int,
    semantic_revision_id varchar(64),
    constraint uq_timeline_snapshot_owner_id unique (tenant_id, project_id, id),
    constraint uq_timeline_snapshot_semantic_revision unique (tenant_id, project_id, semantic_revision_id),
    constraint fk_timeline_snapshot_project
        foreign key (tenant_id, project_id) references project(tenant_id, id) on delete restrict
);

create index idx_timeline_snapshot_project on timeline_snapshot(project_id);

create table timeline_revision (
    id varchar(64) primary key,
    project_id varchar(64) not null,
    tenant_id varchar(64) not null,
    parent_revision_id varchar(64),
    revision_number int not null,
    snapshot_id varchar(64) not null,
    internal_revision int not null default 0,
    content_hash varchar(64) not null,
    schema_version varchar(32) not null default 'timeline-1.0',
    source varchar(32) not null,
    author_user_id varchar(64),
    edit_session_id varchar(64),
    message varchar(512),
    change_summary_json text,
    created_at timestamp not null,
    patch_ops_json text,
    labels_json varchar(512),
    is_merge boolean not null default false,
    merge_parent_revision_ids text,
    merge_base_revision_id varchar(64),
    constraint uq_timeline_revision_owner_id unique (tenant_id, project_id, id),
    constraint uq_timeline_revision_project_id unique (project_id, id),
    constraint fk_timeline_revision_project
        foreign key (tenant_id, project_id) references project(tenant_id, id) on delete restrict,
    constraint fk_timeline_revision_parent
        foreign key (tenant_id, project_id, parent_revision_id)
        references timeline_revision(tenant_id, project_id, id) on delete restrict
        deferrable initially deferred,
    constraint fk_timeline_revision_snapshot
        foreign key (tenant_id, project_id, snapshot_id)
        references timeline_snapshot(tenant_id, project_id, id) on delete restrict
);

alter table timeline_snapshot
    add constraint fk_timeline_snapshot_semantic_revision
        foreign key (tenant_id, project_id, semantic_revision_id)
        references timeline_revision(tenant_id, project_id, id) on delete restrict;

create unique index ux_timeline_revision_project_num on timeline_revision(project_id, revision_number);
create index ix_timeline_revision_project_created on timeline_revision(project_id, created_at desc);
create index ix_timeline_revision_parent on timeline_revision(parent_revision_id);
create index ix_timeline_revision_snapshot on timeline_revision(snapshot_id);
create index ix_timeline_revision_edit_session on timeline_revision(project_id, edit_session_id, created_at desc);
create index ix_timeline_revision_project_source on timeline_revision(project_id, source);
create index ix_timeline_revision_is_merge on timeline_revision(is_merge);

-- GCR5/GCR6 (C5): artifact_pin references timeline_revision — declared here
-- because artifact_pin precedes timeline_revision in the script (forward FK).
alter table artifact_pin
    add constraint fk_artifact_pin_revision
        foreign key (tenant_id, project_id, revision_id)
        references timeline_revision(tenant_id, project_id, id) on delete restrict,
    add constraint fk_artifact_pin_project
        foreign key (tenant_id, project_id) references project(tenant_id, id) on delete restrict;

create table timeline_review (
    id varchar(64) primary key,
    project_id varchar(64) not null,
    tenant_id varchar(64),
    revision_id varchar(64) not null,
    target_type varchar(32) not null default 'TIMELINE',
    author_user_id varchar(64),
    title varchar(256) not null,
    description text,
    status varchar(32) not null default 'OPEN',
    created_at timestamp not null,
    updated_at timestamp
);

create index ix_timeline_review_project on timeline_review(project_id);
create index ix_timeline_review_revision on timeline_review(revision_id);
create index ix_timeline_review_status on timeline_review(status);

create table review_thread (
    id varchar(64) primary key,
    review_id varchar(64) not null,
    entity_ref varchar(256),
    diff_id varchar(64),
    status varchar(32) not null default 'OPEN',
    created_at timestamp not null,
    constraint fk_review_thread_review foreign key (review_id) references timeline_review(id)
);

create index ix_review_thread_review on review_thread(review_id);

create table timeline_comment (
    id varchar(64) primary key,
    review_id varchar(64) not null,
    thread_id varchar(64),
    revision_id varchar(64),
    entity_ref varchar(256),
    author_user_id varchar(64),
    content text not null,
    created_at timestamp not null,
    constraint fk_timeline_comment_review foreign key (review_id) references timeline_review(id)
);

create index ix_timeline_comment_review on timeline_comment(review_id);
create index ix_timeline_comment_thread on timeline_comment(thread_id);

create table review_decision (
    id varchar(64) primary key,
    review_id varchar(64) not null,
    reviewer_user_id varchar(64) not null,
    decision varchar(32) not null,
    created_at timestamp not null,
    constraint fk_review_decision_review foreign key (review_id) references timeline_review(id)
);

create index ix_review_decision_review on review_decision(review_id);

create table effect_pack (
    id varchar(64) primary key,
    pack_id varchar(128) not null,
    version varchar(32) not null,
    name varchar(255) not null,
    description varchar(1024),
    author varchar(128),
    compatibility varchar(32) default '2.0',
    allowed_tiers text,
    tenant_id varchar(64) not null default '',
    builtin boolean not null default false,
    created_at timestamp not null default CURRENT_TIMESTAMP,
    updated_at timestamp not null default CURRENT_TIMESTAMP
);

create unique index uq_effect_pack_identity on effect_pack(pack_id, version, tenant_id);
create index idx_effect_pack_tenant on effect_pack(tenant_id);

create table effect_pack_effect (
    id varchar(64) primary key,
    pack_row_id varchar(64) not null,
    effect_key varchar(128) not null,
    display_name varchar(255) not null,
    category varchar(64) not null,
    description varchar(1024),
    parameter_schema text,
    default_values text,
    provider_mappings text,
    allowed_tiers text,
    sort_order int not null default 0,
    taxonomy_category varchar(50),
    is_effect boolean default true,
    constraint fk_effect_pack_effect_pack foreign key (pack_row_id) references effect_pack(id)
);

create unique index uq_effect_pack_effect_key on effect_pack_effect(pack_row_id, effect_key);
create index idx_effect_pack_effect_taxonomy_category on effect_pack_effect(taxonomy_category);

create table client_export_session (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    workspace_id varchar(64),
    project_id varchar(64) not null,
    user_id varchar(128),
    timeline_snapshot_id varchar(64),
    export_type varchar(32) not null default 'CLIENT_BROWSER',
    preset varchar(64),
    status varchar(32) not null default 'CREATED',
    progress int not null default 0,
    resolution varchar(32) default '1280x720',
    fps int default 30,
    format varchar(16) default 'webm',
    watermark_enabled boolean default true,
    video_bitrate int,
    audio_bitrate int,
    max_duration_sec int,
    output_uri varchar(512),
    artifact_id varchar(64),
    download_path varchar(512),
    error_code varchar(64),
    error_message varchar(1024),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    expires_at timestamp
);

create index ix_cex_tenant on client_export_session(tenant_id);
create index ix_cex_project on client_export_session(project_id);
create index ix_cex_status on client_export_session(status);
create index ix_cex_tenant_proj on client_export_session(tenant_id, project_id);

-- MCMV2-C: media_asset_metadata (double fps/duration authority) REMOVED.
-- Raw probe observations now live in media_probe_observation (opaque);
-- canonical structural truth lives in media_stream / media_asset (exact).

-- ============================================================
-- 4. COMMERCE & BILLING
-- ============================================================
-- commerce_product, commerce_price, provider_product_mapping,
-- checkout_session, purchase_order, payment_transaction/payment_command,
-- provider_webhook_receipt/payment_refund/payment_outbox, subscription_contract, subscription_plan,
-- billing_invoice, billing_ledger_entry, credit_wallet,
-- credit_transaction, invoice_line_item, pricing_rule, usage_meter,
-- observed_runtime_usage, billable_usage, rated_usage_record, custom_pricing_rule,
-- discount_policy, commerce_cart, commerce_cart_line

create table commerce_product (
    id varchar(64) primary key,
    product_code varchar(128) not null unique,
    product_line_type varchar(64) not null,
    display_name varchar(255) not null,
    lifecycle_state varchar(16) not null check (lifecycle_state in ('DRAFT','ACTIVE','RETIRED')),
    version bigint not null check (version > 0),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table commercial_offering (
    id varchar(64) primary key,
    product_id varchar(64) not null,
    offering_key varchar(128) not null,
    offering_version bigint not null check (offering_version > 0),
    lifecycle_state varchar(16) not null check (lifecycle_state in ('DRAFT','ACTIVE','RETIRED')),
    row_version bigint not null check (row_version > 0),
    purchase_mode varchar(32) not null,
    tenant_scope varchar(64) not null,
    market_scope varchar(32) not null,
    valid_from timestamp with time zone not null,
    valid_to timestamp with time zone,
    entitlement_bundle_ref varchar(128),
    entitlement_bundle_version bigint,
    quota_profile_ref varchar(128),
    quota_profile_version bigint,
    subscription_plan_ref varchar(128),
    subscription_plan_version bigint,
    commercial_price_ref varchar(128) not null,
    commercial_price_version bigint not null check (commercial_price_version > 0),
    amount_minor_snapshot bigint not null check (amount_minor_snapshot >= 0),
    currency_code_snapshot varchar(3) not null check (currency_code_snapshot ~ '^[A-Z]{3}$'),
    credit_quantity_minor bigint,
    seat_quantity integer,
    seat_feature_key varchar(128),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    foreign key (product_id) references commerce_product(id),
    unique (product_id, offering_key, offering_version),
    check (valid_to is null or valid_to > valid_from)
);

create index ix_commercial_offering_resolution on commercial_offering(
    tenant_scope, market_scope, lifecycle_state, valid_from, valid_to);

create table product_catalog_command (
    id varchar(64) primary key,
    catalog_scope varchar(64) not null,
    actor_tenant_id varchar(64) not null,
    actor_principal_type varchar(32) not null,
    actor_principal_id varchar(128) not null,
    command_type varchar(32) not null check (command_type in ('CREATE','LIFECYCLE','PRODUCT_LIFECYCLE','MAP_PROVIDER')),
    idempotency_key varchar(255) not null,
    payload_fingerprint varchar(64) not null,
    product_id varchar(64),
    offering_id varchar(64),
    provider_mapping_id varchar(64),
    result_state varchar(32) not null,
    result_version bigint not null,
    source varchar(128) not null,
    reason varchar(512) not null,
    trace_id varchar(128) not null,
    created_at timestamp with time zone not null,
    unique (catalog_scope, idempotency_key)
);

create table provider_product_mapping (
    id varchar(64) primary key,
    provider_code varchar(64) not null,
    external_product_ref varchar(255) not null,
    external_price_ref varchar(255),
    product_id varchar(64) not null,
    offering_id varchar(64) not null,
    offering_version bigint not null,
    version bigint not null check (version > 0),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    foreign key (product_id) references commerce_product(id),
    foreign key (offering_id) references commercial_offering(id),
    unique (provider_code, external_product_ref),
    unique (provider_code, product_id, offering_id, offering_version)
);

create index ix_provider_product_mapping_product_id on provider_product_mapping(product_id);

create table checkout_session (
    id varchar(64) primary key,
    checkout_session_code varchar(128) not null unique,
    product_id varchar(64) not null,
    canonical_product_code varchar(128) not null,
    offering_id varchar(64) not null,
    offering_version bigint not null,
    commercial_price_ref varchar(128) not null,
    commercial_price_version bigint not null,
    amount_minor_snapshot bigint not null,
    currency_code_snapshot varchar(3) not null,
    provider_code varchar(64),
    session_status varchar(32) not null,
    success_url text,
    cancel_url text,
    created_at timestamp not null,
    tenant_id varchar(64) not null,
    user_id varchar(128),
    cart_id varchar(64),
    foreign key (product_id) references commerce_product(id),
    foreign key (offering_id) references commercial_offering(id)
);

create index ix_checkout_session_product_id on checkout_session(product_id);
create index ix_checkout_session_tenant on checkout_session(tenant_id);

create table purchase_order (
    id varchar(64) primary key,
    checkout_session_id varchar(64),
    canonical_product_code varchar(128) not null,
    product_id varchar(64) not null,
    offering_id varchar(64) not null,
    offering_version bigint not null,
    commercial_price_ref varchar(128) not null,
    commercial_price_version bigint not null,
    amount_minor_snapshot bigint not null,
    currency_code_snapshot varchar(3) not null,
    order_status varchar(32) not null,
    total_amount_minor bigint,
    currency_code varchar(8),
    created_at timestamp not null,
    tenant_id varchar(64) not null,
    foreign key (product_id) references commerce_product(id),
    foreign key (offering_id) references commercial_offering(id)
);

create index ix_purchase_order_checkout_session_id on purchase_order(checkout_session_id);
create index ix_purchase_order_tenant on purchase_order(tenant_id);

create table payment_transaction (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    principal_type varchar(32) not null,
    principal_id varchar(128) not null,
    workspace_id varchar(64) not null default '',
    organization_id varchar(64) not null default '',
    order_id varchar(64),
    checkout_session_id varchar(64) not null,
    provider_code varchar(64) not null,
    provider_reference varchar(255),
    redirect_url text,
    amount_minor bigint not null,
    currency_code varchar(3) not null,
    transaction_state varchar(32) not null,
    provider_event_cursor bigint,
    captured_amount_minor bigint not null default 0,
    refunded_amount_minor bigint not null default 0,
    version bigint not null,
    provider_call_claimed_at timestamp with time zone,
    source varchar(128) not null,
    trace_id varchar(128) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    unique (tenant_id, checkout_session_id),
    unique (provider_code, provider_reference),
    check (amount_minor > 0),
    check (currency_code ~ '^[A-Z]{3}$'),
    check (transaction_state in ('INITIATED', 'PENDING', 'AUTHORIZED', 'SETTLED',
        'FAILED', 'CANCELLED', 'PARTIALLY_REFUNDED', 'REFUNDED')),
    check (captured_amount_minor >= 0),
    check (refunded_amount_minor >= 0),
    check (refunded_amount_minor <= captured_amount_minor)
);

create index ix_payment_transaction_principal on payment_transaction(
    tenant_id, principal_type, principal_id, workspace_id, organization_id);
create index ix_payment_transaction_order on payment_transaction(tenant_id, order_id);

create table payment_command (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    principal_type varchar(32) not null,
    principal_id varchar(128) not null,
    workspace_id varchar(64) not null default '',
    organization_id varchar(64) not null default '',
    idempotency_key varchar(255) not null,
    command_type varchar(32) not null,
    transaction_id varchar(64) not null,
    payload_fingerprint varchar(64) not null,
    result_fingerprint varchar(255),
    result_state varchar(32) not null,
    result_version bigint not null,
    source varchar(128) not null,
    reason varchar(512) not null,
    trace_id varchar(128) not null,
    created_at timestamp with time zone not null,
    unique (tenant_id, idempotency_key)
);

create index ix_payment_command_transaction on payment_command(tenant_id, transaction_id);

create table provider_webhook_receipt (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    provider_code varchar(64) not null,
    event_id varchar(255) not null,
    payload_sha256 varchar(64) not null,
    event_type varchar(128) not null,
    event_cursor bigint not null,
    provider_reference varchar(255) not null,
    canonical_state varchar(32) not null,
    processing_outcome varchar(32) not null,
    transaction_id varchar(64) not null,
    occurred_at timestamp with time zone not null,
    received_at timestamp with time zone not null,
    unique (provider_code, event_id),
    check (processing_outcome in ('PROJECTED', 'IGNORED_STALE', 'IGNORED_TERMINAL'))
);

create index ix_provider_webhook_receipt_transaction on provider_webhook_receipt(transaction_id);

create table payment_outbox (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    event_type varchar(64) not null,
    aggregate_id varchar(64) not null,
    dedupe_key varchar(255) not null,
    provider_code varchar(64) not null,
    provider_reference varchar(255) not null,
    checkout_session_id varchar(64) not null,
    trace_id varchar(128) not null,
    created_at timestamp with time zone not null,
    dispatched_at timestamp with time zone,
    unique (tenant_id, event_type, dedupe_key),
    check (event_type = 'PAYMENT_SETTLED')
);

create index ix_payment_outbox_pending on payment_outbox(created_at) where dispatched_at is null;

create table payment_refund (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    transaction_id varchar(64) not null,
    provider_refund_reference varchar(255),
    original_capture_reference varchar(255) not null,
    amount_minor bigint not null,
    currency_code varchar(3) not null,
    refund_state varchar(32) not null,
    provider_call_claimed_at timestamp with time zone,
    idempotency_key varchar(255) not null,
    payload_fingerprint varchar(64) not null,
    source varchar(128) not null,
    reason varchar(512) not null,
    trace_id varchar(128) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    unique (tenant_id, idempotency_key),
    unique (provider_refund_reference),
    check (amount_minor > 0),
    check (currency_code ~ '^[A-Z]{3}$'),
    check (refund_state in ('REQUESTED', 'PROVIDER_CALLING', 'SUCCEEDED', 'FAILED'))
);

create index ix_payment_refund_transaction on payment_refund(tenant_id, transaction_id, refund_state);

create table subscription_contract (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    subject_type varchar(32) not null,
    subject_id varchar(128) not null,
    canonical_product_code varchar(128) not null,
    provider_code varchar(64),
    external_contract_ref varchar(255),
    contract_state varchar(32) not null,
    contract_role varchar(32) not null default 'BASE',
    period_start_at timestamp,
    period_end_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null default now(),
    plan_key varchar(128),
    included_quota_used text,
    version bigint not null default 0,
    check (contract_state in ('ACTIVE', 'CANCELLED')),
    check (contract_role in ('BASE', 'ADD_ON', 'SEAT_PACK'))
);

create index ix_subscription_contract_subject on subscription_contract(subject_type, subject_id);
create index ix_subscription_contract_tenant on subscription_contract(tenant_id);
create unique index uq_subscription_contract_active_base
    on subscription_contract(tenant_id, subject_type, subject_id)
    where contract_state = 'ACTIVE' and contract_role = 'BASE';

create table subscription_command (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    principal_type varchar(32) not null,
    principal_id varchar(128) not null,
    idempotency_key varchar(255) not null,
    command_type varchar(32) not null,
    payload_fingerprint text not null,
    result_snapshot text,
    actor varchar(128) not null,
    reason varchar(512) not null,
    trace_id varchar(128) not null,
    created_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    constraint uq_subscription_command_tenant_key unique (tenant_id, idempotency_key),
    check (command_type in ('CREATE', 'CHANGE', 'CANCEL'))
);

create table subscription_plan (
    id varchar(64) primary key,
    plan_key varchar(128) not null unique,
    name varchar(255) not null,
    description text,
    billing_interval varchar(32) not null,
    base_price_minor bigint not null,
    currency_code varchar(8) not null,
    included_quota text,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index ix_subscription_plan_key on subscription_plan(plan_key);
create index ix_subscription_plan_status on subscription_plan(status);

create table billing_invoice (
    id varchar(64) not null,
    tenant_id varchar(64) not null,
    principal_type varchar(32) not null,
    principal_id varchar(128) not null,
    contract_id varchar(64),
    provider_code varchar(64),
    external_invoice_ref varchar(255),
    invoice_status varchar(32) not null check (invoice_status in ('OPEN', 'ISSUED', 'PAID', 'VOID')),
    total_amount_minor bigint not null default 0 check (total_amount_minor >= 0),
    amount_paid_minor bigint not null default 0 check (amount_paid_minor >= 0),
    currency_code varchar(3) not null,
    version bigint not null default 1 check (version > 0),
    issued_at timestamptz,
    paid_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (tenant_id, id),
    check (amount_paid_minor <= total_amount_minor)
);

create index ix_billing_invoice_contract_id on billing_invoice(tenant_id, contract_id);

create table billing_invoice_command (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    invoice_id varchar(64) not null,
    idempotency_key varchar(255) not null,
    command_type varchar(32) not null,
    payload_fingerprint varchar(64) not null,
    result_version bigint not null,
    result_status varchar(32) not null,
    result_total_minor bigint not null,
    result_currency varchar(3) not null,
    actor varchar(128) not null,
    reason varchar(512) not null,
    trace_id varchar(128) not null,
    created_at timestamptz not null,
    unique (tenant_id, idempotency_key)
);

create table billing_ledger_entry (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    principal_type varchar(32) not null,
    principal_id varchar(128) not null,
    workspace_id varchar(64),
    entry_type varchar(32) not null,
    amount_minor bigint not null,
    currency_code varchar(3) not null,
    reference_type varchar(64) not null,
    reference_id varchar(128) not null,
    description text not null,
    idempotency_key varchar(255) not null,
    payload_fingerprint varchar(64) not null,
    created_at timestamptz not null default now(),
    unique (tenant_id, idempotency_key),
    unique (tenant_id, reference_type, reference_id, entry_type),
    check (entry_type in ('CHARGE', 'REFUND', 'ADJUSTMENT', 'CREDIT', 'DEBIT', 'DISCOUNT')),
    check (entry_type = 'ADJUSTMENT' or amount_minor >= 0)
);

create index ix_billing_ledger_tenant on billing_ledger_entry(tenant_id);
create index ix_billing_ledger_type on billing_ledger_entry(entry_type);
create index ix_billing_ledger_ref on billing_ledger_entry(reference_type, reference_id);
create index ix_billing_ledger_created on billing_ledger_entry(created_at);

create table credit_wallet (
    id varchar(64) not null,
    tenant_id varchar(64) not null,
    principal_type varchar(32) not null,
    principal_id varchar(128) not null,
    workspace_id varchar(64),
    balance_minor bigint not null default 0 check (balance_minor >= 0),
    currency_code varchar(3) not null,
    status varchar(32) not null default 'ACTIVE',
    version bigint not null default 1 check (version > 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (tenant_id, id),
    unique (tenant_id, principal_type, principal_id, workspace_id, currency_code)
);

create index ix_credit_wallet_tenant on credit_wallet(tenant_id);
create index ix_credit_wallet_status on credit_wallet(status);
create unique index uq_credit_wallet_principal on credit_wallet(
    tenant_id, principal_type, principal_id, coalesce(workspace_id, ''), currency_code);

create table credit_transaction (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    wallet_id varchar(64) not null,
    reservation_id varchar(64),
    transaction_type varchar(32) not null,
    amount_minor bigint not null,
    currency_code varchar(3) not null,
    balance_after_minor bigint not null,
    reference_type varchar(64) not null,
    reference_id varchar(128) not null,
    description text not null,
    idempotency_key varchar(255) not null,
    payload_fingerprint varchar(64) not null,
    created_at timestamptz not null default now(),
    unique (tenant_id, idempotency_key)
);

create index ix_credit_txn_wallet on credit_transaction(wallet_id);
create index ix_credit_txn_type on credit_transaction(transaction_type);
create index ix_credit_txn_created on credit_transaction(created_at);

create table credit_reservation (
    id varchar(64) not null,
    tenant_id varchar(64) not null,
    wallet_id varchar(64) not null,
    amount_minor bigint not null check (amount_minor > 0),
    currency_code varchar(3) not null,
    status varchar(32) not null check (status in ('ACTIVE', 'FINALIZED', 'RELEASED')),
    version bigint not null check (version > 0),
    reference_type varchar(64) not null,
    reference_id varchar(128) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (tenant_id, id),
    foreign key (tenant_id, wallet_id) references credit_wallet(tenant_id, id)
);

create index ix_credit_reservation_wallet on credit_reservation(tenant_id, wallet_id, status);

create table credit_wallet_command (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    wallet_id varchar(64) not null,
    idempotency_key varchar(255) not null,
    command_type varchar(32) not null,
    payload_fingerprint varchar(64) not null,
    result_balance_minor bigint not null,
    result_currency varchar(3) not null,
    result_wallet_version bigint not null,
    result_reservation_id varchar(64),
    result_reservation_status varchar(32),
    actor varchar(128) not null,
    reason varchar(512) not null,
    trace_id varchar(128) not null,
    created_at timestamptz not null,
    unique (tenant_id, idempotency_key)
);

create table invoice_line_item (
    id varchar(64) not null,
    tenant_id varchar(64) not null,
    invoice_id varchar(64) not null,
    rated_usage_id varchar(64),
    line_type varchar(32) not null,
    description text not null,
    quantity_base_units bigint not null check (quantity_base_units >= 0),
    unit_price_minor bigint not null,
    amount_minor bigint not null,
    currency_code varchar(3) not null,
    period_start timestamptz,
    period_end timestamptz,
    created_at timestamptz not null default now(),
    primary key (tenant_id, id),
    foreign key (tenant_id, invoice_id) references billing_invoice(tenant_id, id),
    unique (tenant_id, rated_usage_id)
);

create index ix_invoice_line_item_invoice on invoice_line_item(tenant_id, invoice_id);

create table pricing_rule (
    id varchar(64) not null,
    tenant_id varchar(64) not null,
    rule_key varchar(128) not null,
    rule_version bigint not null check (rule_version > 0),
    name varchar(255) not null,
    description text,
    pricing_model varchar(32) not null,
    meter_key varchar(128) not null,
    unit_price_minor bigint not null check (unit_price_minor >= 0),
    currency_code varchar(3) not null,
    tier_config text not null,
    status varchar(32) not null default 'ACTIVE',
    effective_from timestamptz not null,
    effective_to timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (tenant_id, id),
    unique (tenant_id, rule_key, rule_version),
    check (effective_to is null or effective_to > effective_from)
);

create index ix_pricing_rule_key on pricing_rule(tenant_id, rule_key, rule_version);
create index ix_pricing_rule_model on pricing_rule(pricing_model);
create index ix_pricing_rule_status on pricing_rule(status);

create table usage_meter (
    id varchar(64) primary key,
    meter_key varchar(128) not null unique,
    name varchar(255) not null,
    description text,
    unit varchar(64) not null,
    aggregation_type varchar(32) not null,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null default now()
);

create index ix_usage_meter_key on usage_meter(meter_key);

-- H5 I4: neutral immutable operational observations. This table owns no commercial truth.
create table observed_runtime_usage (
    observed_usage_id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(64),
    principal_type varchar(32) not null,
    principal_id varchar(128) not null,
    operation_ref varchar(128) not null,
    attempt_ref varchar(128) not null,
    execution_ref varchar(128),
    provider_ref varchar(128) not null,
    capability varchar(128) not null,
    dimension varchar(64) not null,
    quantity_base_units bigint not null check (quantity_base_units >= 0),
    quantity_unit varchar(32) not null,
    operation_outcome varchar(32) not null check (
        operation_outcome in ('SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT')),
    occurred_at timestamptz not null,
    observed_at timestamptz not null,
    recorded_at timestamptz not null,
    provenance varchar(32) not null check (provenance in ('REPORTED', 'ESTIMATED', 'DERIVED')),
    source varchar(128) not null,
    source_reference varchar(255) not null,
    trace_id varchar(128) not null,
    idempotency_key varchar(255) not null,
    unique (tenant_id, idempotency_key),
    unique (tenant_id, observed_usage_id)
);

create index ix_observed_runtime_usage_tenant on observed_runtime_usage(tenant_id, recorded_at);
create index ix_observed_runtime_usage_operation on observed_runtime_usage(
    tenant_id, operation_ref, attempt_ref);
create index ix_observed_runtime_usage_provider on observed_runtime_usage(provider_ref, occurred_at);
create index ix_observed_runtime_usage_provenance on observed_runtime_usage(provenance, source);

-- H5 I4: Billing-owned normalized usage. Only this type may enter commercial rating.
create table billable_usage (
    billable_usage_id varchar(64) primary key,
    tenant_id varchar(64) not null,
    principal_type varchar(32) not null,
    principal_id varchar(128) not null,
    observed_usage_id varchar(64) not null,
    observed_dimension varchar(64) not null,
    observed_quantity_base_units bigint not null check (observed_quantity_base_units >= 0),
    observed_quantity_unit varchar(32) not null,
    billable_meter varchar(128) not null,
    billable_dimension varchar(64) not null,
    billable_quantity_base_units bigint not null check (billable_quantity_base_units >= 0),
    billable_quantity_unit varchar(32) not null,
    metering_rule_id varchar(128) not null,
    metering_rule_version varchar(64) not null,
    transformation_kind varchar(64) not null check (
        transformation_kind in ('IDENTITY', 'SCALE', 'ROUND_UP_INCREMENT', 'EXCLUDE')),
    transformation_details text not null,
    source_observation_timestamp timestamptz not null,
    metered_at timestamptz not null,
    idempotency_key varchar(255) not null,
    trace_id varchar(128) not null,
    provenance_reference varchar(512) not null,
    foreign key (tenant_id, observed_usage_id)
        references observed_runtime_usage(tenant_id, observed_usage_id),
    unique (tenant_id, idempotency_key),
    unique (tenant_id, observed_usage_id, metering_rule_id, metering_rule_version)
);

create index ix_billable_usage_tenant_meter on billable_usage(tenant_id, billable_meter, metered_at);
create index ix_billable_usage_observation on billable_usage(tenant_id, observed_usage_id);
create index ix_billable_usage_rule on billable_usage(metering_rule_id, metering_rule_version);
create index ix_billable_usage_provenance on billable_usage(trace_id, source_observation_timestamp);

create or replace function reject_usage_fact_mutation()
returns trigger as $$
begin
    raise exception 'usage facts are immutable and append-only';
end;
$$ language plpgsql;

create trigger observed_runtime_usage_immutable
before update or delete on observed_runtime_usage
for each row execute function reject_usage_fact_mutation();

create trigger billable_usage_immutable
before update or delete on billable_usage
for each row execute function reject_usage_fact_mutation();

create table provider_cost_observation (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(64),
    actor_type varchar(32),
    actor_ref varchar(128),
    operation_ref varchar(128),
    execution_ref varchar(128),
    provider_ref varchar(128),
    capability varchar(128),
    amount_minor bigint not null,
    currency_code varchar(8) not null,
    cost_type varchar(32) not null,
    source varchar(128),
    observed_at timestamp not null,
    usage_record_id varchar(64),
    idempotency_key varchar(255) unique,
    created_at timestamp not null default now()
);
create index ix_provider_cost_observation_tenant on provider_cost_observation(tenant_id);
create index ix_provider_cost_observation_operation on provider_cost_observation(operation_ref);

create table rated_usage_record (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    billable_usage_id varchar(64) not null references billable_usage(billable_usage_id),
    pricing_rule_id varchar(64) not null,
    pricing_rule_version bigint not null check (pricing_rule_version > 0),
    quantity_base_units bigint not null check (quantity_base_units >= 0),
    rated_amount_minor bigint not null,
    currency_code varchar(3) not null,
    rating_details text not null,
    rated_at timestamptz not null,
    trace_id varchar(128) not null,
    idempotency_key varchar(255) not null,
    payload_fingerprint varchar(64) not null,
    unique (tenant_id, idempotency_key),
    unique (tenant_id, billable_usage_id, pricing_rule_id, pricing_rule_version)
);

create index ix_rated_usage_record_usage on rated_usage_record(tenant_id, billable_usage_id);
create index ix_rated_usage_record_rule on rated_usage_record(tenant_id, pricing_rule_id, pricing_rule_version);

create table custom_pricing_rule (
    id varchar(64) not null,
    tenant_id varchar(64) not null,
    workspace_id varchar(64),
    meter_key varchar(128) not null,
    rule_version bigint not null check (rule_version > 0),
    override_price_minor bigint,
    currency_code varchar(3) not null,
    discount_numerator bigint,
    discount_denominator bigint,
    effective_from timestamptz not null,
    effective_to timestamptz,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    primary key (tenant_id, id),
    unique (tenant_id, workspace_id, meter_key, rule_version),
    check (override_price_minor is null or override_price_minor >= 0),
    check (discount_numerator is not null and discount_numerator >= 0),
    check (discount_denominator is not null and discount_denominator > 0),
    check (discount_numerator <= discount_denominator),
    check (effective_to is null or effective_to > effective_from)
);

create index ix_custom_pricing_tenant on custom_pricing_rule(tenant_id);
create index ix_custom_pricing_meter on custom_pricing_rule(meter_key);
create unique index uq_custom_pricing_scope on custom_pricing_rule(
    tenant_id, coalesce(workspace_id, ''), meter_key, rule_version);

create table discount_policy (
    id varchar(64) not null,
    tenant_id varchar(64) not null,
    policy_key varchar(128) not null,
    rule_version bigint not null check (rule_version > 0),
    meter_key varchar(128) not null,
    currency_code varchar(3) not null,
    name varchar(255) not null,
    description text,
    discount_type varchar(32) not null,
    discount_numerator bigint not null check (discount_numerator >= 0),
    discount_denominator bigint not null check (discount_denominator > 0),
    flat_amount_minor bigint not null default 0 check (flat_amount_minor >= 0),
    conditions text,
    status varchar(32) not null default 'ACTIVE',
    effective_from timestamptz not null,
    effective_to timestamptz,
    created_at timestamptz not null default now(),
    primary key (tenant_id, id),
    unique (tenant_id, policy_key, rule_version),
    check (discount_numerator <= discount_denominator),
    check (effective_to is null or effective_to > effective_from)
);

create index ix_discount_policy_key on discount_policy(tenant_id, policy_key, rule_version);
create index ix_discount_policy_status on discount_policy(status);

create table commerce_cart (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    user_id varchar(128),
    created_at timestamp not null,
    updated_at timestamp not null
);

create index ix_commerce_cart_tenant on commerce_cart(tenant_id);

create table commerce_cart_line (
    id varchar(64) primary key,
    cart_id varchar(64) not null,
    product_code varchar(128) not null,
    product_id varchar(64) not null,
    offering_id varchar(64) not null,
    offering_version bigint not null,
    commercial_price_ref varchar(128) not null,
    commercial_price_version bigint not null,
    amount_minor_snapshot bigint not null,
    currency_code_snapshot varchar(3) not null,
    quantity int not null,
    created_at timestamp not null,
    foreign key (product_id) references commerce_product(id),
    foreign key (offering_id) references commercial_offering(id),
    unique(cart_id, product_code)
);

create index ix_commerce_cart_line_cart on commerce_cart_line(cart_id);

-- ============================================================
-- 5. ENTITLEMENT & QUOTA
-- ============================================================
-- feature_definition, feature_bundle, feature_bundle_item,
-- entitlement_grant, entitlement_override, entitlement_bundle,
-- quota_profile, quota_usage, workspace_entitlement_pool,
-- workspace_member_entitlement_grant, workspace_quota_allocation,
-- tenant_entitlement_tier

create table feature_definition (
    id varchar(64) primary key,
    feature_code varchar(128) not null unique,
    description varchar(255),
    created_at timestamp not null
);

create table feature_bundle (
    id varchar(64) primary key,
    bundle_code varchar(128) not null unique,
    description varchar(255),
    created_at timestamp not null
);

create table feature_bundle_item (
    id varchar(64) primary key,
    bundle_id varchar(64) not null,
    feature_id varchar(64) not null,
    created_at timestamp not null
);

create index ix_feature_bundle_item_bundle_id on feature_bundle_item(bundle_id);
create index ix_feature_bundle_item_feature_id on feature_bundle_item(feature_id);

create table entitlement_grant (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    subject_type varchar(32) not null,
    subject_id varchar(128) not null,
    bundle_code varchar(128) not null,
    quota_profile_code varchar(128),
    source_type varchar(32) not null,
    source_ref varchar(255) not null,
    grant_status varchar(32) not null,
    effective_at timestamp not null,
    expires_at timestamp,
    version bigint not null default 0,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    check (grant_status in ('ACTIVE', 'REVOKED')),
    constraint uq_entitlement_grant_logical_source unique
        (tenant_id, subject_type, subject_id, bundle_code, source_type, source_ref)
);

create index ix_entitlement_grant_subject on entitlement_grant(tenant_id, subject_type, subject_id);

create table entitlement_command_audit (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    principal_type varchar(32) not null,
    principal_id varchar(128) not null,
    idempotency_key varchar(255) not null,
    command_type varchar(32) not null,
    payload_fingerprint text not null,
    result_snapshot text,
    actor varchar(128) not null,
    reason varchar(512) not null,
    trace_id varchar(128) not null,
    created_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    constraint uq_entitlement_command_tenant_key unique (tenant_id, idempotency_key),
    check (command_type in (
        'GRANT', 'REVOKE', 'EXTEND',
        'WORKSPACE_GRANT', 'WORKSPACE_REVOKE', 'WORKSPACE_EXTEND'))
);

create table entitlement_override (
    id varchar(64) primary key,
    subject_type varchar(32) not null,
    subject_id varchar(128) not null,
    override_kind varchar(64) not null,
    override_payload text not null,
    effective_at timestamp not null,
    expires_at timestamp,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp,
    updated_at timestamp
);

create index ix_entitlement_override_subject on entitlement_override(subject_type, subject_id);

create table entitlement_bundle (
    id varchar(64) primary key,
    bundle_key varchar(128) not null unique,
    name varchar(255) not null,
    description text,
    status varchar(32) not null default 'ACTIVE',
    allowed_providers json,
    allowed_presets json,
    gpu_allowed boolean not null default false,
    remote_worker_allowed boolean not null default false,
    custom_fonts_allowed boolean not null default false,
    max_subtitle_tracks int not null default 2,
    max_concurrent_jobs int not null default 1,
    monthly_render_minutes bigint not null default 60,
    storage_limit_bytes bigint not null default 1073741824,
    watermark_required boolean not null default true,
    priority_queue_allowed boolean not null default false,
    beta_effects_allowed boolean not null default false,
    prompt_execution_limit bigint not null default 100,
    extension_execution_allowed boolean not null default false,
    api_access_allowed boolean not null default false,
    mcp_access_allowed boolean not null default false,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index ix_entitlement_bundle_bundle_key on entitlement_bundle(bundle_key);
create index ix_entitlement_bundle_status on entitlement_bundle(status);

create table quota_profile (
    id varchar(64) primary key,
    profile_key varchar(128) not null unique,
    name varchar(255) not null,
    description text,
    monthly_render_minutes bigint not null default 60,
    daily_render_jobs int not null default 5,
    concurrent_render_jobs int not null default 1,
    storage_bytes bigint not null default 1073741824,
    gpu_minutes bigint not null default 0,
    remote_worker_jobs int not null default 0,
    prompt_executions bigint not null default 100,
    extension_executions bigint not null default 0,
    api_calls_per_minute int not null default 60,
    mcp_calls_per_minute int not null default 30,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index ix_quota_profile_profile_key on quota_profile(profile_key);

create table quota_usage (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    principal_type varchar(32) not null,
    principal_id varchar(128) not null,
    workspace_scope varchar(64) not null default '',
    organization_scope varchar(64) not null default '',
    quota_key varchar(128) not null,
    period_start timestamp with time zone not null,
    period_end timestamp with time zone not null,
    usage_value bigint not null default 0 check (usage_value >= 0),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_quota_usage_logical_period unique (
        tenant_id, principal_type, principal_id, workspace_scope,
        organization_scope, quota_key, period_start, period_end)
);

create index ix_quota_usage_tenant_id on quota_usage(tenant_id);
create index ix_quota_usage_principal_period on quota_usage(
    tenant_id, principal_type, principal_id, quota_key, period_start, period_end);

create table quota_usage_operation (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    principal_type varchar(32) not null,
    principal_id varchar(128) not null,
    workspace_scope varchar(64) not null default '',
    organization_scope varchar(64) not null default '',
    quota_key varchar(128) not null,
    period_start timestamp with time zone not null,
    period_end timestamp with time zone not null,
    signed_delta bigint not null,
    limit_value bigint not null check (limit_value >= 0),
    idempotency_key varchar(255) not null,
    operation_kind varchar(32) not null check (
        operation_kind in ('CONSUMPTION', 'ADJUSTMENT', 'REVERSAL', 'RECONCILIATION')),
    outcome varchar(32) not null check (outcome in ('PENDING', 'APPLIED', 'REJECTED')),
    usage_before bigint,
    usage_after bigint,
    rejection_reason varchar(64),
    trace_id varchar(128) not null,
    reason varchar(512) not null,
    occurred_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    constraint uq_quota_usage_operation_idempotency unique (
        tenant_id, principal_type, principal_id, workspace_scope,
        organization_scope, idempotency_key)
);

create index ix_quota_usage_operation_period on quota_usage_operation(
    tenant_id, principal_type, principal_id, quota_key, period_start, period_end);

create table workspace_entitlement_pool (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    feature_key varchar(128) not null,
    total_quota bigint not null default 0,
    used_quota bigint not null default 0,
    period varchar(32) not null default 'MONTHLY',
    created_at timestamp not null,
    updated_at timestamp not null
);

create index ix_workspace_entitlement_pool_workspace_id on workspace_entitlement_pool(workspace_id);
create index ix_workspace_entitlement_pool_feature_key on workspace_entitlement_pool(feature_key);

create table workspace_member_entitlement_grant (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    workspace_id varchar(64) not null,
    principal_type varchar(32) not null,
    member_id varchar(128) not null,
    feature_key varchar(128) not null,
    quota_amount bigint not null default 0,
    source_type varchar(32) not null,
    source_ref varchar(255) not null,
    starts_at timestamp not null,
    expires_at timestamp,
    status varchar(32) not null default 'ACTIVE',
    version bigint not null default 0,
    granted_by varchar(128) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    check (status in ('ACTIVE', 'REVOKED')),
    constraint uq_workspace_member_grant_logical_source unique
        (tenant_id, workspace_id, principal_type, member_id, feature_key, source_type, source_ref)
);

create index ix_workspace_member_grant_workspace_id on workspace_member_entitlement_grant(tenant_id, workspace_id);
create index ix_workspace_member_grant_member_id on workspace_member_entitlement_grant(tenant_id, principal_type, member_id);
create index ix_workspace_member_grant_status on workspace_member_entitlement_grant(status);

create table workspace_quota_allocation (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    member_id varchar(64) not null,
    quota_profile_key varchar(128) not null,
    allocated_amount bigint not null default 0,
    used_amount bigint not null default 0,
    period varchar(32) not null default 'MONTHLY',
    created_at timestamp not null,
    updated_at timestamp not null
);

create index ix_workspace_quota_alloc_workspace_id on workspace_quota_allocation(workspace_id);
create index ix_workspace_quota_alloc_member_id on workspace_quota_allocation(member_id);

create table tenant_entitlement_tier (
    tenant_id varchar(64) not null primary key,
    tier varchar(32) not null default 'FREE',
    updated_at timestamp not null default now()
);

create index ix_tenant_entitlement_tier_tier on tenant_entitlement_tier(tier);

-- ============================================================
-- 6. PLATFORM CAPABILITIES
-- ============================================================
-- prompt_template, prompt_template_version, prompt_execution_run,
-- prompt_evaluation_result, extension_definition, extension_invocation,
-- extension_routing_rule, extension_resource_limit,
-- extension_rollback_point, extension_audit_event, sandbox_execution_job,
-- problematic_data_record, quarantined_render_jobs,
-- quarantined_prompt_executions, quarantined_provider_workers,
-- problematic_data_rule_config, nlq_report_definition,
-- nlq_query_history, nlq_report_execution

create table prompt_template (
    template_id varchar(64) primary key,
    name varchar(255) not null,
    description text,
    category varchar(128),
    tags text,
    owner varchar(128),
    status varchar(32) not null default 'DRAFT',
    schema_version varchar(32) not null default '1.0.0',
    current_prompt_version varchar(32),
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index idx_prompt_template_status on prompt_template(status);

create table prompt_template_version (
    version_id varchar(64) primary key,
    template_id varchar(64) not null references prompt_template(template_id),
    prompt_version varchar(32) not null,
    template_body text not null,
    variable_schema_json text,
    changelog text,
    created_by varchar(128),
    created_at timestamp not null default now(),
    checksum varchar(64),
    previous_version varchar(32),
    deprecated boolean not null default false,
    unique(template_id, prompt_version)
);

create index idx_prompt_version_template on prompt_template_version(template_id);

create table prompt_execution_run (
    execution_id varchar(64) primary key,
    template_id varchar(64) not null references prompt_template(template_id),
    prompt_version varchar(32) not null,
    tenant_id varchar(64) not null,
    user_id varchar(128) not null,
    model_provider varchar(64),
    model_name varchar(64),
    rendered_prompt_hash varchar(64),
    redacted_prompt_preview varchar(512),
    input_variables_redacted_json text,
    output_summary text,
    status varchar(32) not null default 'PENDING',
    risk_level varchar(32) not null default 'LOW',
    token_estimate int not null default 0,
    cost_estimate double precision not null default 0,
    started_at timestamp not null default now(),
    finished_at timestamp,
    error_code varchar(64),
    error_details_json text,
    related_prompt_file varchar(256),
    related_manifest_entry varchar(256)
);

create index idx_prompt_execution_template on prompt_execution_run(template_id);
create index idx_prompt_execution_tenant on prompt_execution_run(tenant_id);
create index idx_prompt_execution_status on prompt_execution_run(status);

create table prompt_evaluation_result (
    evaluation_id varchar(64) primary key,
    execution_id varchar(64) not null references prompt_execution_run(execution_id),
    template_id varchar(64) not null,
    evaluator_user_id varchar(128) not null,
    acceptance_criteria_met boolean not null default false,
    documentation_updated boolean not null default false,
    manifest_updated boolean not null default false,
    tests_pass boolean not null default false,
    has_high_risk_changes boolean not null default false,
    has_human_review_items boolean not null default false,
    has_scope_creep boolean not null default false,
    has_false_claims boolean not null default false,
    overall_verdict varchar(32) not null,
    evaluated_at timestamp not null default now()
);

create table extension_definition (
    id varchar(64) primary key,
    extension_code varchar(128) not null,
    extension_type varchar(64) not null,
    language varchar(32),
    runtime varchar(64),
    version varchar(64) not null,
    artifact_uri text,
    status varchar(32) not null,
    timeout_ms bigint,
    config_schema text,
    created_at timestamp not null,
    trust_level varchar(32) not null default 'SEMI_TRUSTED',
    sandboxed boolean not null default true,
    max_concurrency int not null default 4,
    max_memory_mb int not null default 256,
    max_cpu_percent int not null default 50,
    max_queue_size int not null default 100,
    max_input_bytes bigint not null default 10485760,
    max_output_bytes bigint not null default 4194304,
    requires_review boolean not null default false,
    review_status varchar(32) default 'APPROVED',
    unique(extension_code, version)
);

create table extension_invocation (
    id varchar(64) primary key,
    extension_code varchar(128) not null,
    extension_version varchar(64) not null,
    caller_module varchar(128) not null,
    input_summary text,
    output_summary text,
    exit_status varchar(32),
    duration_ms bigint,
    created_at timestamp not null,
    trace_id varchar(128),
    trust_level varchar(32),
    input_bytes bigint,
    output_bytes bigint,
    cpu_time_ms bigint,
    memory_peak_mb bigint,
    routing_rule_id varchar(64)
);

create index ix_extension_invocation_extension_code on extension_invocation(extension_code);
create index ix_extension_invocation_created_at on extension_invocation(created_at);
create index ix_ext_invocation_trace on extension_invocation(trace_id);

create table extension_routing_rule (
    id varchar(64) primary key,
    rule_name varchar(255) not null,
    extension_code varchar(128) not null,
    source_version varchar(64),
    target_version varchar(64) not null,
    tenant_id varchar(64),
    user_id varchar(128),
    scene varchar(128),
    priority int not null default 0,
    traffic_percent int not null default 0,
    enabled boolean not null default true,
    created_at timestamp not null default now(),
    created_by varchar(128),
    updated_at timestamp,
    unique(extension_code, source_version, target_version, tenant_id, user_id, scene)
);

create index ix_ext_routing_extension_code on extension_routing_rule(extension_code);
create index ix_ext_routing_tenant on extension_routing_rule(tenant_id);
create index ix_ext_routing_enabled on extension_routing_rule(enabled);
create index ix_ext_routing_priority on extension_routing_rule(priority);

create table extension_resource_limit (
    id varchar(64) primary key,
    extension_code varchar(128) not null,
    tenant_id varchar(64),
    limit_type varchar(64) not null,
    max_value bigint not null,
    current_value bigint not null default 0,
    window_seconds int not null default 60,
    created_at timestamp not null default now(),
    updated_at timestamp,
    unique(extension_code, tenant_id, limit_type)
);

create index ix_ext_res_limit_code on extension_resource_limit(extension_code);
create index ix_ext_res_limit_tenant on extension_resource_limit(tenant_id);

create table extension_rollback_point (
    id varchar(64) primary key,
    extension_code varchar(128) not null,
    version varchar(64) not null,
    artifact_uri text,
    config_snapshot text,
    routing_rule_ids text,
    created_at timestamp not null default now(),
    created_by varchar(128),
    is_active boolean not null default true
);

create index ix_ext_rollback_code on extension_rollback_point(extension_code);
create index ix_ext_rollback_active on extension_rollback_point(is_active);

create table extension_audit_event (
    id varchar(64) primary key,
    extension_code varchar(128) not null,
    extension_version varchar(64),
    event_type varchar(64) not null,
    actor varchar(128) not null,
    tenant_id varchar(64),
    user_id varchar(128),
    trace_id varchar(128),
    trust_level varchar(32),
    details text,
    severity varchar(32) not null default 'INFO',
    created_at timestamp not null default now()
);

create index ix_ext_audit_code on extension_audit_event(extension_code);
create index ix_ext_audit_type on extension_audit_event(event_type);
create index ix_ext_audit_tenant on extension_audit_event(tenant_id);
create index ix_ext_audit_trace on extension_audit_event(trace_id);
create index ix_ext_audit_created on extension_audit_event(created_at);

create table sandbox_execution_job (
    id varchar(64) primary key,
    extension_code varchar(128),
    language varchar(32) not null,
    script_hash varchar(64),
    status varchar(32) not null default 'PENDING',
    trace_id varchar(128),
    tenant_id varchar(64),
    user_id varchar(128),
    timeout_ms bigint not null default 30000,
    started_at timestamp,
    finished_at timestamp,
    exit_code int,
    output_preview text,
    error_message text,
    created_at timestamp not null default now()
);

create index ix_sandbox_job_status on sandbox_execution_job(status);
create index ix_sandbox_job_trace on sandbox_execution_job(trace_id);
create index ix_sandbox_job_extension on sandbox_execution_job(extension_code);

create table problematic_data_record (
    record_id varchar(64) primary key,
    data_type varchar(64) not null,
    data_id varchar(128) not null,
    tenant_id varchar(64),
    user_id varchar(128),
    problematic_type varchar(64) not null,
    severity varchar(32) not null default 'MEDIUM',
    detection_rule varchar(64),
    description text,
    context_json text,
    source_session_id varchar(128),
    render_job_id varchar(128),
    prompt_execution_id varchar(128),
    provider_key varchar(64),
    worker_id varchar(64),
    status varchar(32) not null default 'DETECTED',
    auto_fix_applied text,
    quarantine_table varchar(128),
    requires_human_review boolean not null default false,
    human_review_notes text,
    detected_at timestamp not null default now(),
    resolved_at timestamp,
    resolved_by varchar(128)
);

create index idx_problematic_data_tenant on problematic_data_record(tenant_id);
create index idx_problematic_data_status on problematic_data_record(status);
create index idx_problematic_data_type on problematic_data_record(problematic_type);
create index idx_problematic_data_severity on problematic_data_record(severity);
create index idx_problematic_data_render_job on problematic_data_record(render_job_id);
create index idx_problematic_data_prompt_exec on problematic_data_record(prompt_execution_id);
create index idx_problematic_data_detected_at on problematic_data_record(detected_at);
create index idx_problematic_data_human_review on problematic_data_record(requires_human_review, status);

create table quarantined_render_jobs (
    quarantine_id varchar(64) primary key,
    original_job_id varchar(128) not null,
    tenant_id varchar(64),
    quarantine_reason varchar(64) not null,
    detection_rule varchar(64),
    original_data_json text,
    status varchar(32) not null default 'QUARANTINED',
    quarantined_at timestamp not null default now(),
    resolved_at timestamp,
    resolved_by varchar(128),
    resolution_notes text
);

create table quarantined_prompt_executions (
    quarantine_id varchar(64) primary key,
    original_execution_id varchar(128) not null,
    tenant_id varchar(64),
    quarantine_reason varchar(64) not null,
    detection_rule varchar(64),
    original_data_json text,
    status varchar(32) not null default 'QUARANTINED',
    quarantined_at timestamp not null default now(),
    resolved_at timestamp,
    resolved_by varchar(128),
    resolution_notes text
);

create table quarantined_provider_workers (
    quarantine_id varchar(64) primary key,
    provider_key varchar(64),
    worker_id varchar(64),
    tenant_id varchar(64),
    quarantine_reason varchar(64) not null,
    detection_rule varchar(64),
    original_data_json text,
    status varchar(32) not null default 'QUARANTINED',
    quarantined_at timestamp not null default now(),
    resolved_at timestamp,
    resolved_by varchar(128),
    resolution_notes text
);

create table problematic_data_rule_config (
    rule_id varchar(64) primary key,
    rule_name varchar(255) not null,
    data_type varchar(64) not null,
    default_severity varchar(32) not null default 'MEDIUM',
    description text,
    detection_query text,
    auto_fixable boolean not null default false,
    auto_fix_action varchar(255),
    enabled boolean not null default true,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

insert into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled)
values ('RJB-001', 'Missing RenderJob Output', 'MISSING_FIELD', 'HIGH', 'RenderJob completed but has no output artifact', false, '', true);
insert into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled)
values ('RJB-002', 'Stuck RenderJob', 'INVALID_STATE_TRANSITION', 'MEDIUM', 'RenderJob stuck in non-terminal state for too long', true, 'MARK_STALE_AND_RETRY', true);
insert into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled)
values ('RJB-003', 'Duplicate RenderJob', 'DUPLICATE_ENTRY', 'LOW', 'Multiple render jobs with same project+profile+timeline hash', true, 'MARK_DUPLICATE', true);
insert into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled)
values ('PMT-001', 'Prompt Sensitive Data Leak', 'MISSING_FIELD', 'CRITICAL', 'Sensitive prompt variable found in execution record', false, '', true);
insert into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled)
values ('PMT-002', 'Prompt Output Mismatch', 'OUTPUT_MISMATCH', 'HIGH', 'Prompt execution output does not match expected format', false, '', true);
insert into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled)
values ('PRV-001', 'Provider Error Spike', 'ERROR_RATE_SPIKE', 'HIGH', 'Provider error rate exceeds threshold in time window', false, '', true);
insert into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled)
values ('WRK-001', 'Worker Stale Heartbeat', 'PERFORMANCE_ANOMALY', 'MEDIUM', 'Remote worker has not sent heartbeat within expected interval', true, 'MARK_WORKER_OFFLINE', true);
insert into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled)
values ('SLA-001', 'SLA Breach', 'SLA_BREACH', 'CRITICAL', 'Render job exceeded SLA time limit', false, '', true);
insert into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled)
values ('CST-001', 'Cost Anomaly', 'COST_ANOMALY', 'HIGH', 'Render job cost significantly exceeds estimated cost', false, '', true);

create table nlq_report_definition (
    report_id varchar(64) not null primary key,
    tenant_id varchar(64),
    workspace_id varchar(64),
    name varchar(255) not null,
    description text,
    widgets_json text,
    query_definitions_json text,
    created_by varchar(128),
    visibility varchar(32) not null default 'PRIVATE',
    schedule_json text,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    archived boolean not null default false
);

create index ix_nlq_report_tenant on nlq_report_definition(tenant_id);
create index ix_nlq_report_workspace on nlq_report_definition(workspace_id);

create table nlq_query_history (
    query_id varchar(64) not null primary key,
    user_id varchar(128) not null,
    tenant_id varchar(64),
    workspace_id varchar(64),
    question_redacted text,
    sql_hash varchar(64),
    datasets_json text,
    row_count int not null default 0,
    duration_ms bigint not null default 0,
    risk_level varchar(32),
    status varchar(32) not null,
    error_code varchar(64),
    created_at timestamp not null default now()
);

create index ix_nlq_history_tenant on nlq_query_history(tenant_id);
create index ix_nlq_history_user on nlq_query_history(user_id);

create table nlq_report_execution (
    execution_id varchar(64) not null primary key,
    report_id varchar(64) not null,
    status varchar(32) not null,
    row_count int not null default 0,
    duration_ms bigint not null default 0,
    error_code varchar(64),
    created_at timestamp not null default now()
);

create index ix_nlq_report_exec_report on nlq_report_execution(report_id);

-- ============================================================
-- 7. GOVERNANCE & COMPLIANCE
-- ============================================================
-- feature_flag_definition, feature_flag_targeting_rule,
-- frontend_route_definition, navigation_policy,
-- notification_event_definition, notification_channel_binding,
-- notification_subscription, notification_preference,
-- notification_delivery_record, notification_user_inbox

create table feature_flag_definition (
    id varchar(64) primary key,
    flag_key varchar(128) not null unique,
    name varchar(255) not null,
    description text,
    flag_type varchar(32) not null default 'BOOLEAN',
    enabled boolean not null default false,
    default_value_json text,
    variants_json text,
    tags_json text,
    owner varchar(128),
    archived boolean not null default false,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create table feature_flag_targeting_rule (
    id varchar(64) primary key,
    flag_key varchar(128) not null,
    rule_id varchar(64),
    tenant_id varchar(64),
    workspace_id varchar(64),
    user_id varchar(64),
    role varchar(64),
    tier varchar(64),
    percentage double precision,
    priority int not null default 0,
    enabled boolean not null default true,
    rule_json text not null,
    created_at timestamp not null default now()
);

create index ix_feature_flag_targeting_flag on feature_flag_targeting_rule(flag_key);

create table frontend_route_definition (
    id varchar(64) not null primary key,
    route_key varchar(128) not null unique,
    path varchar(256) not null,
    component_key varchar(128) not null,
    title varchar(256) not null,
    description text,
    menu_group varchar(128),
    icon varchar(64),
    sort_order int not null default 0,
    parent_route_key varchar(128),
    required_permissions text,
    required_roles text,
    required_entitlements text,
    required_tier varchar(64),
    required_features text,
    supported_sources text,
    visible boolean not null default true,
    enabled boolean not null default true,
    hidden_reason varchar(512),
    disabled_reason varchar(512),
    upgrade_options text,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index ix_route_def_menu_group on frontend_route_definition(menu_group);
create index ix_route_def_visible on frontend_route_definition(visible);
create index ix_route_def_enabled on frontend_route_definition(enabled);
create index ix_route_def_parent on frontend_route_definition(parent_route_key);

create table navigation_policy (
    id varchar(64) not null primary key,
    policy_key varchar(128) not null unique,
    route_key varchar(128) not null,
    policy_type varchar(32) not null,
    condition_expr text not null,
    effect varchar(16) not null,
    reason_code varchar(128) not null,
    reason_message text not null,
    upgrade_options text,
    priority int not null default 0,
    enabled boolean not null default true,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    constraint fk_nav_policy_route foreign key (route_key)
        references frontend_route_definition(route_key)
        on delete cascade
);

create index ix_nav_policy_route on navigation_policy(route_key);
create index ix_nav_policy_priority on navigation_policy(priority);

create table notification_event_definition (
    id varchar(64) primary key,
    event_key varchar(100) not null unique,
    name varchar(200) not null,
    description varchar(500),
    category varchar(50) not null,
    severity varchar(20) not null,
    visibility varchar(30) not null,
    user_configurable boolean not null default false,
    critical boolean not null default false,
    default_enabled boolean not null default true,
    supported_channels text,
    required_permissions text,
    required_entitlements text,
    feature_flag_key varchar(100),
    novu_workflow_id varchar(100),
    local_template_key varchar(100),
    archived boolean not null default false,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table notification_channel_binding (
    id varchar(64) primary key,
    tenant_id varchar(64),
    workspace_id varchar(64),
    user_id varchar(64) not null,
    channel_type varchar(32) not null,
    destination_masked varchar(255),
    destination_encrypted text,
    verified boolean not null default false,
    verification_status varchar(32) not null default 'PENDING',
    enabled boolean not null default true,
    failure_count int not null default 0,
    last_failure_at timestamp,
    last_verified_at timestamp with time zone,
    provider varchar(64),
    disabled_reason text,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index ix_notification_channel_binding_user on notification_channel_binding(user_id);

create table notification_subscription (
    id varchar(64) primary key,
    tenant_id varchar(64),
    workspace_id varchar(64),
    user_id varchar(64) not null,
    event_key varchar(100) not null,
    enabled boolean not null default true,
    channels text,
    frequency varchar(30) not null default 'IMMEDIATE',
    filters text,
    quiet_hours_start varchar(10),
    quiet_hours_end varchar(10),
    quiet_hours_timezone varchar(50),
    created_at timestamp not null,
    updated_at timestamp not null
);

create index ix_notification_subscription_user on notification_subscription(user_id);

create table notification_preference (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    user_id varchar(64) not null,
    global_enabled boolean not null default true,
    channel_enabled text not null default '{}',
    event_enabled text not null default '{}',
    quiet_hours_start varchar(10),
    quiet_hours_end varchar(10),
    quiet_hours_timezone varchar(50),
    digest_mode varchar(30) not null default 'IMMEDIATE',
    critical_override boolean not null default true,
    created_at timestamp not null,
    updated_at timestamp not null,
    unique(tenant_id, user_id),
    check (digest_mode in ('IMMEDIATE', 'HOURLY', 'DAILY'))
);

create index ix_notification_preference_user on notification_preference(user_id);

create table notification_delivery_record (
    id varchar(64) primary key,
    event_key varchar(100) not null,
    tenant_id varchar(64),
    user_id varchar(64),
    channel_type varchar(32) not null,
    status varchar(32) not null,
    attempts int not null default 0,
    payload_redacted text,
    provider_message_id varchar(128),
    error_code varchar(64),
    sent_at timestamp,
    failed_at timestamp,
    created_at timestamp not null
);

create index ix_notification_delivery_record_user on notification_delivery_record(user_id);

create table notification_user_inbox (
    id varchar(64) primary key,
    tenant_id varchar(64),
    workspace_id varchar(64),
    user_id varchar(64) not null,
    event_key varchar(100),
    type varchar(32) not null default 'INFO',
    title varchar(255),
    message text,
    read boolean not null default false,
    link varchar(512),
    actor_id varchar(64),
    resource_type varchar(64),
    resource_id varchar(64),
    created_at timestamp not null,
    read_at timestamp
);

create index ix_notification_user_inbox_user on notification_user_inbox(user_id);

-- ============================================================
-- 8. DELIVERY & PUBLISHING
-- ============================================================
-- delivery_destination, delivery_policy, delivery_job,
-- social_connected_platform, social_post, social_post_analytics

create table delivery_destination (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    user_id varchar(64),
    name varchar(255) not null,
    protocol varchar(32) not null,
    config_json text,
    credential_json text,
    enabled boolean default true,
    verified_at timestamp,
    created_at timestamp not null,
    credential_ref varchar(512)
);

create index ix_delivery_destination_tenant on delivery_destination(tenant_id);
create index ix_delivery_destination_credential_ref on delivery_destination(credential_ref);

create table delivery_policy (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(64),
    destination_id varchar(64) not null,
    artifact_selector varchar(32) not null default 'FINAL_ONLY',
    path_template varchar(512) not null,
    trigger_mode varchar(16) not null default 'AUTO',
    enabled boolean default true,
    created_at timestamp not null
);

create index ix_delivery_policy_tenant_project on delivery_policy(tenant_id, project_id);

create table delivery_job (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(64) not null,
    render_job_id varchar(64) not null,
    destination_id varchar(64) not null,
    status varchar(32) not null,
    source_uri varchar(1024) not null,
    remote_path varchar(1024),
    remote_uri varchar(1024),
    bytes_transferred bigint,
    attempt_count int not null default 0,
    error_code varchar(64),
    error_message varchar(2048),
    created_at timestamp not null,
    completed_at timestamp
);

create index ix_delivery_job_render on delivery_job(render_job_id);
create index ix_delivery_job_status on delivery_job(status);

create table social_connected_platform (
    id varchar(36) primary key,
    tenant_id varchar(36) not null,
    user_id varchar(36) not null,
    platform_type varchar(32) not null,
    platform_user_id varchar(256),
    platform_username varchar(256),
    access_token_encrypted text,
    refresh_token_encrypted text,
    token_expires_at timestamp,
    status varchar(16) default 'ACTIVE',
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index ix_social_connected_platform_tenant_user on social_connected_platform(tenant_id, user_id);

create table social_post (
    id varchar(36) primary key,
    tenant_id varchar(36) not null,
    user_id varchar(36) not null,
    content_text text,
    media_urls varchar(4000),
    platform_type varchar(32) not null,
    status varchar(16) default 'DRAFT',
    platform_post_id varchar(256),
    platform_post_url varchar(512),
    scheduled_at timestamp,
    published_at timestamp,
    failed_at timestamp,
    error_code varchar(64),
    error_message text,
    retry_count int default 0,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index ix_social_post_tenant_user on social_post(tenant_id, user_id);
create index ix_social_post_status on social_post(status);

create table social_post_analytics (
    id varchar(36) primary key,
    post_id varchar(36) not null,
    platform_type varchar(32) not null,
    impressions int default 0,
    reach int default 0,
    likes int default 0,
    comments int default 0,
    shares int default 0,
    clicks int default 0,
    fetched_at timestamp,
    created_at timestamp not null default now()
);

create index ix_social_post_analytics_post on social_post_analytics(post_id);

-- ============================================================
-- 9. ANALYTICS & USER
-- ============================================================
-- user_behavior_event, user_profile, user_segment, user_habits,
-- shared_resource_grant

create table user_behavior_event (
    event_id varchar(64) not null primary key,
    tenant_id varchar(64) not null,
    user_id varchar(128) not null,
    event_type varchar(64) not null,
    action varchar(128),
    resource_type varchar(64),
    resource_id varchar(128),
    metadata_json text,
    occurred_at timestamp not null default now()
);

create index ix_user_behavior_tenant on user_behavior_event(tenant_id);
create index ix_user_behavior_user on user_behavior_event(user_id);
create index ix_user_behavior_occurred on user_behavior_event(occurred_at);

create table user_profile (
    profile_id varchar(64) not null primary key,
    tenant_id varchar(64) not null,
    user_id varchar(128) not null,
    display_name varchar(255),
    preferred_languages_json text,
    feature_usage_counts_json text,
    action_counts_json text,
    total_sessions int not null default 0,
    total_actions int not null default 0,
    first_seen_at timestamp,
    last_active_at timestamp,
    updated_at timestamp not null default now(),
    unique(tenant_id, user_id)
);

create index ix_user_profile_tenant on user_profile(tenant_id);

create table user_segment (
    segment_id varchar(64) not null primary key,
    tenant_id varchar(64) not null,
    name varchar(255) not null,
    description text,
    criteria_json text,
    user_ids_json text,
    user_count int not null default 0,
    computed_at timestamp not null default now()
);

create index ix_user_segment_tenant on user_segment(tenant_id);

create table user_habits (
    tenant_id varchar(64) not null,
    user_id varchar(128) not null,
    habits_json text not null,
    computed_at timestamp not null default now(),
    primary key (tenant_id, user_id)
);

create table shared_resource_grant (
    grant_id varchar(64) not null primary key,
    tenant_id varchar(64) not null,
    resource_type varchar(32) not null,
    resource_id varchar(128) not null,
    resource_name varchar(255),
    resource_description text,
    resource_status varchar(32),
    shared_by_user_id varchar(128),
    shared_with_user_id varchar(128) not null,
    permission varchar(32) not null default 'READ',
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null default now(),
    expires_at timestamp
);

create index ix_shared_resource_grant_recipient on shared_resource_grant(tenant_id, shared_with_user_id);
create index ix_shared_resource_grant_resource on shared_resource_grant(resource_type, resource_id);

-- ============================================================
-- 10. AI & LITELLM
-- ============================================================
-- tenant_litellm_virtual_key

create table tenant_litellm_virtual_key (
    tenant_id varchar(64) primary key,
    virtual_key varchar(512),
    key_alias varchar(128),
    enabled boolean not null default true,
    created_at timestamp not null,
    updated_at timestamp not null,
    vault_ref varchar(512)
);

create index ix_tenant_litellm_key_enabled on tenant_litellm_virtual_key(enabled);
create index ix_tenant_litellm_vault_ref on tenant_litellm_virtual_key(vault_ref);

-- ============================================================
-- RENDER FARM: Worker Registry + Job Lease
-- ============================================================

create table render_worker (
    id varchar(64) primary key,
    worker_id varchar(128) not null unique,
    worker_type varchar(32) not null default 'RENDER',
    status varchar(32) not null default 'STARTING',
    version varchar(64),
    image_tag varchar(128),
    hostname varchar(256),
    zone varchar(64),
    provider_ids text,
    capabilities_json text,
    max_concurrent_jobs int not null default 1,
    active_job_count int not null default 0,
    cpu_cores int,
    memory_mb int,
    gpu_count int not null default 0,
    gpu_type varchar(64),
    disk_free_mb bigint,
    last_heartbeat_at timestamp not null,
    registered_at timestamp not null,
    expires_at timestamp,
    metadata_json text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index ix_render_worker_status on render_worker(status);
create index ix_render_worker_heartbeat on render_worker(last_heartbeat_at);
create index ix_render_worker_type on render_worker(worker_type);

create table render_job_lease (
    id varchar(64) primary key,
    lease_id varchar(128) not null unique,
    job_id varchar(64) not null,
    tenant_id varchar(64) not null,
    worker_id varchar(128) not null,
    provider_id varchar(64),
    status varchar(32) not null default 'CLAIMED',
    lease_version bigint not null default 1,
    claimed_at timestamp not null,
    lease_until timestamp not null,
    renewed_at timestamp,
    released_at timestamp,
    attempt int not null default 1,
    max_attempts int not null default 3,
    heartbeat_token_hash varchar(128),
    failure_reason text,
    failure_error_code varchar(64),
    created_by_scheduler varchar(64),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index ix_lease_job_id on render_job_lease(job_id);
create index ix_lease_worker_id on render_job_lease(worker_id);
create index ix_lease_status on render_job_lease(status);
create index ix_lease_until on render_job_lease(lease_until);

-- ============================================================
-- ASSET TABLE
-- ============================================================

create table media_asset (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(128) not null,
    storage_key text not null,
    media_type varchar(32) not null,
    filename varchar(256),
    size_bytes bigint,
    checksum varchar(128),
    media_version varchar(64),
    owner_id varchar(128),
    entity_ref text,
    classification varchar(64),
    license varchar(128),
    retention_policy varchar(128),
    security_level varchar(64),
    contains_pii boolean not null default false,
    ai_generated boolean not null default false,
    created_at timestamp not null,
    updated_at timestamp,
    publish_status varchar(32) not null default 'DRAFT'
);

create index ix_media_asset_tenant_project on media_asset(tenant_id, project_id);
create index ix_media_asset_tenant_created on media_asset(tenant_id, created_at desc);
create index ix_media_asset_classification on media_asset(classification);
create index ix_media_asset_ai_generated on media_asset(ai_generated);

-- MCMV2-C: typed MediaAsset <-> Artifact linkage (MEDIA_ASSET_ARTIFACT_RELATIONSHIP_V1)
create table media_asset_artifact (
    media_asset_id varchar(64) not null,
    artifact_id varchar(64) not null,
    relationship varchar(16) not null,
    created_at timestamp not null default current_timestamp,
    constraint pk_maa primary key (media_asset_id, artifact_id, relationship),
    constraint fk_maa_media_asset foreign key (media_asset_id) references media_asset(id) on delete restrict,
    constraint fk_maa_artifact foreign key (artifact_id) references artifact(id) on delete restrict
);

create index ix_maa_artifact on media_asset_artifact(artifact_id);

-- MCMV2-C: canonical source stream structural model (exact time/rate)
create table media_stream (
    id varchar(64) primary key,
    media_asset_id varchar(64) not null,
    stream_index int not null,
    stream_kind varchar(16) not null,
    codec varchar(64),
    timebase_num bigint not null,
    timebase_den bigint not null,
    rate_num bigint,
    rate_den bigint,
    is_vfr boolean not null default false,
    width int,
    height int,
    pixel_format varchar(64),
    sample_rate int,
    channels int,
    channel_layout varchar(64),
    sample_format varchar(64),
    bit_depth int,
    color_primaries varchar(64),
    color_transfer varchar(64),
    color_matrix varchar(64),
    color_range varchar(64),
    hdr_mastering_display_ref varchar(128),
    hdr_content_light_ref varchar(128),
    container_stream_description varchar(128),
    constraint fk_ms_media_asset foreign key (media_asset_id) references media_asset(id) on delete restrict
);

create index ix_ms_media_asset on media_stream(media_asset_id);

-- MCMV2-C: raw probe observation (RAW_PROBE_RESULT_IS_NOT_CANONICAL_MEDIA_AUTHORITY_V1)
-- Provider-specific raw payload is opaque; canonical structural truth lives in
-- media_stream / media_asset. No double time/rate authority persists here.
create table media_probe_observation (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(64) not null,
    media_asset_id varchar(64) not null,
    provider varchar(64),
    raw_payload text,
    valid boolean not null default false,
    client_export_compatible boolean not null default false,
    normalize_required boolean not null default true,
    warnings varchar(4096),
    error_message varchar(1024),
    probed_at timestamp not null default current_timestamp,
    constraint fk_mpo_media_asset foreign key (media_asset_id) references media_asset(id) on delete cascade
);

create index ix_mpo_tenant_asset on media_probe_observation(tenant_id, media_asset_id);
create index ix_mpo_project on media_probe_observation(project_id);
create index ix_mpo_probed_at on media_probe_observation(probed_at);

create table asset_semantic_metadata (
    asset_id varchar(64) primary key,
    asset_version varchar(64),
    status varchar(32) not null default 'PENDING',
    language varchar(16),
    semantic_json text,
    created_at timestamp not null,
    updated_at timestamp,
    constraint fk_asm_asset foreign key (asset_id) references media_asset(id)
);

create index ix_asm_status on asset_semantic_metadata(status);
create index ix_asm_language on asset_semantic_metadata(language);

create table search_projection (
    asset_id varchar(64) primary key,
    tenant_id varchar(64),
    project_id varchar(64),
    filename varchar(256),
    asset_type varchar(32),
    transcript_text text,
    scene_labels text,
    objects text,
    brands text,
    people text,
    classification varchar(64),
    license varchar(128),
    publish_status varchar(32),
    search_text text,
    search_vector tsvector,
    updated_at timestamp not null,
    constraint fk_sp_asset foreign key (asset_id) references media_asset(id)
);

create index ix_sp_tenant on search_projection(tenant_id);
create index ix_sp_project on search_projection(project_id);
create index ix_sp_publish_status on search_projection(publish_status);
create index ix_sp_fts on search_projection using gin(search_vector);

create table marketplace_listing (
    id varchar(64) primary key,
    asset_id varchar(64) not null,
    tenant_id varchar(64),
    project_id varchar(64),
    listing_type varchar(32) not null,
    title varchar(256) not null,
    summary text,
    description text,
    preview_url varchar(512),
    cover_url varchar(512),
    version varchar(32) not null default '1.0',
    status varchar(32) not null default 'DRAFT',
    search_text text,
    search_vector tsvector,
    review_id varchar(64),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uq_ml_asset unique(asset_id),
    constraint fk_ml_asset foreign key (asset_id) references media_asset(id)
);

create index ix_ml_status on marketplace_listing(status);
create index ix_ml_type on marketplace_listing(listing_type);
create index ix_ml_fts on marketplace_listing using gin(search_vector);

-- ============================================================
-- RENDER JOB ENHANCEMENTS
-- ============================================================

-- Add trace_id to render_job (included in table definition above)
-- This is already part of the consolidated render_job table

-- ============================================================
-- ARTIFACT DAG TABLES
-- ============================================================

create table artifact_node (
    id varchar(128) primary key,
    job_id varchar(64) not null,
    type varchar(32) not null,
    uri text not null,
    parent_artifact_ids text,
    workflow_id varchar(128),
    run_id varchar(128),
    operator_id varchar(128),
    parameters_hash varchar(128),
    source_asset_id varchar(128),
    derived_from_asset_ids text,
    version int not null default 1,
    hash varchar(128),
    metadata text,
    created_at timestamp not null,
    constraint fk_artifact_node_job foreign key (job_id) references render_job(id)
);

create index ix_artifact_node_job_id on artifact_node(job_id);
create index ix_artifact_node_hash on artifact_node(hash);
create index ix_artifact_node_type on artifact_node(type);

create table artifact_graph (
    graph_id varchar(128) primary key,
    job_id varchar(64) not null,
    root_artifact_id varchar(128),
    version int not null default 1,
    created_at timestamp not null,
    constraint fk_artifact_graph_job foreign key (job_id) references render_job(id),
    constraint fk_artifact_graph_root foreign key (root_artifact_id) references artifact_node(id)
);

create index ix_artifact_graph_job_id on artifact_graph(job_id);

-- ============================================================
-- UNIFIED EXECUTION GRAPH TABLES
-- ============================================================

create table unified_request_graph (
    graph_id varchar(128) primary key,
    request_id varchar(128) not null,
    tenant_id varchar(64) not null,
    workspace_id varchar(64),
    job_id varchar(64),
    root_node_id varchar(128),
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null,
    completed_at timestamp,
    constraint fk_ueeg_job foreign key (job_id) references render_job(id)
);

create unique index ix_ueeg_request_id on unified_request_graph(request_id);
create index ix_ueeg_job_id on unified_request_graph(job_id);
create index ix_ueeg_tenant_id on unified_request_graph(tenant_id);
create index ix_ueeg_status on unified_request_graph(status);

create table unified_graph_node (
    node_id varchar(128) primary key,
    graph_id varchar(128) not null,
    type varchar(64) not null,
    subsystem varchar(64) not null,
    action varchar(64) not null,
    status varchar(32) not null,
    data text,
    timestamp timestamp not null,
    constraint fk_node_graph foreign key (graph_id) references unified_request_graph(graph_id)
);

create index ix_node_graph_id on unified_graph_node(graph_id);
create index ix_node_type on unified_graph_node(type);
create index ix_node_subsystem on unified_graph_node(subsystem);

create table unified_graph_edge (
    edge_id varchar(128) primary key,
    graph_id varchar(128) not null,
    source_node_id varchar(128) not null,
    target_node_id varchar(128) not null,
    edge_type varchar(32) not null,
    timestamp timestamp not null,
    constraint fk_edge_graph foreign key (graph_id) references unified_request_graph(graph_id),
    constraint fk_edge_source foreign key (source_node_id) references unified_graph_node(node_id),
    constraint fk_edge_target foreign key (target_node_id) references unified_graph_node(node_id)
);

create index ix_edge_graph_id on unified_graph_edge(graph_id);
create index ix_edge_source on unified_graph_edge(source_node_id);
create index ix_edge_target on unified_graph_edge(target_node_id);

-- ============================================================
-- SYSTEM CANONICAL TABLES
-- ============================================================

create table system_canonical_graph (
    graph_id varchar(128) primary key,
    job_id varchar(64) not null,
    tenant_id varchar(64),
    workspace_id varchar(64),
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null,
    completed_at timestamp,
    constraint fk_canonical_job foreign key (job_id) references render_job(id)
);

create unique index ix_canonical_job_id on system_canonical_graph(job_id);
create index ix_canonical_tenant_id on system_canonical_graph(tenant_id);
create index ix_canonical_status on system_canonical_graph(status);

create table system_canonical_event (
    event_id varchar(128) primary key,
    graph_id varchar(128) not null,
    event_type varchar(64) not null,
    timestamp timestamp not null,
    tenant_id varchar(64),
    workspace_id varchar(64),
    job_id varchar(64),
    source_system varchar(64) not null,
    sequence_number int not null,
    payload text,
    constraint fk_event_graph foreign key (graph_id) references system_canonical_graph(graph_id)
);

create index ix_event_graph_id on system_canonical_event(graph_id);
create index ix_event_job_id on system_canonical_event(job_id);
create index ix_event_type on system_canonical_event(event_type);
create index ix_event_source on system_canonical_event(source_system);
create index ix_event_sequence on system_canonical_event(graph_id, sequence_number);

create table system_canonical_edge (
    edge_id varchar(128) primary key,
    graph_id varchar(128) not null,
    source_event_id varchar(128) not null,
    target_event_id varchar(128) not null,
    edge_type varchar(32) not null,
    timestamp timestamp not null,
    constraint fk_canonical_edge_graph foreign key (graph_id) references system_canonical_graph(graph_id),
    constraint fk_canonical_edge_source foreign key (source_event_id) references system_canonical_event(event_id),
    constraint fk_canonical_edge_target foreign key (target_event_id) references system_canonical_event(event_id)
);

create index ix_canonical_edge_graph_id on system_canonical_edge(graph_id);
create index ix_canonical_edge_source on system_canonical_edge(source_event_id);
create index ix_canonical_edge_target on system_canonical_edge(target_event_id);

-- ============================================================
-- RENDER JOB QUEUE TABLE
-- ============================================================

create table render_job_queue (
    id bigint generated by default as identity primary key,
    job_id varchar(64) not null unique,
    tenant_id varchar(64) not null,
    status varchar(32) not null default 'QUEUED',
    priority int not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index ix_queue_status on render_job_queue(status);
create index ix_queue_priority on render_job_queue(priority desc, created_at asc);
create index ix_queue_job_id on render_job_queue(job_id);

-- ============================================================
-- PROJECT IMPORT METADATA (from V6)
-- ============================================================

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
        foreign key (project_id)
        references project(id)
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

-- ============================================================

create table product (
    product_id varchar(64) primary key,
    tenant_id varchar(64),
    project_id varchar(64),
    owner_asset_id varchar(64),
    product_type varchar(32) not null,
    representation_kind varchar(32) not null,
    producer_type varchar(32),
    producer_id varchar(64),
    source_timeline_revision_id varchar(64),
    status varchar(32) not null default 'REGISTERED',
    storage_reference_id varchar(256),
    checksum varchar(128),
    content_hash varchar(128),
    mime_type varchar(64),
    version int not null default 1,
    metadata_json text,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index ix_product_tenant on product(tenant_id);
create index ix_product_project on product(project_id);
create index ix_product_asset on product(owner_asset_id);
create index ix_product_producer on product(producer_id);
create index ix_product_status on product(status);
create index ix_product_type on product(product_type);

create table product_dependency (
    dependency_id varchar(64) primary key,
    tenant_id varchar(64),
    project_id varchar(64),
    product_id varchar(64) not null,
    depends_on_product_id varchar(64) not null,
    dependency_type varchar(32) not null,
    created_at timestamp not null,
    constraint uq_prod_dep unique(product_id, depends_on_product_id, dependency_type),
    constraint fk_dep_product foreign key (product_id) references product(product_id),
    constraint fk_dep_upstream foreign key (depends_on_product_id) references product(product_id)
);

create index ix_prod_dep_product on product_dependency(product_id);
create index ix_prod_dep_upstream on product_dependency(depends_on_product_id);
create index ix_prod_dep_type on product_dependency(dependency_type);

create table storage_reference (
    storage_reference_id varchar(64) primary key,
    provider_type varchar(32) not null default 'LOCAL',
    storage_class varchar(32) not null default 'STANDARD',
    root_path varchar(512) not null,
    relative_path varchar(512) not null,
    checksum varchar(128),
    content_hash varchar(128),
    file_size bigint not null default 0,
    mime_type varchar(64),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uq_storage_path unique(provider_type, root_path, relative_path)
);

create index ix_storage_checksum on storage_reference(checksum);
create index ix_storage_content_hash on storage_reference(content_hash);

-- V2: RenderJob lifecycle events
-- RenderJob Lifecycle Events
-- Durable event history for diagnostics and operational visibility

CREATE TABLE render_job_lifecycle_events (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    render_job_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    status_from VARCHAR(32),
    status_to VARCHAR(32),
    worker_id VARCHAR(128),
    attempt INT DEFAULT 0,
    retry_count INT DEFAULT 0,
    recovery_count INT DEFAULT 0,
    output_product_id VARCHAR(64),
    reason_code VARCHAR(64),
    reason VARCHAR(512),
    retryable BOOLEAN DEFAULT FALSE,
    next_retry_at TIMESTAMP,
    duration_ms BIGINT,
    event_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    payload_json TEXT,
    source VARCHAR(64) DEFAULT 'worker'
);

-- Index for job event history
CREATE INDEX idx_lifecycle_events_job ON render_job_lifecycle_events(render_job_id, event_time);

-- Index for project event queries
CREATE INDEX idx_lifecycle_events_project ON render_job_lifecycle_events(project_id, render_job_id, event_time);

-- Index for tenant queries
CREATE INDEX idx_lifecycle_events_tenant ON render_job_lifecycle_events(tenant_id, project_id, event_time);

-- Index for event type filtering
CREATE INDEX idx_lifecycle_events_type ON render_job_lifecycle_events(event_type, event_time);

-- Index for worker queries
CREATE INDEX idx_lifecycle_events_worker ON render_job_lifecycle_events(worker_id, event_time);

-- V3: Ingest preflight safe report records
-- Safe preflight report persistence (DEV_PREVIEW_EPHEMERAL_ONLY)
-- Runtime persistence is NOT_IMPLEMENTED. This schema is for future use only.
CREATE TABLE ingest_preflight_safe_report_records (
    -- Identity/scope
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,
    raw_media_product_id VARCHAR(255) NOT NULL,
    upload_attempt_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    lifecycle_state VARCHAR(50) NOT NULL DEFAULT 'RECORDED',

    -- Mode/access/retention
    persistence_mode VARCHAR(50) NOT NULL DEFAULT 'DEV_PREVIEW_EPHEMERAL_ONLY',
    access_scope VARCHAR(50) NOT NULL DEFAULT 'DEV_ONLY',
    retention_days INT NOT NULL DEFAULT 7,
    report_only_mode BOOLEAN NOT NULL DEFAULT true,
    fail_open BOOLEAN NOT NULL DEFAULT true,

    -- Safe report summary
    overall_decision VARCHAR(50) NOT NULL,
    warning_count INT NOT NULL DEFAULT 0,
    finding_count INT NOT NULL DEFAULT 0,
    reject_candidate_count INT NOT NULL DEFAULT 0,
    declared_mime VARCHAR(255),
    detected_mime VARCHAR(255),
    mime_mismatch BOOLEAN NOT NULL DEFAULT false,
    content_type_confidence DOUBLE PRECISION,
    duration_ms BIGINT,
    width INT,
    height INT,
    container_format VARCHAR(100),
    video_codec VARCHAR(100),
    audio_codec VARCHAR(100),
    has_video BOOLEAN NOT NULL DEFAULT false,
    has_audio BOOLEAN NOT NULL DEFAULT false,

    -- Safe detector summary
    tika_detector_success BOOLEAN NOT NULL DEFAULT false,
    ffprobe_detector_success BOOLEAN NOT NULL DEFAULT false,
    detector_warning_codes JSONB,

    -- Safe policy result
    policy_profile VARCHAR(100),
    policy_mode VARCHAR(50) NOT NULL DEFAULT 'REPORT_ONLY',
    policy_decision VARCHAR(50) NOT NULL,
    policy_finding_count INT NOT NULL DEFAULT 0,
    policy_reject_candidate_count INT NOT NULL DEFAULT 0,
    policy_user_safe_message_codes JSONB,
    policy_finding_codes JSONB,
    upload_continues BOOLEAN NOT NULL DEFAULT true,
    blocking BOOLEAN NOT NULL DEFAULT false,

    -- Lifecycle/audit
    redacted_at TIMESTAMP,
    expired_at TIMESTAMP,
    deleted_at TIMESTAMP,
    schema_version INT NOT NULL DEFAULT 1,

    -- Constraints
    CONSTRAINT chk_retention_days CHECK (retention_days BETWEEN 1 AND 7),
    CONSTRAINT chk_access_scope CHECK (access_scope = 'DEV_ONLY'),
    CONSTRAINT chk_persistence_mode CHECK (persistence_mode = 'DEV_PREVIEW_EPHEMERAL_ONLY'),
    CONSTRAINT chk_policy_decision CHECK (policy_decision <> 'REJECT'),
    CONSTRAINT chk_blocking CHECK (blocking = false),
    CONSTRAINT chk_upload_continues CHECK (upload_continues = true),
    CONSTRAINT chk_created_at CHECK (created_at IS NOT NULL),
    CONSTRAINT chk_expires_at CHECK (expires_at IS NOT NULL)
);

-- V5: Add timeline_revision_id to render_job for revision pinning
ALTER TABLE render_job ADD COLUMN timeline_revision_id VARCHAR(64);
CREATE INDEX ix_render_job_timeline_revision ON render_job(timeline_revision_id);

-- Indexes for DEV_ONLY diagnostics queries
CREATE INDEX idx_preflight_safe_tenant_created ON ingest_preflight_safe_report_records(tenant_id, project_id, created_at DESC);
CREATE INDEX idx_preflight_safe_tenant_product ON ingest_preflight_safe_report_records(tenant_id, project_id, raw_media_product_id);
CREATE INDEX idx_preflight_safe_expires ON ingest_preflight_safe_report_records(expires_at);
CREATE INDEX idx_preflight_safe_lifecycle ON ingest_preflight_safe_report_records(lifecycle_state, expires_at);
CREATE INDEX idx_preflight_safe_policy_decision ON ingest_preflight_safe_report_records(policy_decision);
CREATE INDEX idx_preflight_safe_overall_decision ON ingest_preflight_safe_report_records(overall_decision);

-- V4: Add selected_provider to render_job
ALTER TABLE render_job ADD COLUMN selected_provider VARCHAR(128);

-- V4: Add updated_at to render_job
ALTER TABLE render_job ADD COLUMN updated_at TIMESTAMPTZ;

-- =====================================================================
-- W2 V1: User Workflow Definition (USER_WORKFLOW_DEFINITION_V1_CONTRACT_V2)
-- Consolidated into canonical V1 per GREENFIELD_MIGRATION_GOVERNANCE_AUTHORITY_V1
-- (ONE_CONSOLIDATED_V1). CREATE ONLY; PK/UNIQUE only; no CHECK; no FKs
-- (application-enforced integrity); timestamp not timestamptz.
-- =====================================================================
create table user_workflow_definition (
    definition_id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(128),
    created_at timestamp not null,
    created_by varchar(128) not null
);

create table user_workflow_definition_version (
    definition_id varchar(64) not null,
    version_number int not null,
    tenant_id varchar(64) not null,
    project_id varchar(128),
    name varchar(255) not null,
    description text,
    status varchar(32) not null,
    schema_version int not null,
    optimistic_version bigint not null default 1,
    trigger_json text not null,
    parameter_json text not null,
    created_at timestamp not null,
    created_by varchar(128) not null,
    updated_at timestamp not null,
    updated_by varchar(128) not null,
    published_at timestamp,
    published_by varchar(128),
    archived_at timestamp,
    archived_by varchar(128),
    primary key (definition_id, version_number)
);

create table user_workflow_definition_node (
    definition_id varchar(64) not null,
    version_number int not null,
    node_id varchar(64) not null,
    tenant_id varchar(64) not null,
    node_type varchar(32) not null,
    name varchar(255) not null,
    config_json text not null,
    input_json text,
    output_json text,
    error_policy varchar(16) not null default 'FAIL',
    sort_order int not null default 0,
    primary key (definition_id, version_number, node_id)
);

create table user_workflow_definition_edge (
    definition_id varchar(64) not null,
    version_number int not null,
    edge_id varchar(64) not null,
    tenant_id varchar(64) not null,
    source_node_id varchar(64) not null,
    target_node_id varchar(64) not null,
    condition_ref varchar(255) not null default '',
    sort_order int not null default 0,
    primary key (definition_id, version_number, edge_id),
    unique (definition_id, version_number, source_node_id, target_node_id, condition_ref)
);

-- ── UWEV1-FV1: User Workflow Execution (product/query/audit authority) ────────
-- Minimal single-table persistence (UWE-ADR-006). Temporal history is the
-- orchestration runtime authority; this table is the product authority.
create table workflow_execution (
    execution_id varchar(64) not null,
    tenant_id varchar(64) not null,
    actor_type varchar(32) not null,
    actor_id varchar(64) not null,
    definition_id varchar(64) not null,
    definition_version int not null,
    trigger_type varchar(32) not null,
    status varchar(16) not null,
    temporal_workflow_id varchar(255) not null,
    idempotency_key varchar(255) not null,
    input_refs_json text,
    result_summary_json text,
    error_category varchar(32),
    created_at timestamptz not null,
    started_at timestamptz,
    completed_at timestamptz,
    primary key (execution_id, tenant_id)
);
create unique index ux_workflow_execution_idempotency on workflow_execution (tenant_id, idempotency_key);
create index ix_workflow_execution_tenant_status on workflow_execution (tenant_id, status, created_at desc);

-- ============================================================
-- GCR-2 SINGLE-V1 CONSOLIDATION
-- Former V2 (timeline_revision_ref, apply_command), V3 (deferrable FK),
-- V4 (timeline_revision_parent, project_revision_counter, command_domain),
-- V5 (source_visual_description_snapshot), V6 (ownership UNIQUE/FKs),
-- V7 (composite snapshot PK, immutable trigger) folded into canonical V1
-- as FINAL schema definitions. No incremental migration archaeology.
-- ============================================================

-- OWNERSHIP UNIQUES (former V6): enable composite ownership FKs.
alter table media_stream
    add constraint uq_ms_id_asset unique (id, media_asset_id);

alter table media_asset_artifact
    add constraint uq_maa_asset_artifact unique (media_asset_id, artifact_id);

-- REVISION PARENT GRAPH (former V4): composite FK target + ordered parent edges.
create table timeline_revision_parent (
    tenant_id          varchar(64) not null,
    project_id         varchar(64) not null,
    revision_id        varchar(64) not null,
    parent_revision_id varchar(64) not null,
    parent_order       int         not null,
    primary key (tenant_id, project_id, revision_id, parent_order),
    constraint ux_timeline_revision_parent_pair
        unique (tenant_id, project_id, revision_id, parent_revision_id),
    constraint ck_timeline_revision_parent_order_nonnegative
        check (parent_order >= 0),
    constraint ck_timeline_revision_parent_no_self
        check (revision_id <> parent_revision_id),
    constraint fk_timeline_revision_parent_revision
        foreign key (tenant_id, project_id, revision_id)
        references timeline_revision(tenant_id, project_id, id),
    constraint fk_timeline_revision_parent_parent
        foreign key (tenant_id, project_id, parent_revision_id)
        references timeline_revision(tenant_id, project_id, id)
        deferrable initially deferred
);

create index ix_timeline_revision_parent_child on timeline_revision_parent(revision_id);
create index ix_timeline_revision_parent_parent on timeline_revision_parent(parent_revision_id);

-- REVISION COUNTER (former V4): DB-safe per-project revision allocation.
create table project_revision_counter (
    project_id          varchar(64) not null primary key,
    next_revision_number bigint not null
);

-- OPERATION PLAN TRANSACTION MODEL (former V2/V3): per-project head/ref row with
-- database-enforced CAS; head FK DEFERRABLE INITIALLY DEFERRED so the apply
-- transaction may advance head before inserting the revision in the same tx
-- (FK remains fully active: validated at COMMIT).
create table timeline_revision_ref (
    tenant_id          varchar(64)  not null,
    project_id         varchar(64)  not null,
    ref_id             varchar(64)  not null,
    head_revision_id   varchar(64),
    version            bigint       not null default 0,
    updated_at         timestamp    not null default current_timestamp,
    primary key (tenant_id, project_id, ref_id),
    constraint fk_timeline_revision_ref_head
        foreign key (tenant_id, project_id, head_revision_id)
        references timeline_revision(tenant_id, project_id, id)
        deferrable initially deferred
);

-- APPLY COMMAND IDEMPOTENCY (former V2/V4): durable command replay authority;
-- command_domain separates OPERATION_PLAN vs REVISION_COMMAND semantic domains.
create table apply_command (
    apply_command_id     varchar(64)  not null,
    plan_digest          varchar(64)  not null,
    fingerprint          varchar(64)  not null,
    status               varchar(16)  not null,
    result_revision_id   varchar(64),
    result_content_hash  varchar(64),
    result_status        varchar(16),
    tenant_id            varchar(64)  not null,
    project_id           varchar(64)  not null,
    command_domain       varchar(32)  not null default 'OPERATION_PLAN',
    target_ref_id        varchar(64)  not null,
    expected_head_revision_id varchar(64),
    expected_result_status varchar(32) not null,
    created_at           timestamp    not null default current_timestamp,
    completed_at         timestamp,
    primary key (apply_command_id)
);

create index ix_apply_command_fingerprint on apply_command(fingerprint);

alter table apply_command
    add constraint fk_apply_command_project
        foreign key (tenant_id, project_id) references project(tenant_id, id),
    add constraint fk_apply_command_target_ref
        foreign key (tenant_id, project_id, target_ref_id)
        references timeline_revision_ref(tenant_id, project_id, ref_id)
        deferrable initially deferred,
    add constraint fk_apply_command_expected_head
        foreign key (tenant_id, project_id, expected_head_revision_id)
        references timeline_revision(tenant_id, project_id, id)
        deferrable initially deferred,
    add constraint fk_apply_command_result_revision
        foreign key (tenant_id, project_id, result_revision_id)
        references timeline_revision(tenant_id, project_id, id)
        deferrable initially deferred;

-- SOURCE VISUAL DESCRIPTION SNAPSHOT (former V5/V6/V7): durable canonical
-- Media-owned snapshot, bound to immutable source content. Final shape:
-- composite PK (media_stream_id, artifact_id) supports F2 multi-content-version
-- coexistence; ownership FKs reject cross-asset/unlinked bindings; append-only
-- immutability is enforced by PostgreSQL trigger (no semantic UPDATE).
create table source_visual_description_snapshot (
    media_stream_id varchar(64) not null,
    media_asset_id   varchar(64) not null,
    artifact_id      varchar(64) not null,
    canonical_payload text not null,
    created_at       timestamp not null default current_timestamp,
    constraint pk_svd_stream_artifact primary key (media_stream_id, artifact_id),
    constraint fk_source_visual_snapshot_stream
        foreign key (media_stream_id) references media_stream(id),
    constraint fk_svd_stream_asset
        foreign key (media_stream_id, media_asset_id)
        references media_stream (id, media_asset_id),
    constraint fk_svd_asset_artifact
        foreign key (media_asset_id, artifact_id)
        references media_asset_artifact (media_asset_id, artifact_id)
);

create or replace function trg_fn_svd_snapshot_immutable() returns trigger as $$
begin
    if new.media_stream_id is distinct from old.media_stream_id
       or new.media_asset_id is distinct from old.media_asset_id
       or new.artifact_id is distinct from old.artifact_id
       or new.canonical_payload is distinct from old.canonical_payload then
        raise exception 'SOURCE_VISUAL_SNAPSHOT_IMMUTABLE: semantic mutation of '
            'source_visual_description_snapshot is forbidden (append-only canonical fact)';
    end if;
    return new;
end;
$$ language plpgsql;

create trigger trg_svd_snapshot_immutable
    before update on source_visual_description_snapshot
    for each row execute function trg_fn_svd_snapshot_immutable();

-- ============================================================
-- 11. WORKER FABRIC EXECUTION AUTHORITY
-- ============================================================
-- Canonical PostgreSQL authority for Roadmap #22 execution-fabric registration,
-- host snapshots, placement selection, assignment, reservation, lease and lifecycle.

create table wf_worker_runtime_connection (
    worker_runtime_id varchar(128) primary key,
    current_incarnation_id varchar(128) not null,
    connected boolean not null,
    updated_at timestamptz not null
);

create table wf_physical_host_connection (
    physical_host_id varchar(128) primary key,
    current_incarnation_id varchar(128) not null,
    connected boolean not null,
    updated_at timestamptz not null
);

create table wf_host_registration (
    physical_host_id varchar(128) not null,
    physical_host_incarnation_id varchar(128) not null,
    registered_at timestamptz not null,
    valid_until timestamptz not null,
    active boolean not null,
    primary key (physical_host_id, physical_host_incarnation_id),
    check (valid_until > registered_at)
);

create unique index ux_wf_one_active_host_registration
    on wf_host_registration (physical_host_id) where active;

create table wf_host_snapshot_generation_authority (
    physical_host_id varchar(128) not null,
    physical_host_incarnation_id varchar(128) not null,
    current_generation bigint not null check (current_generation > 0),
    primary key (physical_host_id, physical_host_incarnation_id),
    foreign key (physical_host_id, physical_host_incarnation_id)
        references wf_host_registration (physical_host_id, physical_host_incarnation_id)
);

create or replace function wf_reject_snapshot_generation_regression()
returns trigger language plpgsql as $$
begin
    if new.current_generation <= old.current_generation then
        raise exception 'host resource snapshot generation must increase: old %, new %',
            old.current_generation, new.current_generation;
    end if;
    return new;
end
$$;

create trigger wf_snapshot_generation_strictly_increases
before update of current_generation on wf_host_snapshot_generation_authority
for each row execute function wf_reject_snapshot_generation_regression();

create table wf_host_resource_snapshot (
    physical_host_id varchar(128) not null,
    physical_host_incarnation_id varchar(128) not null,
    snapshot_generation bigint not null check (snapshot_generation > 0),
    snapshot_fingerprint varchar(64) not null check (length(snapshot_fingerprint) = 64),
    captured_at timestamptz not null,
    schema_version integer not null check (schema_version > 0),
    cpu_millicores bigint not null check (cpu_millicores >= 0),
    memory_bytes bigint not null check (memory_bytes >= 0),
    temporary_storage_bytes bigint not null check (temporary_storage_bytes >= 0),
    safety_headroom_cpu_millicores bigint not null check (
        safety_headroom_cpu_millicores >= 0),
    safety_headroom_memory_bytes bigint not null check (
        safety_headroom_memory_bytes >= 0),
    safety_headroom_temporary_storage_bytes bigint not null check (
        safety_headroom_temporary_storage_bytes >= 0),
    publication_transaction_id bigint not null default txid_current(),
    primary key (physical_host_id, physical_host_incarnation_id, snapshot_generation),
    foreign key (physical_host_id, physical_host_incarnation_id)
        references wf_host_registration (physical_host_id, physical_host_incarnation_id),
    check (safety_headroom_cpu_millicores <= cpu_millicores),
    check (safety_headroom_memory_bytes <= memory_bytes),
    check (safety_headroom_temporary_storage_bytes <= temporary_storage_bytes)
);

create or replace function wf_reject_published_snapshot_mutation()
returns trigger language plpgsql as $$
begin
    raise exception 'durable host resource snapshot publication is immutable';
end
$$;

create trigger wf_host_snapshot_publication_immutable
before update or delete on wf_host_resource_snapshot
for each row execute function wf_reject_published_snapshot_mutation();

create table wf_host_resource_snapshot_device (
    physical_host_id varchar(128) not null,
    physical_host_incarnation_id varchar(128) not null,
    snapshot_generation bigint not null,
    device_id varchar(128) not null,
    vram_bytes bigint not null check (vram_bytes >= 0),
    compute_units bigint not null check (compute_units >= 0),
    encoder_engines bigint not null check (encoder_engines >= 0),
    decoder_engines bigint not null check (decoder_engines >= 0),
    safety_headroom_vram_bytes bigint not null check (safety_headroom_vram_bytes >= 0),
    safety_headroom_compute_units bigint not null check (safety_headroom_compute_units >= 0),
    safety_headroom_encoder_engines bigint not null check (
        safety_headroom_encoder_engines >= 0),
    safety_headroom_decoder_engines bigint not null check (
        safety_headroom_decoder_engines >= 0),
    primary key (
        physical_host_id, physical_host_incarnation_id, snapshot_generation, device_id),
    foreign key (physical_host_id, physical_host_incarnation_id, snapshot_generation)
        references wf_host_resource_snapshot (
            physical_host_id, physical_host_incarnation_id, snapshot_generation),
    check (safety_headroom_vram_bytes <= vram_bytes),
    check (safety_headroom_compute_units <= compute_units),
    check (safety_headroom_encoder_engines <= encoder_engines),
    check (safety_headroom_decoder_engines <= decoder_engines)
);

create or replace function wf_require_snapshot_device_publication_transaction()
returns trigger language plpgsql as $$
declare snapshot_publication_transaction_id bigint;
begin
    select publication_transaction_id into snapshot_publication_transaction_id
      from wf_host_resource_snapshot
     where physical_host_id = new.physical_host_id
       and physical_host_incarnation_id = new.physical_host_incarnation_id
       and snapshot_generation = new.snapshot_generation;
    if snapshot_publication_transaction_id is null
       or snapshot_publication_transaction_id <> txid_current() then
        raise exception 'host resource snapshot device membership must be inserted in snapshot publication transaction';
    end if;
    return new;
end
$$;

create trigger wf_snapshot_device_insert_during_publication
before insert on wf_host_resource_snapshot_device
for each row execute function wf_require_snapshot_device_publication_transaction();

create or replace function wf_reject_published_snapshot_device_mutation()
returns trigger language plpgsql as $$
begin
    raise exception 'durable host resource snapshot device membership is immutable';
end
$$;

create trigger wf_snapshot_device_membership_immutable
before update or delete on wf_host_resource_snapshot_device
for each row execute function wf_reject_published_snapshot_device_mutation();

create table wf_runtime_registration (
    worker_runtime_id varchar(128) not null,
    worker_runtime_incarnation_id varchar(128) not null,
    physical_host_id varchar(128) not null,
    physical_host_incarnation_id varchar(128) not null,
    registered_at timestamptz not null,
    valid_until timestamptz not null,
    active boolean not null,
    primary key (worker_runtime_id, worker_runtime_incarnation_id),
    foreign key (physical_host_id, physical_host_incarnation_id)
        references wf_host_registration (physical_host_id, physical_host_incarnation_id),
    check (valid_until > registered_at)
);

create unique index ux_wf_one_active_runtime_registration
    on wf_runtime_registration (worker_runtime_id) where active;

create table wf_execution_backend_selection (
    selection_id varchar(128) primary key,
    task_id varchar(128) not null,
    backend varchar(64) not null check (
        backend in ('NATIVE_PULL_WORKER','OPEN_CUE_FARM','REMOTE_PROVIDER')),
    placement_authority_scope varchar(64) not null check (
        placement_authority_scope in (
            'PLATFORM_MANAGED','BACKEND_DELEGATED','REMOTE_PROVIDER_MANAGED')),
    active boolean not null,
    selected_at timestamptz not null,
    terminal_at timestamptz,
    unique (selection_id, task_id, backend),
    check ((active and terminal_at is null) or (not active and terminal_at is not null)),
    check ((backend = 'NATIVE_PULL_WORKER'
                and placement_authority_scope = 'PLATFORM_MANAGED')
        or (backend = 'OPEN_CUE_FARM'
                and placement_authority_scope = 'BACKEND_DELEGATED')
        or (backend = 'REMOTE_PROVIDER'
                and placement_authority_scope = 'REMOTE_PROVIDER_MANAGED'))
);

create unique index ux_wf_one_active_backend_selection_per_task
    on wf_execution_backend_selection (task_id) where active;

create table wf_execution_ownership_generation (
    task_id varchar(128) not null,
    generation bigint not null check (generation > 0),
    created_at timestamptz not null,
    primary key (task_id, generation)
);

create table wf_execution_attempt (
    attempt_id varchar(128) primary key,
    task_id varchar(128) not null,
    generation bigint not null,
    backend varchar(64) not null,
    state varchar(32) not null check (
        state in ('CREATED','RUNNING','SUCCEEDED','FAILED','CANCELLED','ABANDONED')),
    backend_selection_id varchar(128) not null unique,
    backend_local_handle_reference varchar(512),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (task_id, generation),
    constraint ux_wf_execution_attempt_task_generation
        unique (attempt_id, task_id, generation),
    constraint ux_wf_execution_attempt_generation
        unique (attempt_id, generation),
    foreign key (task_id, generation)
        references wf_execution_ownership_generation (task_id, generation),
    foreign key (backend_selection_id, task_id, backend)
        references wf_execution_backend_selection (selection_id, task_id, backend)
);

create table wf_execution_assignment (
    assignment_id varchar(128) primary key,
    task_id varchar(128) not null,
    attempt_id varchar(128) not null unique,
    generation bigint not null,
    worker_runtime_id varchar(128) not null,
    worker_runtime_incarnation_id varchar(128) not null,
    physical_host_id varchar(128) not null,
    physical_host_incarnation_id varchar(128) not null,
    created_at timestamptz not null,
    unique (assignment_id, task_id),
    unique (assignment_id, task_id, physical_host_id, physical_host_incarnation_id),
    unique (assignment_id, task_id, attempt_id, generation,
        worker_runtime_id, worker_runtime_incarnation_id),
    unique (assignment_id, task_id, attempt_id, generation),
    foreign key (attempt_id, task_id, generation)
        references wf_execution_attempt (attempt_id, task_id, generation)
);

create table wf_execution_observation (
    observation_id varchar(128) primary key,
    attempt_id varchar(128) not null,
    generation bigint not null,
    observed_state varchar(32) not null check (
        observed_state in ('SUBMITTED','RUNNING','SUCCEEDED','FAILED','CANCELLED','UNKNOWN')),
    current_evidence boolean not null,
    observed_at timestamptz not null,
    foreign key (attempt_id, generation)
        references wf_execution_attempt (attempt_id, generation)
);

create table wf_completion_event (
    completion_event_id varchar(128) primary key,
    task_id varchar(128) not null,
    attempt_id varchar(128) not null,
    generation bigint not null,
    artifact_commit_reference varchar(512) not null,
    artifact_committed_at timestamptz not null,
    completed_at timestamptz not null,
    foreign key (attempt_id, task_id, generation)
        references wf_execution_attempt (attempt_id, task_id, generation)
);

-- Phase 16: derived execution reuse metadata. This index is deliberately not
-- Artifact existence authority: values are immutable ArtifactId+ContentDigest
-- pins, lookup is tenant-scoped, and only WINNING rows are reusable.
create table wf_artifact_reuse_index (
    tenant_id varchar(128) not null,
    reuse_key_version varchar(64) not null,
    reuse_key_digest varchar(64) not null check (length(reuse_key_digest) = 64),
    reuse_key_canonical text not null,
    artifact_id varchar(64) not null references artifact(id) on delete cascade,
    artifact_digest_algorithm varchar(32) not null,
    artifact_digest_value varchar(64) not null check (length(artifact_digest_value) = 64),
    task_id varchar(128) not null,
    attempt_id varchar(128) not null,
    generation bigint not null check (generation > 0),
    publication_status varchar(16) not null check (publication_status in ('PENDING','WINNING')),
    completion_event_id varchar(128) references wf_completion_event(completion_event_id),
    published_at timestamptz not null,
    primary key (tenant_id, reuse_key_version, reuse_key_digest),
    foreign key (attempt_id, task_id, generation)
        references wf_execution_attempt (attempt_id, task_id, generation),
    check ((publication_status = 'PENDING' and completion_event_id is null)
        or (publication_status = 'WINNING' and completion_event_id is not null))
);

create index ix_wf_artifact_reuse_artifact on wf_artifact_reuse_index(artifact_id);
create index ix_wf_artifact_reuse_pending on wf_artifact_reuse_index(published_at)
    where publication_status = 'PENDING';

create table wf_execution_assignment_device (
    assignment_id varchar(128) not null
        references wf_execution_assignment (assignment_id) on delete cascade,
    device_id varchar(128) not null,
    primary key (assignment_id, device_id)
);

create table wf_reservation (
    reservation_id varchar(128) primary key,
    assignment_id varchar(128) not null references wf_execution_assignment (assignment_id),
    task_id varchar(128) not null,
    physical_host_id varchar(128) not null,
    physical_host_incarnation_id varchar(128) not null,
    kind varchar(32) not null check (kind in ('TASK','RESIDENT_RUNTIME')),
    state varchar(32) not null check (state in ('ACTIVE','RECOVERY_HOLD','RELEASED')),
    cpu_millicores bigint not null check (cpu_millicores >= 0),
    memory_bytes bigint not null check (memory_bytes >= 0),
    temporary_storage_bytes bigint not null check (temporary_storage_bytes >= 0),
    created_at timestamptz not null,
    unique (reservation_id, assignment_id),
    foreign key (assignment_id, task_id, physical_host_id, physical_host_incarnation_id)
        references wf_execution_assignment (
            assignment_id, task_id, physical_host_id, physical_host_incarnation_id)
);

create table wf_reservation_device (
    reservation_id varchar(128) not null
        references wf_reservation (reservation_id) on delete cascade,
    device_id varchar(128) not null,
    vram_bytes bigint not null check (vram_bytes >= 0),
    compute_units bigint not null check (compute_units >= 0),
    encoder_engines bigint not null check (encoder_engines >= 0),
    decoder_engines bigint not null check (decoder_engines >= 0),
    primary key (reservation_id, device_id)
);

create unique index ux_wf_one_active_reservation_per_task
    on wf_reservation (task_id) where state = 'ACTIVE';

create table wf_task_lease (
    lease_id varchar(128) primary key,
    task_id varchar(128) not null,
    assignment_id varchar(128) not null,
    attempt_id varchar(128) not null,
    generation bigint not null,
    worker_runtime_id varchar(128) not null,
    worker_runtime_incarnation_id varchar(128) not null,
    expires_at timestamptz not null,
    last_heartbeat_at timestamptz not null,
    heartbeat_interval_millis bigint not null check (heartbeat_interval_millis > 0),
    lease_duration_millis bigint not null check (
        lease_duration_millis > heartbeat_interval_millis),
    fencing_token varchar(128) not null unique,
    active boolean not null,
    created_at timestamptz not null,
    unique (lease_id, assignment_id),
    unique (lease_id, attempt_id, generation),
    unique (lease_id, task_id, assignment_id, attempt_id, generation),
    foreign key (assignment_id, task_id, attempt_id, generation)
        references wf_execution_assignment (assignment_id, task_id, attempt_id, generation),
    foreign key (assignment_id, task_id, attempt_id, generation,
            worker_runtime_id, worker_runtime_incarnation_id)
        references wf_execution_assignment (assignment_id, task_id, attempt_id, generation,
            worker_runtime_id, worker_runtime_incarnation_id),
    check (expires_at > last_heartbeat_at)
);

create unique index ux_wf_one_active_native_lease_per_task
    on wf_task_lease (task_id) where active;

create table wf_task_lease_reservation (
    lease_id varchar(128) not null,
    reservation_id varchar(128) not null,
    assignment_id varchar(128) not null,
    primary key (lease_id, reservation_id),
    foreign key (lease_id, assignment_id)
        references wf_task_lease (lease_id, assignment_id) on delete cascade,
    foreign key (reservation_id, assignment_id)
        references wf_reservation (reservation_id, assignment_id)
);

create table wf_task_ownership (
    task_id varchar(128) primary key,
    current_generation bigint not null check (current_generation >= 0),
    current_attempt_id varchar(128),
    active_assignment_id varchar(128),
    active_lease_id varchar(128),
    claimable boolean not null,
    updated_at timestamptz not null,
    check ((claimable and current_attempt_id is null
                and active_assignment_id is null and active_lease_id is null)
        or (not claimable and current_generation > 0
                and current_attempt_id is not null
                and active_assignment_id is not null and active_lease_id is not null)),
    foreign key (active_assignment_id, task_id, current_attempt_id, current_generation)
        references wf_execution_assignment (assignment_id, task_id, attempt_id, generation),
    foreign key (active_lease_id, task_id, active_assignment_id,
            current_attempt_id, current_generation)
        references wf_task_lease (lease_id, task_id, assignment_id, attempt_id, generation)
);

create table wf_request_work_resolution (
    request_work_id varchar(128) primary key,
    request_context_fingerprint varchar(64) not null
        check (length(request_context_fingerprint) = 64),
    result_kind varchar(32) not null check (
        result_kind in ('PENDING','GRANTED','NO_WORK','REJECTED','REPROBE_REQUIRED')),
    failure_reason varchar(128),
    assignment_id varchar(128) unique references wf_execution_assignment (assignment_id),
    task_id varchar(128),
    created_at timestamptz not null,
    check ((result_kind = 'GRANTED' and assignment_id is not null and task_id is not null
                and failure_reason is null)
        or (result_kind in ('PENDING','NO_WORK') and assignment_id is null
                and task_id is null and failure_reason is null)
        or (result_kind in ('REJECTED','REPROBE_REQUIRED') and assignment_id is null
                and task_id is null and failure_reason is not null)),
    foreign key (assignment_id, task_id)
        references wf_execution_assignment (assignment_id, task_id)
);

create table wf_local_admission (
    lease_id varchar(128) primary key references wf_task_lease (lease_id),
    attempt_id varchar(128) not null,
    generation bigint not null,
    decision varchar(16) not null check (decision in ('ACCEPT','DECLINE')),
    decline_reason varchar(128),
    result varchar(64) not null,
    received_at timestamptz not null,
    foreign key (lease_id, attempt_id, generation)
        references wf_task_lease (lease_id, attempt_id, generation),
    check ((decision = 'ACCEPT' and decline_reason is null)
        or (decision = 'DECLINE' and decline_reason is not null))
);

create table wf_physical_release_confirmation (
    confirmation_id varchar(128) primary key,
    lease_id varchar(128) not null references wf_task_lease (lease_id),
    confirmed_at timestamptz not null
);

create or replace function wf_assert_assignment_has_reservation()
returns trigger language plpgsql as $$
declare target_assignment varchar(128);
begin
    target_assignment := coalesce(new.assignment_id, old.assignment_id);
    if exists (select 1 from wf_execution_assignment where assignment_id = target_assignment)
       and not exists (select 1 from wf_reservation where assignment_id = target_assignment) then
        raise exception 'execution assignment % has no reservation', target_assignment;
    end if;
    return null;
end
$$;

create constraint trigger wf_assignment_requires_reservation
after insert or update on wf_execution_assignment
deferrable initially deferred
for each row execute function wf_assert_assignment_has_reservation();

create constraint trigger wf_reservation_delete_preserves_assignment
after delete or update of assignment_id on wf_reservation
deferrable initially deferred
for each row execute function wf_assert_assignment_has_reservation();

create or replace function wf_assert_lease_reservation_set()
returns trigger language plpgsql as $$
declare target_lease varchar(128);
declare target_assignment varchar(128);
begin
    target_lease := coalesce(new.lease_id, old.lease_id);
    select assignment_id into target_assignment
      from wf_task_lease where lease_id = target_lease;
    if target_assignment is not null and (
        not exists (select 1 from wf_task_lease_reservation where lease_id = target_lease)
        or exists (
            select 1 from wf_reservation r
             where r.assignment_id = target_assignment
               and not exists (
                   select 1 from wf_task_lease_reservation lr
                    where lr.lease_id = target_lease
                      and lr.reservation_id = r.reservation_id))) then
        raise exception 'lease % does not cover its assignment reservations', target_lease;
    end if;
    return null;
end
$$;

create constraint trigger wf_lease_requires_reservation_set
after insert or update on wf_task_lease
deferrable initially deferred
for each row execute function wf_assert_lease_reservation_set();

create constraint trigger wf_lease_reservation_delete_preserves_set
after delete or update on wf_task_lease_reservation
deferrable initially deferred
for each row execute function wf_assert_lease_reservation_set();

create or replace function wf_close_terminal_backend_selection()
returns trigger language plpgsql as $$
begin
    if new.state in ('SUCCEEDED','FAILED','CANCELLED','ABANDONED')
       and old.state is distinct from new.state then
        update wf_execution_backend_selection
           set active = false, terminal_at = new.updated_at
         where selection_id = new.backend_selection_id and active;
    end if;
    return new;
end
$$;

create trigger wf_attempt_terminal_closes_backend_selection
after update of state on wf_execution_attempt
for each row execute function wf_close_terminal_backend_selection();

-- Durable greenfield commercial seeds. These are data, not a second Java catalog/plan writer.
insert into subscription_plan(id,plan_key,name,description,billing_interval,base_price_minor,currency_code,included_quota,status) values
 ('seed-plan-basic','basic_monthly','Basic Monthly','Catalog seed','MONTHLY',2999,'USD','{}','ACTIVE'),
 ('seed-plan-pro','pro_monthly','Pro Monthly','Catalog seed','MONTHLY',9999,'USD','{}','ACTIVE'),
 ('seed-plan-team','team_monthly','Team Monthly','Catalog seed','MONTHLY',29999,'USD','{}','ACTIVE'),
 ('seed-plan-enterprise','enterprise_monthly','Enterprise Monthly','Catalog seed','MONTHLY',99999,'USD','{}','ACTIVE'),
 ('seed-plan-gpu','addon_gpu_monthly','GPU Add-on','Catalog seed','MONTHLY',4999,'USD','{}','ACTIVE'),
 ('seed-plan-ai','addon_ai_monthly','AI Add-on','Catalog seed','MONTHLY',2999,'USD','{}','ACTIVE');

insert into pricing_rule(id,tenant_id,rule_key,rule_version,name,description,pricing_model,meter_key,
 unit_price_minor,currency_code,tier_config,status,effective_from) values
 ('seed-price-basic','GLOBAL','price-basic',1,'Basic Monthly','Catalog price reference','SUBSCRIPTION','offering.basic',2999,'USD','[]','ACTIVE','2020-01-01T00:00:00Z'),
 ('seed-price-pro','GLOBAL','price-pro',1,'Pro Monthly','Catalog price reference','SUBSCRIPTION','offering.pro',9999,'USD','[]','ACTIVE','2020-01-01T00:00:00Z'),
 ('seed-price-team','GLOBAL','price-team',1,'Team Monthly','Catalog price reference','SUBSCRIPTION','offering.team',29999,'USD','[]','ACTIVE','2020-01-01T00:00:00Z'),
 ('seed-price-enterprise','GLOBAL','price-enterprise',1,'Enterprise Monthly','Catalog price reference','SUBSCRIPTION','offering.enterprise',99999,'USD','[]','ACTIVE','2020-01-01T00:00:00Z'),
 ('seed-price-gpu','GLOBAL','price-gpu',1,'GPU Add-on','Catalog price reference','SUBSCRIPTION','offering.gpu',4999,'USD','[]','ACTIVE','2020-01-01T00:00:00Z'),
 ('seed-price-ai','GLOBAL','price-ai',1,'AI Add-on','Catalog price reference','SUBSCRIPTION','offering.ai',2999,'USD','[]','ACTIVE','2020-01-01T00:00:00Z'),
 ('seed-price-credit50','GLOBAL','price-credit50',1,'Credit Pack 50','Catalog price reference','CREDIT','offering.credit50',5000,'USD','[]','ACTIVE','2020-01-01T00:00:00Z'),
 ('seed-price-credit200','GLOBAL','price-credit200',1,'Credit Pack 200','Catalog price reference','CREDIT','offering.credit200',18000,'USD','[]','ACTIVE','2020-01-01T00:00:00Z'),
 ('seed-price-seat5','GLOBAL','price-seat5',1,'Five Seats','Catalog price reference','CUSTOM','offering.seat5',1999,'USD','[]','ACTIVE','2020-01-01T00:00:00Z');

insert into commerce_product(id,product_code,product_line_type,display_name,lifecycle_state,version,created_at,updated_at) values
 ('seed-product-basic','basic_monthly','BASE_SUBSCRIPTION','Basic Monthly','ACTIVE',1,now(),now()),
 ('seed-product-pro','pro_monthly','BASE_SUBSCRIPTION','Pro Monthly','ACTIVE',1,now(),now()),
 ('seed-product-team','team_monthly','BASE_SUBSCRIPTION','Team Monthly','ACTIVE',1,now(),now()),
 ('seed-product-enterprise','enterprise_monthly','BASE_SUBSCRIPTION','Enterprise Monthly','ACTIVE',1,now(),now()),
 ('seed-product-gpu','addon_gpu_monthly','ADD_ON_SUBSCRIPTION','GPU Add-on','ACTIVE',1,now(),now()),
 ('seed-product-ai','addon_ai_monthly','ADD_ON_SUBSCRIPTION','AI Add-on','ACTIVE',1,now(),now()),
 ('seed-product-credit50','credit_pack_50','CREDIT_PACK','Credit Pack 50','ACTIVE',1,now(),now()),
 ('seed-product-credit200','credit_pack_200','CREDIT_PACK','Credit Pack 200','ACTIVE',1,now(),now()),
 ('seed-product-seat5','seat_pack_5','SEAT_PACK','Five Seats','ACTIVE',1,now(),now());

insert into commercial_offering(id,product_id,offering_key,offering_version,lifecycle_state,row_version,purchase_mode,
 tenant_scope,market_scope,valid_from,entitlement_bundle_ref,entitlement_bundle_version,quota_profile_ref,quota_profile_version,
 subscription_plan_ref,subscription_plan_version,commercial_price_ref,commercial_price_version,amount_minor_snapshot,currency_code_snapshot,
 credit_quantity_minor,seat_quantity,seat_feature_key,created_at,updated_at) values
 ('seed-offer-basic','seed-product-basic','Basic Monthly',1,'ACTIVE',1,'SUBSCRIPTION','GLOBAL','GLOBAL','2020-01-01T00:00:00Z','basic_features',1,'basic_quota',1,'basic_monthly',1,'price-basic',1,2999,'USD',null,null,null,now(),now()),
 ('seed-offer-pro','seed-product-pro','Pro Monthly',1,'ACTIVE',1,'SUBSCRIPTION','GLOBAL','GLOBAL','2020-01-01T00:00:00Z','default_features',1,'pro_quota',1,'pro_monthly',1,'price-pro',1,9999,'USD',null,null,null,now(),now()),
 ('seed-offer-team','seed-product-team','Team Monthly',1,'ACTIVE',1,'SUBSCRIPTION','GLOBAL','GLOBAL','2020-01-01T00:00:00Z','team_features',1,'team_quota',1,'team_monthly',1,'price-team',1,29999,'USD',null,null,null,now(),now()),
 ('seed-offer-enterprise','seed-product-enterprise','Enterprise Monthly',1,'ACTIVE',1,'SUBSCRIPTION','GLOBAL','GLOBAL','2020-01-01T00:00:00Z','enterprise_features',1,'enterprise_quota',1,'enterprise_monthly',1,'price-enterprise',1,99999,'USD',null,null,null,now(),now()),
 ('seed-offer-gpu','seed-product-gpu','GPU Add-on',1,'ACTIVE',1,'SUBSCRIPTION','GLOBAL','GLOBAL','2020-01-01T00:00:00Z',null,null,'pro_quota',1,'addon_gpu_monthly',1,'price-gpu',1,4999,'USD',null,null,null,now(),now()),
 ('seed-offer-ai','seed-product-ai','AI Add-on',1,'ACTIVE',1,'SUBSCRIPTION','GLOBAL','GLOBAL','2020-01-01T00:00:00Z',null,null,'pro_quota',1,'addon_ai_monthly',1,'price-ai',1,2999,'USD',null,null,null,now(),now()),
 ('seed-offer-credit50','seed-product-credit50','Credit Pack 50',1,'ACTIVE',1,'CREDIT_PACK','GLOBAL','GLOBAL','2020-01-01T00:00:00Z',null,null,null,null,null,null,'price-credit50',1,5000,'USD',5000,null,null,now(),now()),
 ('seed-offer-credit200','seed-product-credit200','Credit Pack 200',1,'ACTIVE',1,'CREDIT_PACK','GLOBAL','GLOBAL','2020-01-01T00:00:00Z',null,null,null,null,null,null,'price-credit200',1,18000,'USD',20000,null,null,now(),now()),
 ('seed-offer-seat5','seed-product-seat5','Five Seats',1,'ACTIVE',1,'SEAT_PACK','GLOBAL','GLOBAL','2020-01-01T00:00:00Z',null,null,null,null,null,null,'price-seat5',1,1999,'USD',null,5,'render.minutes',now(),now());
