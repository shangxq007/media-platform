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
        // Verify the canonical start route exists (not 404)
        HttpResponse<String> response = httpPost(
                "/api/v1/tenants/t1/projects/p1/render-jobs/nonexistent/start", null);
        evidence.append(String.format("S1_START_ROUTE: %d%n", response.statusCode()));
        // Should not be 404 (route exists), but may be 400/404 for invalid resource
        Assertions.assertNotEquals(404, response.statusCode(),
                "Start route should be registered");
    }

    @Test
    void createRoute_registered() throws Exception {
        HttpResponse<String> response = httpPost(
                "/api/v1/tenants/t1/projects/p1/render-jobs",
                "{\"timelineSnapshotId\":\"snap1\",\"profile\":\"default_1080p\"}");
        evidence.append(String.format("S1_CREATE_ROUTE: %d%n", response.statusCode()));
        Assertions.assertNotEquals(404, response.statusCode(),
                "Create route should be registered");
    }

    // ========== Provider Registry verification ==========

    @Test
    void ffmpegInRegistry() {
        boolean present = registry.getProvider("ffmpeg").isPresent();
        evidence.append(String.format("PROVIDER_FFMPEG_REGISTRY: %b%n", present));
        Assertions.assertTrue(present, "FFmpeg should be in Registry");
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
        HttpResponse<String> response = httpPost(
                "/api/v1/tenants/t1/projects/p1/render-jobs/rj1/execute-local", null);
        evidence.append(String.format("REMOVED_EXECUTELocal: %d%n", response.statusCode()));
        Assertions.assertEquals(404, response.statusCode(), "execute-local should remain 404");
    }

    @Test
    void retry_remains404() throws Exception {
        HttpResponse<String> response = httpPost(
                "/api/v1/render/jobs/rj1/retry", null);
        evidence.append(String.format("REMOVED_RETRY: %d%n", response.statusCode()));
        Assertions.assertEquals(404, response.statusCode(), "retry should remain 404");
    }

    // ========== Helpers ==========

    private HttpResponse<String> httpPost(String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "{}"))
                .header("Content-Type", "application/json");
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> httpGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
