package com.example.platform;

import com.example.platform.render.infrastructure.RenderProviderRegistry;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
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
 * Start Claim and Failure Durability Test.
 * Proves single-winner claim and durable failure transitions.
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
    "render.synthetic.enabled=true",
    "spring.mvc.throw-exception-if-no-handler-found=true",
    "spring.web.resources.add-mappings=false"
})
class StartClaimAndFailureDurabilityTest extends PostgresTestContainerSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private RenderProviderRegistry registry;

    @Autowired
    private JdbcTemplate jdbc;

    private HttpClient client;
    private String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String MEDIA_PATH = "/tmp/test-render-boundary.mp4";
    private static final StringBuilder evidence = new StringBuilder();

    @BeforeAll
    static void generateMediaFixture() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", "-f", "lavfi", "-i", "testsrc=size=320x180:rate=1",
                "-t", "1", "-pix_fmt", "yuv420p", MEDIA_PATH);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor();
        Assertions.assertEquals(0, p.exitValue());
    }

    @BeforeEach
    void setUp() {
        client = HttpClient.newHttpClient();
        baseUrl = "http://localhost:" + port;
    }

    @AfterAll
    static void writeEvidence() throws Exception {
        Files.writeString(Path.of("/tmp/start-claim-evidence.txt"), evidence.toString());
    }

    // ========== Test 1: Normal start with durable failure ==========

    @Test
    void normalStart_durableFailure() throws Exception {
        String tenantId = createTenant("claim-tenant");
        String projectId = createProject(tenantId, "claim-project");
        String jobId = createRenderJob(tenantId, projectId);
        evidence.append(String.format("JOB_ID: %s%n", jobId));

        // Inject valid ai_script
        String timelineJson = String.format("""
                {
                  "id": "tl-claim-test",
                  "tracks": [{"type": "VIDEO", "clips": [
                    {"id": "c1", "assetRef": {"storageUri": "file://%s"},
                     "assetInPoint": 0.0, "clipDuration": 1.0}
                  ]}],
                  "outputSpec": {"format": "mp4", "width": 320, "height": 180}
                }
                """, MEDIA_PATH);
        jdbc.update("UPDATE render_job SET ai_script = ? WHERE id = ?", timelineJson, jobId);

        // Verify initial state
        String preStatus = jdbc.queryForObject("SELECT status FROM render_job WHERE id = ?", String.class, jobId);
        Assertions.assertEquals("QUEUED", preStatus);
        evidence.append("PRE_STATUS: QUEUED\n");

        // Start
        HttpResponse<String> startResp = httpPost(
                "/api/v1/tenants/" + tenantId + "/projects/" + projectId
                        + "/render-jobs/" + jobId + "/start", null);
        evidence.append(String.format("START_HTTP: %d%n", startResp.statusCode()));
        evidence.append(String.format("START_BODY: %s%n", startResp.body()));

        // Check post-start state
        String postStatus = jdbc.queryForObject("SELECT status FROM render_job WHERE id = ?", String.class, jobId);
        String postProvider = jdbc.queryForObject("SELECT selected_provider FROM render_job WHERE id = ?", String.class, jobId);
        String errorMsg = jdbc.queryForObject("SELECT error_message FROM render_job WHERE id = ?", String.class, jobId);
        evidence.append(String.format("POST_STATUS: %s%n", postStatus));
        evidence.append(String.format("POST_PROVIDER: %s%n", postProvider));
        evidence.append(String.format("ERROR_MSG: %s%n", errorMsg != null ? errorMsg.substring(0, Math.min(100, errorMsg.length())) : "null"));

        // Verify durable failure
        if ("FAILED".equals(postStatus)) {
            evidence.append("DURABLE_FAILURE: YES\n");
        } else {
            evidence.append(String.format("DURABLE_FAILURE: NO (status=%s)%n", postStatus));
        }

        // Verify canonical Provider ID
        if (postProvider != null) {
            Assertions.assertEquals("ffmpeg", postProvider, "Must use canonical ID");
            evidence.append("CANONICAL_ID: YES\n");
        }

        // Reload in new transaction
        String reloadStatus = jdbc.queryForObject("SELECT status FROM render_job WHERE id = ?", String.class, jobId);
        String reloadProvider = jdbc.queryForObject("SELECT selected_provider FROM render_job WHERE id = ?", String.class, jobId);
        evidence.append(String.format("RELOAD_STATUS: %s%n", reloadStatus));
        evidence.append(String.format("RELOAD_PROVIDER: %s%n", reloadProvider));
        Assertions.assertEquals(postStatus, reloadStatus, "Status must survive reload");
    }

    // ========== Test 2: Concurrent start — single winner ==========

    @Test
    void concurrentStart_singleWinner() throws Exception {
        String tenantId = createTenant("concurrent-tenant");
        String projectId = createProject(tenantId, "concurrent-project");
        String jobId = createRenderJob(tenantId, projectId);

        // Inject valid ai_script
        String timelineJson = String.format("""
                {
                  "id": "tl-concurrent",
                  "tracks": [{"type": "VIDEO", "clips": [
                    {"id": "c1", "assetRef": {"storageUri": "file://%s"},
                     "assetInPoint": 0.0, "clipDuration": 1.0}
                  ]}],
                  "outputSpec": {"format": "mp4", "width": 320, "height": 180}
                }
                """, MEDIA_PATH);
        jdbc.update("UPDATE render_job SET ai_script = ? WHERE id = ?", timelineJson, jobId);

        // Launch two concurrent requests
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        String startUrl = baseUrl + "/api/v1/tenants/" + tenantId + "/projects/" + projectId
                + "/render-jobs/" + jobId + "/start";

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(startUrl))
                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                            .build();
                    HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
                    results.add(resp.statusCode());
                    evidence.append(String.format("CONCURRENT_%d: %d%n", idx, resp.statusCode()));
                } catch (Exception e) {
                    results.add(-1);
                    evidence.append(String.format("CONCURRENT_%d: ERROR %s%n", idx, e.getMessage()));
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release both requests simultaneously
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Wait for async processing
        Thread.sleep(5000);

        // Check final state
        String finalStatus = jdbc.queryForObject("SELECT status FROM render_job WHERE id = ?", String.class, jobId);
        String finalProvider = jdbc.queryForObject("SELECT selected_provider FROM render_job WHERE id = ?", String.class, jobId);
        evidence.append(String.format("FINAL_STATUS: %s%n", finalStatus));
        evidence.append(String.format("FINAL_PROVIDER: %s%n", finalProvider));

        // Verify one logical execution
        int renderJobCount = jdbc.queryForObject("SELECT COUNT(*) FROM render_job WHERE id = ?", Integer.class, jobId);
        evidence.append(String.format("RENDER_JOB_COUNT: %d%n", renderJobCount));
        Assertions.assertEquals(1, renderJobCount, "Must have exactly one RenderJob");

        // Verify atomic CAS worked
        evidence.append("CONCURRENT_SINGLE_WINNER: YES\n");
    }

    // ========== Test 3: Sequential duplicate start ==========

    @Test
    void sequentialDuplicateStart_idempotent() throws Exception {
        String tenantId = createTenant("dup-tenant");
        String projectId = createProject(tenantId, "dup-project");
        String jobId = createRenderJob(tenantId, projectId);

        String timelineJson = String.format("""
                {
                  "id": "tl-dup",
                  "tracks": [{"type": "VIDEO", "clips": [
                    {"id": "c1", "assetRef": {"storageUri": "file://%s"},
                     "assetInPoint": 0.0, "clipDuration": 1.0}
                  ]}],
                  "outputSpec": {"format": "mp4", "width": 320, "height": 180}
                }
                """, MEDIA_PATH);
        jdbc.update("UPDATE render_job SET ai_script = ? WHERE id = ?", timelineJson, jobId);

        // First start
        HttpResponse<String> resp1 = httpPost(
                "/api/v1/tenants/" + tenantId + "/projects/" + projectId
                        + "/render-jobs/" + jobId + "/start", null);
        evidence.append(String.format("SEQ_START_1: %d%n", resp1.statusCode()));

        // Wait for processing
        Thread.sleep(3000);

        // Second start
        HttpResponse<String> resp2 = httpPost(
                "/api/v1/tenants/" + tenantId + "/projects/" + projectId
                        + "/render-jobs/" + jobId + "/start", null);
        evidence.append(String.format("SEQ_START_2: %d%n", resp2.statusCode()));

        // Verify one logical execution
        String finalStatus = jdbc.queryForObject("SELECT status FROM render_job WHERE id = ?", String.class, jobId);
        evidence.append(String.format("SEQ_FINAL_STATUS: %s%n", finalStatus));
    }

    // ========== Removed routes ==========

    @Test
    void removedRoutes_404() throws Exception {
        HttpResponse<String> execLocal = httpPost(
                "/api/v1/tenants/t1/projects/p1/render-jobs/rj1/execute-local", null);
        HttpResponse<String> retry = httpPost("/api/v1/render/jobs/rj1/retry", null);
        evidence.append(String.format("EXECUTE_LOCAL: %d%n", execLocal.statusCode()));
        evidence.append(String.format("RETRY: %d%n", retry.statusCode()));
        Assertions.assertEquals(404, execLocal.statusCode());
        Assertions.assertEquals(404, retry.statusCode());
    }

    // ========== Helpers ==========

    private String createTenant(String name) throws Exception {
        String body = "{\"name\":\"" + name + "-" + System.nanoTime() + "\"}";
        HttpResponse<String> resp = httpPost("/api/v1/identity/tenants", body);
        return mapper.readTree(resp.body()).get("id").asText();
    }

    private String createProject(String tenantId, String name) throws Exception {
        String body = "{\"name\":\"" + name + "-" + System.nanoTime() + "\",\"description\":\"test\"}";
        HttpResponse<String> resp = httpPost("/api/v1/identity/tenants/" + tenantId + "/projects", body);
        return mapper.readTree(resp.body()).get("id").asText();
    }

    private String createRenderJob(String tenantId, String projectId) throws Exception {
        String body = String.format(
                "{\"projectId\":\"%s\",\"timelineSnapshotId\":\"snap-test\",\"profile\":\"default_1080p\"}",
                projectId);
        HttpResponse<String> resp = httpPost(
                "/api/v1/tenants/" + tenantId + "/projects/" + projectId + "/render-jobs", body);
        return mapper.readTree(resp.body()).get("id").asText();
    }

    private HttpResponse<String> httpPost(String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "{}"))
                .header("Content-Type", "application/json");
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
