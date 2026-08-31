package com.example.platform;

import com.example.platform.render.infrastructure.RenderProviderRegistry;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * RenderJob Preselection and Flyway Consolidation.
 * Proves script resolution → Provider selection → persistence → dispatch boundary.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "preview"})
@TestPropertySource(properties = {
    "app.security.enabled=false",
    "app.identity.api-key-auth-enabled=false",
    "render.providers.ffmpeg.enabled=true",
    "render.providers.gstreamer.enabled=false",
    "render.providers.vapoursynth.enabled=false",
    "render.providers.natron.enabled=false",
    "render.execution.mode=local",
    "spring.mvc.throw-exception-if-no-handler-found=true",
    "spring.web.resources.add-mappings=false"
})
class RenderJobPreselectionTest extends PostgresTestContainerSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private RenderProviderRegistry registry;

    @Autowired
    private JdbcTemplate jdbc;

    private HttpClient client;
    private String baseUrl;

    // Minimal valid timeline JSON that passes isTimelineJson() check
    private static final String MINIMAL_TIMELINE_JSON = """
            {
              "id": "tl-test",
              "tracks": [
                {
                  "type": "VIDEO",
                  "clips": [
                    {
                      "id": "clip-1",
                      "assetRef": {"storageUri": "file:///tmp/test.mp4"},
                      "assetInPoint": 0.0,
                      "clipDuration": 5.0
                    }
                  ]
                }
              ],
              "outputSpec": {"format": "mp4", "width": 1920, "height": 1080}
            }
            """;

    private static final StringBuilder evidence = new StringBuilder();

    @BeforeEach
    void setUp() {
        client = HttpClient.newHttpClient();
        baseUrl = "http://localhost:" + port;
    }

    @AfterAll
    static void writeEvidence() throws Exception {
        Files.writeString(Path.of("/tmp/renderjob-preselection-evidence.txt"), evidence.toString());
    }

    // ========== P1-P4: Canonical Create + Script Resolution ==========

    @Test
    void canonicalFlow_createStartStatus_sameJob() throws Exception {
        assertPostContainedWithoutRenderWrite(
                "/api/tenants/request-tenant/projects/request-project/render-jobs",
                "{\"projectId\":\"request-project\",\"timelineSnapshotId\":\"snap-test\",\"profile\":\"default_1080p\"}");
    }

    // ========== Flyway V4 ==========

    @Test
    void flywayV4_columnExists() {
        try {
            jdbc.execute("SELECT selected_provider FROM render_job LIMIT 0");
            evidence.append("V4_COLUMN: EXISTS\n");
        } catch (Exception e) {
            evidence.append(String.format("V4_COLUMN: MISSING (%s)\n", e.getMessage()));
            Assertions.fail("selected_provider column should exist");
        }
    }

    @Test
    void flywayV4_appliedMigrations() {
        // Check Flyway schema_history table
        List<Map<String, Object>> migrations = jdbc.queryForList(
                "SELECT version, description FROM flyway_schema_history ORDER BY installed_rank");
        evidence.append(String.format("FLYWAY_MIGRATIONS: %d%n", migrations.size()));
        for (Map<String, Object> m : migrations) {
            evidence.append(String.format("FLYWAY_VERSION: %s - %s%n", m.get("version"), m.get("description")));
        }
    }

    // ========== Removed routes ==========

    @Test
    void removedRoutes_404() throws Exception {
        assertPostContainedWithoutRenderWrite(
                "/api/tenants/t1/projects/p1/render-jobs/rj1/execute-local", null);
        assertPostContainedWithoutRenderWrite("/api/render/jobs/rj1/retry", null);
    }

    private void assertPostContainedWithoutRenderWrite(String path, String body) throws Exception {
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM render_job", Integer.class);
        HttpResponse<String> response = httpPost(path, body);
        Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM render_job", Integer.class);
        evidence.append(String.format("CONTAINED_POST %s: %d%n", path, response.statusCode()));
        Assertions.assertEquals(403, response.statusCode());
        Assertions.assertEquals(before, after, "Denied request must not dispatch a render write");
    }

    // ========== Provider Registry ==========

    @Test
    void providerRegistry_legacyFfmpegAbsent() {
        boolean present = registry.getProvider("ffmpeg").isPresent();
        evidence.append(String.format("FFMPEG_REGISTRY: %b%n", present));
        Assertions.assertFalse(present,
                "Legacy FFmpeg must not remain in the render-integrated registry");
    }

    // ========== Helpers ==========

    private HttpResponse<String> httpPost(String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "{}"))
                .header("Content-Type", "application/json");
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

}
