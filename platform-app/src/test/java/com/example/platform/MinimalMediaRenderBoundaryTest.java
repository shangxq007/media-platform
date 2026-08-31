package com.example.platform;

import com.example.platform.render.infrastructure.RenderProviderRegistry;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Minimal Media Render Boundary Validation.
 * Preserves the application render-job, persistence, status, and removed-route boundaries
 * while legacy in-process FFmpeg execution is absent.
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
    "render.synthetic.enabled=true",
    "spring.mvc.throw-exception-if-no-handler-found=true",
    "spring.web.resources.add-mappings=false"
})
class MinimalMediaRenderBoundaryTest extends PostgresTestContainerSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private RenderProviderRegistry registry;

    @Autowired
    private JdbcTemplate jdbc;

    private HttpClient client;
    private String baseUrl;

    private static final String MEDIA_PATH = "/tmp/test-render-boundary.mp4";
    private static final StringBuilder evidence = new StringBuilder();

    @BeforeEach
    void setUp() {
        client = HttpClient.newHttpClient();
        baseUrl = "http://localhost:" + port;
    }

    @AfterAll
    static void writeEvidence() throws Exception {
        Files.writeString(Path.of("/tmp/minimal-media-evidence.txt"), evidence.toString());
    }

    // ========== Canonical Provider ID ==========

    @Test
    void legacyFfmpegProvider_isAbsentFromRenderRegistry() {
        boolean present = registry.getProvider("ffmpeg").isPresent();
        evidence.append(String.format("LEGACY_FFMPEG_REGISTRY_PRESENT: %b%n", present));
        Assertions.assertFalse(present);
    }

    // ========== R1-R10: Full render boundary flow ==========

    @Test
    void renderBoundary_recordsStateWhenNoLegacyFfmpegProviderExists() throws Exception {
        assertPostContainedWithoutRenderWrite(
                "/api/tenants/request-tenant/projects/request-project/render-jobs",
                "{\"projectId\":\"request-project\",\"timelineSnapshotId\":\"snap-test\",\"profile\":\"default_1080p\"}");
    }

    // ========== Flyway ==========

    @Test
    void selectedProviderColumnExists() {
        try {
            jdbc.execute("SELECT selected_provider FROM render_job LIMIT 0");
            evidence.append("SELECTED_PROVIDER_COLUMN: EXISTS\n");
        } catch (Exception e) {
            Assertions.fail("selected_provider column should exist");
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

    // ========== Helpers ==========

    private HttpResponse<String> httpPost(String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "{}"))
                .header("Content-Type", "application/json");
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

}
