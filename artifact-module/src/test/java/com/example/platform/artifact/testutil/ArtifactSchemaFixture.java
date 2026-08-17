package com.example.platform.artifact.testutil;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * GCR-2: canonical Artifact schema fixture for artifact-module DB tests.
 * Creates the canonical artifact / artifact_replica / artifact_pin tables
 * (matching V1__initial_schema.sql final shape) idempotently.
 */
public final class ArtifactSchemaFixture {

    private ArtifactSchemaFixture() {}

    public static void createCanonicalTables(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS artifact ("
                + "id varchar(64) primary key,"
                + "tenant_id varchar(64) not null,"
                + "project_id varchar(64),"
                + "render_job_id varchar(64),"
                + "content_digest varchar(128) not null,"
                + "byte_length bigint not null,"
                + "media_type varchar(32) not null,"
                + "artifact_kind varchar(32) not null,"
                + "state varchar(32) not null,"
                + "schema_version int not null default 1,"
                + "created_at timestamp not null,"
                + "tombstoned_at timestamp"
                + ")");
        jdbc.execute("CREATE TABLE IF NOT EXISTS artifact_replica ("
                + "artifact_id varchar(64) not null,"
                + "replica_id varchar(64) not null,"
                + "provider_id varchar(64) not null,"
                + "storage_object_id varchar(128) not null,"
                + "region varchar(64),"
                + "role varchar(32) not null,"
                + "state varchar(32) not null default 'ACTIVE',"
                + "created_at timestamp not null,"
                + "primary key (artifact_id, replica_id),"
                + "constraint fk_replica_artifact foreign key (artifact_id) references artifact(id)"
                + ")");
        jdbc.execute("CREATE TABLE IF NOT EXISTS artifact_pin ("
                + "pin_id varchar(64) primary key,"
                + "revision_id varchar(64) not null,"
                + "project_id varchar(64) not null,"
                + "artifact_id varchar(64) not null,"
                + "content_digest varchar(128) not null,"
                + "pinned_at timestamp not null,"
                + "constraint uq_pin_revision_artifact unique (revision_id, artifact_id),"
                + "constraint fk_pin_artifact foreign key (artifact_id) references artifact(id)"
                + ")");
    }
}
