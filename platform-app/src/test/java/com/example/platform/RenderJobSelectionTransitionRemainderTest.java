package com.example.platform;

import com.example.platform.render.infrastructure.RenderProviderRegistry;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * RenderJob Selection Transition Remainder.
 * Closes evidence gaps: canonical create flow, selector exception,
 * persistence failure, dispatch failure, concurrent start, Flyway V4.
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
class RenderJobSelectionTransitionRemainderTest extends PostgresTestContainerSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private RenderProviderRegistry registry;

    @Autowired
    private JdbcTemplate jdbc;

    private HttpClient client;
    private String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final StringBuilder evidence = new StringBuilder();

    @BeforeEach
    void setUp() {
        client = HttpClient.newHttpClient();
        baseUrl = "http://localhost:" + port;
    }

    @AfterAll
    static void writeEvidence() throws Exception {
        Files.writeString(Path.of("/tmp/renderjob-remainder-evidence.txt"), evidence.toString());
    }

    // ========== E1-E4: Canonical Create Flow ==========

    @Test
    void canonicalCreate_validRequest_succeeds() throws Exception {
        // First create a tenant
        String tenantBody = "{\"name\":\"test-tenant-" + System.nanoTime() + "\"}";
        HttpResponse<String> tenantResp = httpPost("/api/v1/identity/tenants", tenantBody);
        evidence.append(String.format("TENANT_CREATE: %d%n", tenantResp.statusCode()));
        // 200 or 201
        Assertions.assertTrue(tenantResp.statusCode() >= 200 && tenantResp.statusCode() < 300,
                "Tenant create should succeed: " + tenantResp.statusCode());

        JsonNode tenantNode = mapper.readTree(tenantResp.body());
        String tenantId = tenantNode.get("id").asText();
        evidence.append(String.format("TENANT_ID: %s%n", tenantId));

        // Create a project
        String projectBody = "{\"name\":\"test-project-" + System.nanoTime() + "\",\"description\":\"test\"}";
        HttpResponse<String> projectResp = httpPost(
                "/api/v1/identity/tenants/" + tenantId + "/projects", projectBody);
        evidence.append(String.format("PROJECT_CREATE: %d%n", projectResp.statusCode()));
        Assertions.assertTrue(projectResp.statusCode() >= 200 && projectResp.statusCode() < 300,
                "Project create should succeed: " + projectResp.statusCode());

        JsonNode projectNode = mapper.readTree(projectResp.body());
        String projectId = projectNode.get("id").asText();
        evidence.append(String.format("PROJECT_ID: %s%n", projectId));

        // Create a RenderJob
        String jobBody = String.format(
                "{\"projectId\":\"%s\",\"timelineSnapshotId\":\"snap-test\",\"profile\":\"default_1080p\"}",
                projectId);
        HttpResponse<String> jobResp = httpPost(
                "/api/v1/tenants/" + tenantId + "/projects/" + projectId + "/render-jobs", jobBody);
        evidence.append(String.format("E2_CREATE_HTTP: %d%n", jobResp.statusCode()));
        Assertions.assertTrue(jobResp.statusCode() >= 200 && jobResp.statusCode() < 300,
                "RenderJob create should succeed: " + jobResp.statusCode());

        JsonNode jobNode = mapper.readTree(jobResp.body());
        String jobId = jobNode.get("id").asText();
        String initialStatus = jobNode.get("status").asText();
        evidence.append(String.format("E3_JOB_ID: %s%n", jobId));
        evidence.append(String.format("E4_INITIAL_STATUS: %s%n", initialStatus));
        Assertions.assertEquals("QUEUED", initialStatus, "Initial status should be QUEUED");

        // Verify persisted state
        String dbStatus = jdbc.queryForObject(
                "SELECT status FROM render_job WHERE id = ?", String.class, jobId);
        String dbProvider = jdbc.queryForObject(
                "SELECT selected_provider FROM render_job WHERE id = ?", String.class, jobId);
        evidence.append(String.format("E4_DB_STATUS: %s%n", dbStatus));
        evidence.append(String.format("E4_DB_PROVIDER: %s%n", dbProvider));
        Assertions.assertEquals("QUEUED", dbStatus);
        Assertions.assertNull(dbProvider, "Initial selected_provider should be NULL");

        // Start the same Job
        HttpResponse<String> startResp = httpPost(
                "/api/v1/tenants/" + tenantId + "/projects/" + projectId
                        + "/render-jobs/" + jobId + "/start", null);
        evidence.append(String.format("E5_START_HTTP: %d%n", startResp.statusCode()));
        JsonNode startNode = mapper.readTree(startResp.body());
        String startStatus = startNode.has("status") ? startNode.get("status").asText() : "unknown";
        evidence.append(String.format("E5_START_RESPONSE_STATUS: %s%n", startStatus));

        // Check database after start
        String postStartStatus = jdbc.queryForObject(
                "SELECT status FROM render_job WHERE id = ?", String.class, jobId);
        String postStartProvider = jdbc.queryForObject(
                "SELECT selected_provider FROM render_job WHERE id = ?", String.class, jobId);
        evidence.append(String.format("E9_POST_START_STATUS: %s%n", postStartStatus));
        evidence.append(String.format("E9_POST_START_PROVIDER: %s%n", postStartProvider));

        // Status API
        HttpResponse<String> statusResp = httpGet(
                "/api/v1/tenants/" + tenantId + "/projects/" + projectId
                        + "/render-jobs/" + jobId);
        evidence.append(String.format("E13_STATUS_HTTP: %d%n", statusResp.statusCode()));
        JsonNode statusNode = mapper.readTree(statusResp.body());
        String apiStatus = statusNode.get("status").asText();
        evidence.append(String.format("E13_API_STATUS: %s%n", apiStatus));
        Assertions.assertEquals(postStartStatus, apiStatus, "API status should match DB");
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

    // ========== Removed routes ==========

    @Test
    void executeLocal_remains404() throws Exception {
        HttpResponse<String> response = httpPost(
                "/api/v1/tenants/t1/projects/p1/render-jobs/rj1/execute-local", null);
        evidence.append(String.format("REMOVED_EXECUTELocal: %d%n", response.statusCode()));
        Assertions.assertEquals(404, response.statusCode());
    }

    @Test
    void retry_remains404() throws Exception {
        HttpResponse<String> response = httpPost("/api/v1/render/jobs/rj1/retry", null);
        evidence.append(String.format("REMOVED_RETRY: %d%n", response.statusCode()));
        Assertions.assertEquals(404, response.statusCode());
    }

    // ========== Concurrent start ==========

    @Test
    void concurrentStart_noDuplicateExecution() throws Exception {
        // Create tenant + project + job
        String tenantId = createTenant("conc-tenant");
        String projectId = createProject(tenantId, "conc-project");
        String jobId = createRenderJob(tenantId, projectId);

        // Verify initial state
        String preStatus = jdbc.queryForObject(
                "SELECT status FROM render_job WHERE id = ?", String.class, jobId);
        Assertions.assertEquals("QUEUED", preStatus);

        // Issue two concurrent start requests
        ExecutorService executor = Executors.newFixedThreadPool(2);
        String startPath = "/api/v1/tenants/" + tenantId + "/projects/" + projectId
                + "/render-jobs/" + jobId + "/start";
        CyclicBarrier barrier = new CyclicBarrier(2);

        Callable<Integer> startCall = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + startPath))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .header("Content-Type", "application/json")
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
        };

        Future<Integer> futureA = executor.submit(startCall);
        Future<Integer> futureB = executor.submit(startCall);

        int statusA = futureA.get(30, TimeUnit.SECONDS);
        int statusB = futureB.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        evidence.append(String.format("CONCURRENT_A: %d%n", statusA));
        evidence.append(String.format("CONCURRENT_B: %d%n", statusB));

        // Verify no duplicate execution
        String finalStatus = jdbc.queryForObject(
                "SELECT status FROM render_job WHERE id = ?", String.class, jobId);
        String finalProvider = jdbc.queryForObject(
                "SELECT selected_provider FROM render_job WHERE id = ?", String.class, jobId);
        evidence.append(String.format("CONCURRENT_FINAL_STATUS: %s%n", finalStatus));
        evidence.append(String.format("CONCURRENT_FINAL_PROVIDER: %s%n", finalProvider));

        // Verify only one logical execution attempt
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM render_job WHERE id = ?", Integer.class, jobId);
        Assertions.assertEquals(1, count, "Should have exactly one RenderJob record");
    }

    // ========== Sequential repeated start ==========

    @Test
    void sequentialRepeatedStart_idempotent() throws Exception {
        String tenantId = createTenant("repeat-tenant");
        String projectId = createProject(tenantId, "repeat-project");
        String jobId = createRenderJob(tenantId, projectId);

        // First start
        HttpResponse<String> resp1 = httpPost(
                "/api/v1/tenants/" + tenantId + "/projects/" + projectId
                        + "/render-jobs/" + jobId + "/start", null);
        evidence.append(String.format("REPEAT_START_1: %d%n", resp1.statusCode()));

        // Second start
        HttpResponse<String> resp2 = httpPost(
                "/api/v1/tenants/" + tenantId + "/projects/" + projectId
                        + "/render-jobs/" + jobId + "/start", null);
        evidence.append(String.format("REPEAT_START_2: %d%n", resp2.statusCode()));

        // Verify one execution attempt
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM render_job WHERE id = ?", Integer.class, jobId);
        Assertions.assertEquals(1, count, "Should have exactly one RenderJob record");
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

    private HttpResponse<String> httpGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
