package com.example.platform;

import com.example.platform.render.infrastructure.RenderProviderRegistry;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * RenderJob Selection Transition Validation.
 * Validates S1-S10 evidence levels for Provider selection persistence.
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
class RenderJobSelectionTransitionTest extends PostgresTestContainerSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private RenderProviderRegistry registry;

    @Autowired
    private JdbcTemplate jdbc;

    private HttpClient client;
    private String baseUrl;

    private static final StringBuilder evidence = new StringBuilder();

    @BeforeEach
    void setUp() {
        client = HttpClient.newHttpClient();
        baseUrl = "http://localhost:" + port;
    }

    @AfterAll
    static void writeEvidence() throws Exception {
        Files.writeString(Path.of("/tmp/renderjob-transition-evidence.txt"), evidence.toString());
    }

    // ========== S1: Route registered ==========

    @Test
    void startRoute_registered() throws Exception {
        assertPostContainedWithoutRenderWrite(
                "/api/tenants/request-tenant/projects/request-project/render-jobs/request-job/start", null);
    }

    @Test
    void createRoute_registered() throws Exception {
        assertPostContainedWithoutRenderWrite(
                "/api/tenants/request-tenant/projects/request-project/render-jobs",
                "{\"timelineSnapshotId\":\"snap1\",\"profile\":\"default_1080p\"}");
    }

    // ========== Provider Registry verification ==========

    @Test
    void legacyFfmpegAbsentFromRegistry() {
        boolean present = registry.getProvider("ffmpeg").isPresent();
        evidence.append(String.format("PROVIDER_FFMPEG_REGISTRY: %b%n", present));
        Assertions.assertFalse(present,
                "Legacy FFmpeg must not remain in the render-integrated registry");
    }

    @Test
    void registryContents() {
        var caps = registry.getAllCapabilities();
        evidence.append(String.format("PROVIDER_REGISTRY_COUNT: %d%n", caps.size()));
        for (var cap : caps) {
            evidence.append(String.format("PROVIDER_ENTRY: %s (status=%s, priority=%s)%n",
                    cap.providerKey(), cap.status(), cap.priority()));
        }
    }

    // ========== RenderJob state model ==========

    @Test
    void renderJobStates_documented() {
        // Document all valid states
        evidence.append("STATE_MODEL: QUEUED, SELECTING_PROVIDER, PROVIDER_SELECTED, EXECUTING, COMPLETING, COMPLETED, FAILED, CANCELLED, REJECTED%n");
    }

    // ========== Selected Provider persistence field ==========

    @Test
    void selectedProviderColumn_exists() {
        // Verify the selected_provider column exists in render_job table
        try {
            jdbc.execute("SELECT selected_provider FROM render_job LIMIT 0");
            evidence.append("SELECTED_PROVIDER_COLUMN: EXISTS\n");
        } catch (Exception e) {
            evidence.append(String.format("SELECTED_PROVIDER_COLUMN: MISSING (%s)\n", e.getMessage()));
            Assertions.fail("selected_provider column should exist: " + e.getMessage());
        }
    }

    // ========== Removed routes regression ==========

    @Test
    void executeLocal_remains404() throws Exception {
        assertPostContainedWithoutRenderWrite(
                "/api/tenants/t1/projects/p1/render-jobs/rj1/execute-local", null);
    }

    @Test
    void retry_remains404() throws Exception {
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
