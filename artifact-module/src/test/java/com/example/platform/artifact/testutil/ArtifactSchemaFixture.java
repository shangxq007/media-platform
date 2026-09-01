package com.example.platform.artifact.testutil;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * GCR-2: canonical Artifact schema fixture for artifact-module DB tests.
 * Creates the Artifact-owned subset of the canonical artifact / artifact_replica /
 * artifact_pin / artifact_relation schema idempotently. The relation fixture mirrors
 * both production endpoint foreign keys because they are part of the bounded
 * new-child provenance proof. Cross-module pin ownership to project and
 * timeline_revision is covered by tests that apply the complete production V1 schema.
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
                + "tombstoned_at timestamp,"
                + "constraint uq_artifact_tenant_id unique (tenant_id, id)"
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
                + "tenant_id varchar(64) not null,"
                + "revision_id varchar(64) not null,"
                + "project_id varchar(64) not null,"
                + "artifact_id varchar(64) not null,"
                + "content_digest varchar(128) not null,"
                + "pinned_at timestamp not null,"
                + "constraint uq_artifact_pin_revision "
                + "unique (tenant_id, project_id, revision_id, artifact_id),"
                + "constraint fk_artifact_pin_artifact foreign key (tenant_id, artifact_id) "
                + "references artifact(tenant_id, id)"
                + ")");
        jdbc.execute("CREATE TABLE IF NOT EXISTS artifact_relation ("
                + "id varchar(64) primary key,"
                + "source_artifact_id varchar(64) not null,"
                + "target_artifact_id varchar(64) not null,"
                + "relation_type varchar(64) not null,"
                + "created_at timestamp not null,"
                + "constraint fk_artifact_relation_source foreign key (source_artifact_id) "
                + "references artifact(id),"
                + "constraint fk_artifact_relation_target foreign key (target_artifact_id) "
                + "references artifact(id)"
                + ")");
    }
}
