package com.example.platform.render.testsupport;

import java.util.function.Consumer;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/**
 * Lightweight unit-test-only schema. It is deliberately noncanonical and must never be used
 * as evidence for PostgreSQL ownership, Flyway parity, canonical persistence, or V2 acceptance.
 * Canonical integration tests apply the exact production V1 migration instead.
 */
public final class RenderTestSchemaFixture {

    public static final boolean CANONICAL_POSTGRES_COVERAGE = false;
    public static final String SERVER_ACTOR = "server:test-actor";

    private RenderTestSchemaFixture() {}

    public static void createSchema(DSLContext dsl) {
        // Drop all tables to ensure fresh schema (handles schema evolution)
        dsl.execute("""
            DROP TABLE IF EXISTS
                flyway_schema_history,
                apply_command,
                timeline_revision_ref,
                timeline_revision_parent,
                project_revision_counter,
                timeline_revision,
                timeline_snapshot,
                artifact_pin,
                artifact_replica,
                artifact,
                media_probe_observation,
                media_stream,
                media_asset_artifact,
                media_asset,
                client_export_session,
                quota_usage,
                render_job_lease,
                render_worker,
                render_job_status_history,
                render_job,
                product,
                project
            CASCADE
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS project (
                id varchar(64) primary key,
                tenant_id varchar(64) not null,
                name varchar(255) not null,
                description text,
                status varchar(32) not null,
                created_at timestamp not null,
                unique (tenant_id, id)
            )
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS product (
                product_id varchar(64) primary key,
                tenant_id varchar(64),
                project_id varchar(64),
                owner_asset_id varchar(64),
                product_type varchar(32) not null,
                representation_kind varchar(32) not null,
                producer_type varchar(32),
                producer_id varchar(64),
                source_timeline_revision_id varchar(64),
                status varchar(32) not null,
                storage_reference_id varchar(256),
                checksum varchar(128),
                content_hash varchar(128),
                mime_type varchar(64),
                version integer not null default 1,
                metadata_json text,
                created_at timestamp not null,
                updated_at timestamp not null
            )
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS render_job (
                id varchar(64) primary key,
                project_id varchar(128) not null,
                timeline_snapshot_id varchar(128),
                profile varchar(128),
                status varchar(32),
                created_at timestamp,
                ai_script text,
                artifact_uri text,
                error_message text,
                tenant_id varchar(64),
                pipeline_plan_json text,
                pipeline_execution_json text,
                base_job_id varchar(64),
                trace_id varchar(128),
                selected_provider varchar(128),
                timeline_revision_id varchar(64),
                updated_at timestamp with time zone
            )
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS render_job_status_history (
                id varchar(64) primary key,
                job_id varchar(64) not null,
                from_status varchar(30),
                to_status varchar(30) not null,
                reason varchar(255),
                error_code varchar(100),
                occurred_at timestamp not null
            )
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS render_worker (
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
                created_at timestamp not null default now(),
                updated_at timestamp not null default now()
            )
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS render_job_lease (
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
                created_at timestamp not null default now(),
                updated_at timestamp not null default now()
            )
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS quota_usage (
                id varchar(64) primary key,
                tenant_id varchar(64) not null,
                feature_code varchar(80) not null,
                usage_value int not null default 0,
                created_at timestamp not null,
                updated_at timestamp not null
            )
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS client_export_session (
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
                created_at timestamp not null default now(),
                updated_at timestamp not null default now(),
                expires_at timestamp
            )
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS media_asset (
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
            )
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS media_stream (
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
                container_stream_description varchar(128)
            )
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS media_asset_artifact (
                media_asset_id varchar(64) not null,
                artifact_id varchar(64) not null,
                relationship varchar(16) not null,
                created_at timestamp not null default now(),
                constraint pk_maa primary key (media_asset_id, artifact_id, relationship)
            )
        """);

        // GCR-2: canonical Artifact tables (artifact + artifact_replica + artifact_pin)
        dsl.execute("""
            CREATE TABLE IF NOT EXISTS artifact (
                id varchar(64) primary key,
                tenant_id varchar(64) not null,
                project_id varchar(64),
                render_job_id varchar(64),
                content_digest varchar(128) not null,
                byte_length bigint not null,
                media_type varchar(32) not null,
                artifact_kind varchar(32) not null,
                state varchar(32) not null,
                schema_version int not null default 1,
                created_at timestamp not null,
                tombstoned_at timestamp,
                unique (tenant_id, id)
            )
        """);
        dsl.execute("""
            CREATE TABLE IF NOT EXISTS artifact_replica (
                artifact_id varchar(64) not null,
                replica_id varchar(64) not null,
                provider_id varchar(64) not null,
                storage_object_id varchar(128) not null,
                region varchar(64),
                role varchar(32) not null,
                state varchar(32) not null default 'ACTIVE',
                created_at timestamp not null,
                primary key (artifact_id, replica_id),
                constraint fk_replica_artifact foreign key (artifact_id) references artifact(id)
            )
        """);
        dsl.execute("""
            CREATE TABLE IF NOT EXISTS artifact_pin (
                pin_id varchar(64) primary key,
                tenant_id varchar(64) not null,
                revision_id varchar(64) not null,
                project_id varchar(64) not null,
                artifact_id varchar(64) not null,
                content_digest varchar(128) not null,
                pinned_at timestamp not null,
                constraint uq_pin_revision_artifact unique (tenant_id, project_id, revision_id, artifact_id),
                constraint fk_pin_artifact foreign key (tenant_id, artifact_id)
                    references artifact(tenant_id, id)
            )
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS media_probe_observation (
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
                probed_at timestamp not null default now()
            )
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS timeline_snapshot (
                id varchar(64) primary key,
                project_id varchar(64) not null,
                tenant_id varchar(64) not null,
                payload_json text not null,
                schema_version varchar(32) not null default 'timeline-1.0',
                content_hash varchar(64),
                revision_number int,
                semantic_revision_id varchar(64),
                created_at timestamp,
                unique (tenant_id, project_id, id),
                unique (tenant_id, project_id, semantic_revision_id),
                foreign key (tenant_id, project_id)
                    references project(tenant_id, id)
            )
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS timeline_revision (
                id varchar(64) primary key,
                project_id varchar(64) not null,
                tenant_id varchar(64) not null,
                parent_revision_id varchar(64),
                revision_number int not null,
                snapshot_id varchar(64) not null,
                internal_revision int not null,
                content_hash varchar(64) not null,
                schema_version varchar(32) not null default 'timeline-1.0',
                source varchar(32) not null,
                author_user_id varchar(64),
                edit_session_id varchar(64),
                message varchar(512),
                change_summary_json text,
                patch_ops_json text,
                labels_json varchar(512),
                is_merge boolean not null default false,
                merge_parent_revision_ids text,
                merge_base_revision_id varchar(64),
                created_at timestamp not null,
                unique (tenant_id, project_id, id),
                unique (project_id, id),
                foreign key (tenant_id, project_id)
                    references project(tenant_id, id),
                foreign key (tenant_id, project_id, parent_revision_id)
                    references timeline_revision(tenant_id, project_id, id)
                    deferrable initially deferred,
                foreign key (tenant_id, project_id, snapshot_id)
                    references timeline_snapshot(tenant_id, project_id, id)
            )
        """);

        dsl.execute("create unique index if not exists ux_timeline_revision_project_num "
                + "on timeline_revision(project_id, revision_number)");
        dsl.execute("alter table timeline_snapshot add constraint "
                + "fk_timeline_snapshot_semantic_revision foreign key "
                + "(tenant_id, project_id, semantic_revision_id) references "
                + "timeline_revision(tenant_id, project_id, id)");
        dsl.execute("alter table artifact_pin add constraint "
                + "fk_artifact_pin_revision foreign key "
                + "(tenant_id, project_id, revision_id) references "
                + "timeline_revision(tenant_id, project_id, id)");
        dsl.execute("alter table artifact_pin add constraint "
                + "fk_artifact_pin_project foreign key (tenant_id, project_id) "
                + "references project(tenant_id, id)");
        dsl.execute("""
            CREATE TABLE IF NOT EXISTS project_revision_counter (
                project_id varchar(64) primary key,
                next_revision_number bigint not null
            )
        """);
        dsl.execute("""
            CREATE TABLE IF NOT EXISTS timeline_revision_parent (
                tenant_id varchar(64) not null,
                project_id varchar(64) not null,
                revision_id varchar(64) not null,
                parent_revision_id varchar(64) not null,
                parent_order int not null,
                primary key (tenant_id, project_id, revision_id, parent_order),
                unique (tenant_id, project_id, revision_id, parent_revision_id),
                check (parent_order >= 0),
                check (revision_id <> parent_revision_id),
                foreign key (tenant_id, project_id, revision_id)
                    references timeline_revision(tenant_id, project_id, id),
                foreign key (tenant_id, project_id, parent_revision_id)
                    references timeline_revision(tenant_id, project_id, id)
                    deferrable initially deferred
            )
        """);
        dsl.execute("""
            CREATE TABLE IF NOT EXISTS timeline_revision_ref (
                tenant_id varchar(64) not null,
                project_id varchar(64) not null,
                ref_id varchar(64) not null,
                head_revision_id varchar(64),
                version bigint not null default 0,
                updated_at timestamp not null default current_timestamp,
                primary key (tenant_id, project_id, ref_id),
                foreign key (tenant_id, project_id, head_revision_id)
                    references timeline_revision(tenant_id, project_id, id)
                    deferrable initially deferred
            )
        """);
        dsl.execute("""
            CREATE TABLE IF NOT EXISTS apply_command (
                apply_command_id varchar(64) primary key,
                plan_digest varchar(64) not null,
                fingerprint varchar(64) not null,
                status varchar(16) not null,
                result_revision_id varchar(64),
                result_content_hash varchar(64),
                result_status varchar(16),
                tenant_id varchar(64) not null,
                project_id varchar(64) not null,
                command_domain varchar(32) not null default 'OPERATION_PLAN',
                target_ref_id varchar(64) not null,
                expected_head_revision_id varchar(64),
                expected_result_status varchar(32) not null,
                created_at timestamp not null default current_timestamp,
                completed_at timestamp,
                foreign key (tenant_id, project_id)
                    references project(tenant_id, id),
                foreign key (tenant_id, project_id, target_ref_id)
                    references timeline_revision_ref(tenant_id, project_id, ref_id)
                    deferrable initially deferred,
                foreign key (tenant_id, project_id, expected_head_revision_id)
                    references timeline_revision(tenant_id, project_id, id)
                    deferrable initially deferred,
                foreign key (tenant_id, project_id, result_revision_id)
                    references timeline_revision(tenant_id, project_id, id)
                    deferrable initially deferred
            )
        """);
    }

    /** Creates the trusted ownership row required by every canonical Timeline test project. */
    public static void insertCanonicalProject(DSLContext dsl, String tenantId, String projectId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId required");
        }
        if (projectId == null || projectId.isBlank() || projectId.length() > 64) {
            throw new IllegalArgumentException("projectId must be 1..64 characters");
        }
        dsl.execute("insert into project (id, tenant_id, name, status, created_at) "
                        + "values (?, ?, ?, 'ACTIVE', now())",
                projectId, tenantId, projectId);
    }

    /**
     * Seeds an owned immutable revision for tests whose subject is a dependent row store.
     * This is fixture construction only; production revisions must use the canonical writer.
     */
    public static void insertCanonicalRevisionFixture(
            DSLContext dsl, String tenantId, String projectId, String revisionId) {
        String snapshotId = "fixture-snap-" + revisionId;
        dsl.execute("insert into timeline_snapshot "
                        + "(id, project_id, tenant_id, payload_json) values (?, ?, ?, '{}')",
                snapshotId, projectId, tenantId);
        dsl.execute("insert into timeline_revision "
                        + "(id, project_id, tenant_id, revision_number, snapshot_id, "
                        + "internal_revision, content_hash, source, created_at) "
                        + "values (?, ?, ?, 1, ?, 1, ?, 'fixture', current_timestamp)",
                revisionId, projectId, tenantId, snapshotId, "fixture-digest-" + revisionId);
    }

    /**
     * Plants an intentionally corrupt row graph so application fail-closed behavior can be
     * tested even though the V1-equivalent foreign keys correctly reject that graph normally.
     * The bypass is transaction-local and all constraints remain enabled afterwards.
     */
    public static void plantCorruptFixture(DSLContext dsl, Consumer<DSLContext> mutation) {
        dsl.transaction(configuration -> {
            DSLContext tx = DSL.using(configuration);
            tx.execute("set local session_replication_role = replica");
            mutation.accept(tx);
        });
    }

    public static void truncate(DSLContext dsl) {
        dsl.execute("""
            TRUNCATE TABLE
                apply_command,
                timeline_revision_ref,
                timeline_revision_parent,
                project_revision_counter,
                timeline_revision,
                timeline_snapshot,
                media_probe_observation,
                media_stream,
                media_asset_artifact,
                media_asset,
                artifact_pin,
                artifact_replica,
                artifact,
                client_export_session,
                quota_usage,
                render_job_lease,
                render_worker,
                render_job_status_history,
                render_job,
                product,
                project
            RESTART IDENTITY CASCADE
        """);
    }
}
