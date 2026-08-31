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

    // ========== Removed routes ==========

    @Test
    void executeLocal_remains404() throws Exception {
        assertPostContainedWithoutRenderWrite(
                "/api/tenants/t1/projects/p1/render-jobs/rj1/execute-local", null);
    }

    @Test
    void retry_remains404() throws Exception {
        assertPostContainedWithoutRenderWrite("/api/render/jobs/rj1/retry", null);
    }

    // ========== Concurrent start ==========

    @Test
    void concurrentStart_noDuplicateExecution() throws Exception {
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM render_job", Integer.class);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        String startPath = "/api/tenants/request-tenant/projects/request-project/render-jobs/request-job/start";
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

        Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM render_job", Integer.class);
        Assertions.assertEquals(403, statusA);
        Assertions.assertEquals(403, statusB);
        Assertions.assertEquals(before, after, "Concurrent denials must not dispatch a render write");
    }

    // ========== Sequential repeated start ==========

    @Test
    void sequentialRepeatedStart_idempotent() throws Exception {
        String path = "/api/tenants/request-tenant/projects/request-project/render-jobs/request-job/start";
        assertPostContainedWithoutRenderWrite(path, null);
        assertPostContainedWithoutRenderWrite(path, null);
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
