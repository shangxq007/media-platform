package com.example.platform.render.testsupport;

import org.jooq.DSLContext;

public final class RenderTestSchemaFixture {

    private RenderTestSchemaFixture() {}

    public static void createSchema(DSLContext dsl) {
        // Drop all tables to ensure fresh schema (handles schema evolution)
        dsl.execute("""
            DROP TABLE IF EXISTS
                flyway_schema_history,
                timeline_revision,
                timeline_snapshot,
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
                created_at timestamp not null
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
                updated_at timestamp not null,
                current_revision_id varchar(64)
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
                initiator_type varchar(32) not null,
                initiator_id varchar(128) not null,
                initiator_tenant_id varchar(64) not null,
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
                tombstoned_at timestamp
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
                revision_id varchar(64) not null,
                project_id varchar(64) not null,
                artifact_id varchar(64) not null,
                content_digest varchar(128) not null,
                pinned_at timestamp not null,
                constraint uq_pin_revision_artifact unique (revision_id, artifact_id),
                constraint fk_pin_artifact foreign key (artifact_id) references artifact(id)
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
                project_id varchar(64),
                tenant_id varchar(64),
                payload_json text not null,
                schema_version varchar(32),
                content_hash varchar(64),
                revision_number int,
                created_at timestamp
            )
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS timeline_revision (
                id varchar(64) primary key,
                project_id varchar(64) not null,
                tenant_id varchar(64),
                parent_revision_id varchar(64),
                revision_number int not null,
                snapshot_id varchar(64) not null,
                internal_revision int not null,
                content_hash varchar(64) not null,
                schema_version varchar(32),
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
                created_at timestamp not null
            )
        """);
    }

    public static void truncate(DSLContext dsl) {
        dsl.execute("""
            TRUNCATE TABLE
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
