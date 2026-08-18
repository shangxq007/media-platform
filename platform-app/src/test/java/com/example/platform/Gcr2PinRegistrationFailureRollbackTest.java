package com.example.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.artifact.app.ArtifactPinService;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;

/**
 * GCR-2 T7 HARD ATOMICITY PROOF (PIN_REGISTRATION_FAILURE_ROLLBACK_V1).
 *
 * Architecture requires: Artifact validation succeeds, revision persistence
 * begins, artifact_pin persistence FAILS -> the WHOLE transaction rolls back:
 * no revision row, no partial pin row, no current-revision advance.
 *
 * The @Transactional annotation alone is NOT proof — this test forces a real
 * pin-registration failure inside the Spring-managed transaction boundary and
 * asserts the DB shows zero committed effects.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "preview"})
class Gcr2PinRegistrationFailureRollbackTest extends PostgresTestContainerSupport {

    @Autowired
    private com.example.platform.timeline.app.TimelineRevisionService revisionService;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private ArtifactPinService artifactPinService;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    private void seedProject(String projectId, String tenantId) {
        jdbc.update("INSERT INTO tenant (id, name, status, created_at) VALUES (?, 't7', 'ACTIVE', ?)",
                tenantId, java.sql.Timestamp.from(Instant.now()));
        jdbc.update("INSERT INTO project (id, tenant_id, name, created_at) VALUES (?, ?, 't7-project', ?)",
                projectId, tenantId, java.sql.Timestamp.from(Instant.now()));
    }

    private void seedArtifact(String artifactId, String tenantId) {
        jdbc.update("INSERT INTO artifact (id, tenant_id, content_digest, byte_length, media_type, "
                        + "artifact_kind, state, schema_version, created_at) "
                        + "VALUES (?, ?, ?, 512, 'VIDEO', 'RENDER_MASTER', 'AVAILABLE', 1, ?)",
                artifactId, tenantId, ContentDigest.sha256("d".repeat(64)).canonicalValue(),
                java.sql.Timestamp.from(Instant.now()));
    }

    private String pinJson(String artifactId, String digest) {
        return "{\"schemaVersion\":1,\"id\":\"tl-t7\",\"revision\":1,\"composition\":{\"tracks\":["
                + "{\"id\":\"t1\",\"type\":\"VIDEO\",\"clips\":[{\"id\":\"c1\",\"assetId\":\"ast-t7\","
                + "\"timelineRange\":{\"start\":{\"frame\":0,\"rate\":{\"num\":30,\"den\":1}},\"duration\":{\"frame\":30,\"rate\":{\"num\":30,\"den\":1}}},"
                + "\"sourceRange\":{\"start\":{\"frame\":0,\"rate\":{\"num\":30,\"den\":1}},\"duration\":{\"frame\":30,\"rate\":{\"num\":30,\"den\":1}}},"
                + "\"sourceBinding\":{"
                + "\"sourceKind\":\"MEDIA_STREAM\",\"mediaStreamId\":\"stream-1\",\"mediaAssetId\":\"ast-t7\","
                + "\"artifactId\":\"" + artifactId + "\",\"contentDigest\":{\"algorithm\":\"SHA256\",\"value\":\""
                + digest + "\"}}}]}]}}";
    }

    @Test
    void pinRegistrationFailureRollsBackWholeRevisionTransaction() {
        String tenant = "ten-t7";
        String artifactId = "art-t7";
        String projectId = "prj-t7";
        seedProject(projectId, tenant);
        seedArtifact(artifactId, tenant);
        // The artifact's content digest on the canonical row.
        String digest = ContentDigest.sha256("d".repeat(64)).canonicalValue();
        com.example.platform.shared.web.TenantContext.set(tenant);

        // Force the pin-registration step (which runs AFTER revision insert in the
        // SAME @Transactional method) to fail hard.
        doAnswer(invocation -> {
            throw new IllegalStateException("INJECTED_PIN_PERSISTENCE_FAILURE");
        }).when(artifactPinService)
                .registerRevisionPins(any(), any(), any(), anyList());

        int revisionsBefore = jdbc.queryForObject(
                "SELECT count(*) FROM timeline_revision WHERE project_id = ?", Integer.class, projectId);

        assertThrows(IllegalStateException.class,
                () -> revisionService.recordRevision(projectId, tenant, pinJson(artifactId, digest),
                        "sync", null, null, "t7 pin failure"));

        // The whole transaction must have rolled back: no revision row, no pin rows.
        int revisionsAfter = jdbc.queryForObject(
                "SELECT count(*) FROM timeline_revision WHERE project_id = ?", Integer.class, projectId);
        int pinsAfter = jdbc.queryForObject(
                "SELECT count(*) FROM artifact_pin WHERE project_id = ?", Integer.class, projectId);

        assertEquals(revisionsBefore, revisionsAfter,
                "PIN_FAILURE_REVISION_ROW_COUNT must be 0 (revision rolled back)");
        assertEquals(0, pinsAfter, "PIN_FAILURE_ARTIFACT_PIN_PARTIAL_WRITE_COUNT must be 0");
        // Current revision pointer must not have advanced (no committed revision exists).
        Integer currentPointer = jdbc.queryForObject(
                "SELECT count(*) FROM timeline_revision_ref WHERE project_id = ?", Integer.class, projectId);
        assertTrue(currentPointer == null || currentPointer == 0,
                "PIN_FAILURE_CURRENT_REVISION_ADVANCE_COUNT must be 0");
    }
}
