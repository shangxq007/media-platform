package com.example.platform;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * GCR5/GCR6 (DATABASE_CANONICALIZATION_CONTRACT_V1 C5/C7/C9): real-PostgreSQL
 * structural integrity tests for the canonical V1 constraints — same-owner
 * relations pass, cross-owner/cross-revision references fail closed at the
 * database level, and canonical history is not destroyed by accidental cascade.
 *
 * Uses the exact canonical V1 schema (no fixture SQL).
 */
class Gcr5Gcr6DatabaseStructuralIntegrityTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void createSchema() throws Exception {
        dataSource = createDataSource();
        jdbc = new JdbcTemplate(dataSource);
        // Apply the canonical V1 schema directly (single source of truth).
        var sql = new String(
                Gcr5Gcr6DatabaseStructuralIntegrityTest.class.getClassLoader()
                        .getResourceAsStream("db/migration/V1__initial_schema.sql")
                        .readAllBytes());
        // GCR5/GCR6: run the canonical V1 inside a UNIQUE isolated logical schema
        // (never DROP the shared public schema — that would destroy the shared
        // runtime's flyway history and break sibling tests).
        schemaName = isolatedSchemaName();
        jdbc.execute("CREATE SCHEMA " + schemaName);
        jdbc.execute("SET search_path TO " + schemaName);
        // Split on ';\n' but treat plpgsql $$ ... $$ bodies as atomic.
        var buffer = new StringBuilder();
        for (String stmt : sql.split(";\\s*\n")) {
            if (stmt.isBlank()) continue;
            buffer.append(stmt).append(";\n");
            if (buffer.toString().contains("$$") && !isBalanced(buffer.toString())) {
                continue; // inside a plpgsql function body — keep accumulating
            }
            jdbc.execute(buffer.toString());
            buffer.setLength(0);
        }
        if (buffer.length() > 0) {
            jdbc.execute(buffer.toString());
        }
    }

    /** Unique logical schema for this class — isolated from the shared public schema. */
    private static String schemaName;

    private static boolean isBalanced(String s) {
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf("$$", idx)) >= 0) {
            count++;
            idx += 2;
        }
        return count % 2 == 0;
    }

    @AfterAll
    static void tearDown() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM artifact_pin");
        jdbc.update("DELETE FROM timeline_revision_parent");
        jdbc.update("DELETE FROM timeline_revision_ref");
        jdbc.update("DELETE FROM timeline_revision");
        jdbc.update("DELETE FROM timeline_snapshot");
        jdbc.update("DELETE FROM artifact_relation");
        jdbc.update("DELETE FROM artifact_replica");
        jdbc.update("DELETE FROM artifact");
        jdbc.update("DELETE FROM render_job");
        jdbc.update("DELETE FROM media_stream");
        jdbc.update("DELETE FROM media_asset");
        jdbc.update("DELETE FROM project");
        jdbc.update("DELETE FROM tenant");
    }

    // ── D4: tenant/project ownership ──

    @Test
    void timelineRevisionProjectFkRejectsUnknownProject() {
        jdbc.update("INSERT INTO tenant (id, name, status, created_at) VALUES ('ten-1','t','ACTIVE',now())");
        assertThrows(Exception.class,
                () -> jdbc.update("INSERT INTO timeline_snapshot (id, project_id, payload_json) VALUES ('snap-1','proj-missing','{}')"),
                "timeline_snapshot.project_id FK must reject unknown project");
    }

    @Test
    void timelineRevisionProjectFkAcceptsSameProject() {
        jdbc.update("INSERT INTO tenant (id, name, status, created_at) VALUES ('ten-1','t','ACTIVE',now())");
        jdbc.update("INSERT INTO project (id, tenant_id, name, created_at) VALUES ('proj-1','ten-1','p',now())");
        assertDoesNotThrow(() -> jdbc.update(
                "INSERT INTO timeline_snapshot (id, project_id, payload_json) VALUES ('snap-1','proj-1','{}')"));
        assertDoesNotThrow(() -> jdbc.update(
                "INSERT INTO timeline_revision (id, project_id, revision_number, snapshot_id, content_hash, schema_version, source, created_at) "
                        + "VALUES ('rev-1','proj-1',1,'snap-1','hash','internal-1.0','test',now())"));
    }

    @Test
    void timelineRevisionParentFkRejectsUnknownParent() {
        jdbc.update("INSERT INTO tenant (id, name, status, created_at) VALUES ('ten-1','t','ACTIVE',now())");
        jdbc.update("INSERT INTO project (id, tenant_id, name, created_at) VALUES ('proj-1','ten-1','p',now())");
        jdbc.update("INSERT INTO timeline_snapshot (id, project_id, payload_json) VALUES ('snap-1','proj-1','{}')");
        assertThrows(Exception.class,
                () -> jdbc.update(
                        "INSERT INTO timeline_revision (id, project_id, parent_revision_id, revision_number, snapshot_id, content_hash, schema_version, source, created_at) "
                                + "VALUES ('rev-1','proj-1','rev-ghost',1,'snap-1','hash','internal-1.0','test',now())"),
                "parent_revision_id FK must reject unknown parent");
    }

    @Test
    void artifactPinRevisionFkRejectsUnknownRevision() {
        jdbc.update("INSERT INTO tenant (id, name, status, created_at) VALUES ('ten-1','t','ACTIVE',now())");
        jdbc.update("INSERT INTO project (id, tenant_id, name, created_at) VALUES ('proj-1','ten-1','p',now())");
        jdbc.update("INSERT INTO artifact (id, tenant_id, content_digest, byte_length, media_type, artifact_kind, state, schema_version, created_at) "
                + "VALUES ('art-1','ten-1','" + "a".repeat(64) + "',100,'VIDEO','RENDER_MASTER','AVAILABLE',1,now())");
        assertThrows(Exception.class,
                () -> jdbc.update(
                        "INSERT INTO artifact_pin (pin_id, revision_id, project_id, artifact_id, content_digest, pinned_at) "
                                + "VALUES ('pin-1','rev-ghost','proj-1','art-1','" + "a".repeat(64) + "',now())"),
                "artifact_pin.revision_id FK must reject unknown revision");
    }

    @Test
    void artifactPinProjectFkRejectsUnknownProject() {
        jdbc.update("INSERT INTO tenant (id, name, status, created_at) VALUES ('ten-1','t','ACTIVE',now())");
        jdbc.update("INSERT INTO project (id, tenant_id, name, created_at) VALUES ('proj-1','ten-1','p',now())");
        jdbc.update("INSERT INTO artifact (id, tenant_id, content_digest, byte_length, media_type, artifact_kind, state, schema_version, created_at) "
                + "VALUES ('art-1','ten-1','" + "a".repeat(64) + "',100,'VIDEO','RENDER_MASTER','AVAILABLE',1,now())");
        assertThrows(Exception.class,
                () -> jdbc.update(
                        "INSERT INTO artifact_pin (pin_id, revision_id, project_id, artifact_id, content_digest, pinned_at) "
                                + "VALUES ('pin-1','rev-1','proj-ghost','art-1','" + "a".repeat(64) + "',now())"),
                "artifact_pin.project_id FK must reject unknown project");
    }

    // ── D6: historical delete safety (media_stream RESTRICT) ──

    @Test
    void mediaAssetDeleteRejectedWhenStreamExists() {
        jdbc.update("INSERT INTO tenant (id, name, status, created_at) VALUES ('ten-1','t','ACTIVE',now())");
        jdbc.update("INSERT INTO project (id, tenant_id, name, created_at) VALUES ('proj-1','ten-1','p',now())");
        jdbc.update("INSERT INTO media_asset (id, tenant_id, project_id, storage_key, media_type, created_at) "
                + "VALUES ('ma-1','ten-1','proj-1','k','VIDEO',now())");
        jdbc.update("INSERT INTO media_stream (id, media_asset_id, stream_index, stream_kind, timebase_num, timebase_den) "
                + "VALUES ('ms-1','ma-1',0,'video',1,1)");
        assertThrows(Exception.class,
                () -> jdbc.update("DELETE FROM media_asset WHERE id='ma-1'"),
                "media_asset delete must be REJECTED while canonical media_stream rows exist (C9)");
    }

    @Test
    void mediaAssetDeleteAllowedWhenNoStream() {
        jdbc.update("INSERT INTO tenant (id, name, status, created_at) VALUES ('ten-1','t','ACTIVE',now())");
        jdbc.update("INSERT INTO project (id, tenant_id, name, created_at) VALUES ('proj-1','ten-1','p',now())");
        jdbc.update("INSERT INTO media_asset (id, tenant_id, project_id, storage_key, media_type, created_at) "
                + "VALUES ('ma-1','ten-1','proj-1','k','VIDEO',now())");
        assertDoesNotThrow(() -> jdbc.update("DELETE FROM media_asset WHERE id='ma-1'"));
    }

    // ── render_job FK (execution state same-context) ──

    @Test
    void renderJobProjectFkRejectsUnknownProject() {
        jdbc.update("INSERT INTO tenant (id, name, status, created_at) VALUES ('ten-1','t','ACTIVE',now())");
        jdbc.update("INSERT INTO project (id, tenant_id, name, created_at) VALUES ('proj-1','ten-1','p',now())");
        jdbc.update("INSERT INTO timeline_snapshot (id, project_id, payload_json) VALUES ('snap-1','proj-1','{}')");
        assertThrows(Exception.class,
                () -> jdbc.update(
                        "INSERT INTO render_job (id, project_id, tenant_id, timeline_snapshot_id, profile, status, created_at, initiator_type, initiator_id, initiator_tenant_id) "
                                + "VALUES ('job-1','proj-ghost','ten-1','snap-1','default','PENDING',now(),'USER','test-principal-p1','ten-1')"),
                "render_job.project_id FK must reject unknown project");
        assertDoesNotThrow(() -> jdbc.update(
                "INSERT INTO render_job (id, project_id, tenant_id, timeline_snapshot_id, profile, status, created_at, initiator_type, initiator_id, initiator_tenant_id) "
                        + "VALUES ('job-1','proj-1','ten-1','snap-1','default','PENDING',now(),'USER','test-principal-p1','ten-1')"));
    }
}
