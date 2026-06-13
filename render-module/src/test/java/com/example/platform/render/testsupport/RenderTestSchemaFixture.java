package com.example.platform.render.testsupport;

import org.jooq.DSLContext;

public final class RenderTestSchemaFixture {

    private RenderTestSchemaFixture() {}

    public static void createSchema(DSLContext dsl) {
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
            CREATE TABLE IF NOT EXISTS render_job (
                id varchar(64) primary key,
                project_id varchar(64) not null,
                tenant_id varchar(64),
                timeline_snapshot_id varchar(64) not null,
                profile varchar(100) not null,
                status varchar(20) not null,
                created_at timestamp not null,
                ai_script text,
                artifact_uri text,
                error_message text,
                pipeline_plan_json text,
                pipeline_execution_json text,
                base_job_id varchar(64),
                trace_id varchar(64)
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
            CREATE TABLE IF NOT EXISTS media_asset_metadata (
                id varchar(64) primary key,
                tenant_id varchar(64) not null,
                project_id varchar(64) not null,
                asset_id varchar(64) not null,
                asset_uri varchar(1024) not null,
                valid boolean not null default false,
                container varchar(32),
                file_size_bytes bigint default 0,
                duration_ms double precision default 0,
                width int default 0,
                height int default 0,
                fps double precision default 0,
                video_codec varchar(64),
                audio_codec varchar(64),
                audio_sample_rate int default 0,
                audio_channels int default 0,
                has_audio boolean default false,
                rotation int default 0,
                color_space varchar(32),
                bitrate bigint default 0,
                is_vfr boolean default false,
                stream_count int default 0,
                client_export_compatible boolean default false,
                normalize_required boolean default true,
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
                created_at timestamp not null
            )
        """);
    }

    public static void truncate(DSLContext dsl) {
        dsl.execute("""
            TRUNCATE TABLE
                timeline_revision,
                timeline_snapshot,
                media_asset_metadata,
                client_export_session,
                quota_usage,
                render_job_lease,
                render_worker,
                render_job_status_history,
                render_job,
                project
            RESTART IDENTITY CASCADE
        """);
    }
}
