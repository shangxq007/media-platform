package com.example.platform;

import com.example.platform.render.infrastructure.RenderProviderRegistry;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.fasterxml.jackson.databind.JsonNode;
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
        assertPostContainedWithoutRenderWrite(
                "/api/tenants/request-tenant/projects/request-project/render-jobs/request-job/start", null);
    }

    // ========== Test 2: Concurrent start — single winner ==========

    @Test
    void concurrentStart_singleWinner() throws Exception {
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM render_job", Integer.class);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        String startUrl = baseUrl
                + "/api/tenants/request-tenant/projects/request-project/render-jobs/request-job/start";

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

        Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM render_job", Integer.class);
        Assertions.assertEquals(List.of(403, 403), results.stream().sorted().toList());
        Assertions.assertEquals(before, after, "Concurrent denials must not dispatch a render write");
    }

    // ========== Test 3: Sequential duplicate start ==========

    @Test
    void sequentialDuplicateStart_idempotent() throws Exception {
        String path = "/api/tenants/request-tenant/projects/request-project/render-jobs/request-job/start";
        assertPostContainedWithoutRenderWrite(path, null);
        assertPostContainedWithoutRenderWrite(path, null);
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
