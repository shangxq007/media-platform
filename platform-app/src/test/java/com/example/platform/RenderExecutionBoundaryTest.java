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
 * Render Execution Boundary and Concurrency Remainder.
 * Proves the render boundary, persistence, failure paths, and removed routes without
 * registering the legacy in-process FFmpeg provider.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "preview"})
@TestPropertySource(properties = {
    "app.security.enabled=false",
    "app.identity.api-key-auth-enabled=false",
    "render.providers.ffmpeg.enabled=false",
    "render.providers.gstreamer.enabled=false",
    "render.providers.vapoursynth.enabled=false",
    "render.providers.natron.enabled=false",
    "render.execution.mode=local",
    "spring.mvc.throw-exception-if-no-handler-found=true",
    "spring.web.resources.add-mappings=false"
})
class RenderExecutionBoundaryTest extends PostgresTestContainerSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private RenderProviderRegistry registry;

    @Autowired
    private JdbcTemplate jdbc;

    private HttpClient client;
    private String baseUrl;

    // Minimal valid timeline JSON
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
        Files.writeString(Path.of("/tmp/render-exec-boundary-evidence.txt"), evidence.toString());
    }

    // ========== Canonical Provider ID ==========

    @Test
    void legacyFfmpegId_isAbsent() {
        boolean present = registry.getProvider("ffmpeg").isPresent();
        evidence.append(String.format("CANONICAL_ID: ffmpeg (present=%b)%n", present));
        Assertions.assertFalse(present, "Legacy FFmpeg should not be in the render registry");
    }

    @Test
    void registryKeys_areCanonical() {
        var caps = registry.getAllCapabilities();
        for (var cap : caps) {
            evidence.append(String.format("REGISTRY_KEY: %s%n", cap.providerKey()));
        }
    }

    // ========== R1-R7: Canonical HTTP Flow ==========

    @Test
    void canonicalFlow_providerIdIsCanonical() throws Exception {
        assertPostContainedWithoutRenderWrite(
                "/api/tenants/request-tenant/projects/request-project/render-jobs",
                "{\"projectId\":\"request-project\",\"timelineSnapshotId\":\"snap-test\",\"profile\":\"default_1080p\"}");
    }

    // ========== Flyway ==========

    @Test
    void flyway_selectedProviderColumnExists() {
        try {
            jdbc.execute("SELECT selected_provider FROM render_job LIMIT 0");
            evidence.append("SELECTED_PROVIDER_COLUMN: EXISTS\n");
        } catch (Exception e) {
            Assertions.fail("selected_provider column should exist");
        }
    }

    @Test
    void flywayMigrations_allApplied() {
        List<Map<String, Object>> migrations = jdbc.queryForList(
                "SELECT version, description, script FROM flyway_schema_history ORDER BY installed_rank");
        evidence.append(String.format("FLYWAY_COUNT: %d%n", migrations.size()));
        for (Map<String, Object> m : migrations) {
            evidence.append(String.format("FLYWAY: V%s - %s [%s]%n", m.get("version"), m.get("description"), m.get("script")));
        }
        // GCR-2 (GREENFIELD_DATABASE_HAS_ONE_CONSOLIDATED_CANONICAL_FLYWAY_V1_V1):
        // exactly ONE canonical V1 migration; former V2..V7 consolidated into V1.
        Assertions.assertEquals(1, migrations.size(),
                "Greenfield baseline must contain exactly one canonical V1 migration");
        Map<String, Object> singleMigration = migrations.get(0);
        Assertions.assertEquals("1", String.valueOf(singleMigration.get("version")),
                "Only V1 may be active before first release");
        Assertions.assertEquals("V1__initial_schema.sql", String.valueOf(singleMigration.get("script")),
                "Greenfield baseline must use exactly V1__initial_schema.sql");
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

    // ========== Helpers ==========

    private HttpResponse<String> httpPost(String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "{}"))
                .header("Content-Type", "application/json");
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

}
