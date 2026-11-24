package com.example.platform;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * GCR5/GCR6 (DATABASE_CANONICALIZATION_CONTRACT_V1 C5/C7/C9): real-PostgreSQL
 * structural integrity tests for the canonical V1 constraints — same-owner
 * relations pass, cross-owner/cross-revision references fail closed at the
 * database level, and canonical history is not destroyed by accidental cascade.
 *
 * Exercises the original constraints after canonical Flyway migration.
 */
class Gcr5Gcr6DatabaseStructuralIntegrityTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void createSchema() throws Exception {
        org.flywaydb.core.Flyway.configure().dataSource(jdbcUrl(), username(), password())
                .defaultSchema("public").locations("classpath:db/migration").load().migrate();
        dataSource = createDataSource();
        jdbc = new JdbcTemplate(dataSource);
    }

    private static void assertForeignKeyViolation(Executable executable, String message) {
        assertSqlState(executable,message,"23503");
    }

    private static void assertSqlState(Executable executable,String message,String state) {
        var failure = assertThrows(DataIntegrityViolationException.class, executable, message);
        Throwable cause = failure;
        while (cause != null && !(cause instanceof SQLException)) {
            cause = cause.getCause();
        }
        var postgresFailure = assertInstanceOf(SQLException.class, cause, message);
        assertEquals(state, postgresFailure.getSQLState(), message);
    }

    @AfterAll
    static void tearDown() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM artifacts.artifact_pin");
        jdbc.update("DELETE FROM timeline.timeline_revision_parent");
        jdbc.update("DELETE FROM timeline.timeline_revision_ref");
        jdbc.update("DELETE FROM timeline.timeline_revision");
        jdbc.update("DELETE FROM timeline.timeline_snapshot");
        jdbc.update("DELETE FROM artifacts.artifact_relation");
        jdbc.update("DELETE FROM artifacts.artifact_replica");
        jdbc.update("DELETE FROM media.source_visual_description_snapshot");
        jdbc.update("DELETE FROM media.media_stream");
        jdbc.update("DELETE FROM artifacts.artifact");
        jdbc.update("DELETE FROM render.render_job");
        jdbc.update("DELETE FROM media.media_stream");
        jdbc.update("DELETE FROM identity.project");
        jdbc.update("DELETE FROM identity.tenant");
    }

    // ── D4: tenant/project ownership ──

    @Test
    void timelineRevisionProjectFkRejectsUnknownProject() {
        jdbc.update("INSERT INTO identity.tenant (id, name, status, created_at) VALUES ('ten-1','t','ACTIVE',now())");
        assertForeignKeyViolation(
                () -> jdbc.update("INSERT INTO timeline.timeline_snapshot (id, tenant_id, project_id, payload_json) "
                        + "VALUES ('snap-1','ten-1','proj-missing','{}')"),
                "timeline_snapshot.project_id FK must reject unknown project");
    }

    @Test
    void timelineRevisionProjectFkAcceptsSameProject() {
        jdbc.update("INSERT INTO identity.tenant (id, name, status, created_at) VALUES ('ten-1','t','ACTIVE',now())");
        jdbc.update("INSERT INTO identity.project (id, tenant_id, name, created_at) VALUES ('proj-1','ten-1','p',now())");
        assertDoesNotThrow(() -> jdbc.update(
                "INSERT INTO timeline.timeline_snapshot (id, tenant_id, project_id, payload_json) "
                        + "VALUES ('snap-1','ten-1','proj-1','{}')"));
        assertDoesNotThrow(() -> jdbc.update(
                "INSERT INTO timeline.timeline_revision (id, tenant_id, project_id, revision_number, snapshot_id, content_hash, schema_version, source, created_at) "
                        + "VALUES ('rev-1','ten-1','proj-1',1,'snap-1','hash','internal-1.0','test',now())"));
    }

    @Test
    void timelineRevisionParentFkRejectsUnknownParent() {
        jdbc.update("INSERT INTO identity.tenant (id, name, status, created_at) VALUES ('ten-1','t','ACTIVE',now())");
        jdbc.update("INSERT INTO identity.project (id, tenant_id, name, created_at) VALUES ('proj-1','ten-1','p',now())");
        jdbc.update("INSERT INTO timeline.timeline_snapshot (id, tenant_id, project_id, payload_json) "
                + "VALUES ('snap-1','ten-1','proj-1','{}')");
        assertForeignKeyViolation(
                () -> jdbc.update(
                        "INSERT INTO timeline.timeline_revision (id, tenant_id, project_id, parent_revision_id, revision_number, snapshot_id, content_hash, schema_version, source, created_at) "
                                + "VALUES ('rev-1','ten-1','proj-1','rev-ghost',1,'snap-1','hash','internal-1.0','test',now())"),
                "parent_revision_id FK must reject unknown parent");
    }

    @Test
    void artifactPinRevisionFkRejectsUnknownRevision() {
        jdbc.update("INSERT INTO identity.tenant (id, name, status, created_at) VALUES ('ten-1','t','ACTIVE',now())");
        jdbc.update("INSERT INTO identity.project (id, tenant_id, name, created_at) VALUES ('proj-1','ten-1','p',now())");
        jdbc.update("INSERT INTO artifacts.artifact (id, tenant_id, content_digest, byte_length, media_type, artifact_kind, state, schema_version, created_at) "
                + "VALUES ('art-1','ten-1','" + "a".repeat(64) + "',100,'VIDEO','RENDER_MASTER','AVAILABLE',1,now())");
        assertForeignKeyViolation(
                () -> jdbc.update(
                        "INSERT INTO artifacts.artifact_pin (pin_id, tenant_id, revision_id, project_id, artifact_id, content_digest, pinned_at) "
                                + "VALUES ('pin-1','ten-1','rev-ghost','proj-1','art-1','" + "a".repeat(64) + "',now())"),
                "artifact_pin.revision_id FK must reject unknown revision");
    }

    @Test
    void artifactPinProjectFkRejectsUnknownProject() {
        jdbc.update("INSERT INTO identity.tenant (id, name, status, created_at) VALUES ('ten-1','t','ACTIVE',now())");
        jdbc.update("INSERT INTO identity.project (id, tenant_id, name, created_at) VALUES ('proj-1','ten-1','p',now())");
        jdbc.update("INSERT INTO timeline.timeline_snapshot (id, tenant_id, project_id, payload_json) "
                + "VALUES ('snap-1','ten-1','proj-1','{}')");
        jdbc.update("INSERT INTO timeline.timeline_revision (id, tenant_id, project_id, revision_number, snapshot_id, content_hash, schema_version, source, created_at) "
                + "VALUES ('rev-1','ten-1','proj-1',1,'snap-1','hash','internal-1.0','test',now())");
        jdbc.update("INSERT INTO artifacts.artifact (id, tenant_id, content_digest, byte_length, media_type, artifact_kind, state, schema_version, created_at) "
                + "VALUES ('art-1','ten-1','" + "a".repeat(64) + "',100,'VIDEO','RENDER_MASTER','AVAILABLE',1,now())");
        assertForeignKeyViolation(
                () -> jdbc.update(
                        "INSERT INTO artifacts.artifact_pin (pin_id, tenant_id, revision_id, project_id, artifact_id, content_digest, pinned_at) "
                                + "VALUES ('pin-1','ten-1','rev-1','proj-ghost','art-1','" + "a".repeat(64) + "',now())"),
                "artifact_pin composite ownership FKs must reject an unknown project/cross-project revision");
    }

    // ── D6: historical delete safety (media_stream RESTRICT) ──

    @Test
    void mediaAssetDeleteRejectedWhenStreamExists() {
        jdbc.update("INSERT INTO identity.tenant (id, name, status, created_at) VALUES ('ten-1','t','ACTIVE',now())");
        jdbc.update("INSERT INTO identity.project (id, tenant_id, name, created_at) VALUES ('proj-1','ten-1','p',now())");
        jdbc.update("INSERT INTO artifacts.artifact (id, tenant_id, project_id, content_digest, byte_length, media_type, artifact_kind, state, created_at) "
                + "VALUES ('ma-1','ten-1','proj-1','" + "a".repeat(64) + "',100,'VIDEO','RENDER_MASTER','AVAILABLE',now())");
        jdbc.update("INSERT INTO media.media_stream (id, artifact_id, stream_index, stream_kind, timebase_num, timebase_den) "
                + "VALUES ('ms-1','ma-1',0,'video',1,1)");
        assertSqlState(
                () -> jdbc.update("DELETE FROM artifacts.artifact WHERE id='ma-1'"),
                "canonical Artifact deletion is restricted while a media_stream exists (PG18 RESTRICT)","23001");
    }

    @Test
    void mediaAssetDeleteAllowedWhenNoStream() {
        jdbc.update("INSERT INTO identity.tenant (id, name, status, created_at) VALUES ('ten-1','t','ACTIVE',now())");
        jdbc.update("INSERT INTO identity.project (id, tenant_id, name, created_at) VALUES ('proj-1','ten-1','p',now())");
        jdbc.update("INSERT INTO artifacts.artifact (id, tenant_id, project_id, content_digest, byte_length, media_type, artifact_kind, state, created_at) "
                + "VALUES ('ma-1','ten-1','proj-1','" + "a".repeat(64) + "',100,'VIDEO','RENDER_MASTER','AVAILABLE',now())");
        assertDoesNotThrow(() -> jdbc.update("DELETE FROM artifacts.artifact WHERE id='ma-1'"));
    }

    // ── render_job FK (execution state same-context) ──

    @Test
    void renderJobProjectFkRejectsUnknownProject() {
        jdbc.update("INSERT INTO identity.tenant (id, name, status, created_at) VALUES ('ten-1','t','ACTIVE',now())");
        jdbc.update("INSERT INTO identity.project (id, tenant_id, name, created_at) VALUES ('proj-1','ten-1','p',now())");
        jdbc.update("INSERT INTO timeline.timeline_snapshot (id, tenant_id, project_id, payload_json) "
                + "VALUES ('snap-1','ten-1','proj-1','{}')");
        assertForeignKeyViolation(
                () -> jdbc.update(
                        "INSERT INTO render.render_job (id, project_id, tenant_id, timeline_snapshot_id, profile, status, created_at, initiator_type, initiator_id, initiator_tenant_id) "
                                + "VALUES ('job-1','proj-ghost','ten-1','snap-1','default','PENDING',now(),'USER','test-principal-p1','ten-1')"),
                "render_job.project_id FK must reject unknown project");
        assertDoesNotThrow(() -> jdbc.update(
                "INSERT INTO render.render_job (id, project_id, tenant_id, timeline_snapshot_id, profile, status, created_at, initiator_type, initiator_id, initiator_tenant_id) "
                        + "VALUES ('job-1','proj-1','ten-1','snap-1','default','PENDING',now(),'USER','test-principal-p1','ten-1')"));
    }
}
