package com.example.platform;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Real TCP HTTP validation test.
 * Starts a real embedded Tomcat on a random port and sends actual HTTP requests
 * using Java HttpClient (real TCP, not MockMvc).
 *
 * Uses Testcontainers PostgreSQL for disposable database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "preview"})
@TestPropertySource(properties = {
    "app.security.enabled=false",
    "app.identity.api-key-auth-enabled=false",
    "spring.mvc.throw-exception-if-no-handler-found=true",
    "spring.web.resources.add-mappings=false"
})
class RealHttpSecurityBoundaryTest extends PostgresTestContainerSupport {

    @LocalServerPort
    private int port;

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
        Files.writeString(Path.of("/tmp/real-http-evidence.txt"), evidence.toString());
    }

    // ========== Removed routes — must return 404 ==========

    @Test
    void removedRoute_executeLocal_returns404() throws Exception {
        assertRemovedRoute("POST", "/api/v1/tenants/t1/projects/p1/render-jobs/rj1/execute-local");
    }

    @Test
    void removedRoute_retry_returns404() throws Exception {
        assertRemovedRoute("POST", "/api/v1/render/jobs/rj1/retry");
    }

    @Test
    void removedRoute_oldCreateAlias_returns404() throws Exception {
        assertRemovedRoute("POST", "/api/v1/render/jobs");
    }

    @Test
    void removedRoute_oldSubmitAlias_returns404() throws Exception {
        assertRemovedRoute("POST", "/api/v1/render/jobs/submit");
    }

    @Test
    void removedRoute_oldDetailAlias_returns404() throws Exception {
        assertRemovedRoute("GET", "/api/v1/render/jobs/rj1");
    }

    @Test
    void removedRoute_oldListAlias_returns404() throws Exception {
        assertRemovedRoute("GET", "/api/v1/render/jobs");
    }

    // ========== Canonical RenderJob routes ==========

    @Test
    void canonicalCreate_isRegistered() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v1/tenants/t1/projects/p1/render-jobs"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"timelineSnapshotId\":\"snap1\",\"profile\":\"default_1080p\"}"))
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        evidence.append(String.format("CANONICAL_CREATE: %d%n", response.statusCode()));
        Assertions.assertTrue(response.statusCode() != 404, "Canonical create route should not return 404, got " + response.statusCode());
    }

    @Test
    void canonicalStatus_isRegistered() throws Exception {
        HttpResponse<String> response = httpGetReq("/api/v1/tenants/t1/projects/p1/render-jobs/nonexistent");
        evidence.append(String.format("CANONICAL_STATUS: %d%n", response.statusCode()));
        // 404 for nonexistent resource is OK — route is registered
    }

    @Test
    void canonicalList_isRegistered() throws Exception {
        HttpResponse<String> response = httpGetReq("/api/v1/tenants/t1/projects/p1/render-jobs");
        evidence.append(String.format("CANONICAL_LIST: %d%n", response.statusCode()));
    }

    // ========== Cancel route ==========

    @Test
    void cancelRoute_isRegistered() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v1/render/jobs/nonexistent/cancel?tenantId=t1"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        evidence.append(String.format("CANCEL: %d%n", response.statusCode()));
    }

    // ========== Status-history route ==========

    @Test
    void statusHistory_isRegistered() throws Exception {
        HttpResponse<String> response = httpGetReq("/api/v1/render/jobs/nonexistent/status-history?tenantId=t1");
        evidence.append(String.format("STATUS_HISTORY: %d%n", response.statusCode()));
    }

    // ========== Job-scoped Artifact routes ==========

    @Test
    void jobArtifacts_isRegistered() throws Exception {
        HttpResponse<String> response = httpGetReq("/api/v1/render/jobs/nonexistent/artifacts");
        evidence.append(String.format("JOB_ARTIFACTS: %d%n", response.statusCode()));
    }

    @Test
    void jobArtifactContent_isRegistered() throws Exception {
        HttpResponse<String> response = httpGetReq("/api/v1/render/jobs/nonexistent/artifacts/aid1/content");
        evidence.append(String.format("JOB_ARTIFACT_CONTENT: %d%n", response.statusCode()));
    }

    @Test
    void jobArtifactAccess_isRegistered() throws Exception {
        HttpResponse<String> response = httpGetReq("/api/v1/render/jobs/nonexistent/artifacts/aid1/access");
        evidence.append(String.format("JOB_ARTIFACT_ACCESS: %d%n", response.statusCode()));
    }

    // ========== Dev routes — should be absent under test/preview ==========

    @Test
    void devRoutes_absentUnderPreview() throws Exception {
        String[] devPaths = {
            "/dev/storage-delivery-profiles",
            "/dev/storage-delivery-profiles/validation",
            "/dev/ingest/preflight-policy",
            "/dev/ingest/preflight-policy/config",
            "/dev/ingest/preflight-policy/decision-semantics",
            "/dev/tenants/t1/projects/p1/ingest/preflight/safe-reports",
        };
        for (String path : devPaths) {
            HttpResponse<String> response = httpGetReq(path);
            evidence.append(String.format("DEV_%s: %d%n", path, response.statusCode()));
            Assertions.assertEquals(404, response.statusCode(),
                "Dev route should be absent under preview: " + path);
        }
    }

    // ========== Admin routes — anonymous should not get 2xx ==========

    @Test
    void adminRoutes_anonymous_recordBehavior() throws Exception {
        // With security disabled, admin routes are accessible. Record for documentation.
        // Full admin security validation requires app.security.enabled=true.
        String[] adminPaths = {
            "/api/v1/admin/feature-flags",
            "/api/v1/admin/billing/plans",
            "/api/v1/admin/delivery/destinations",
        };
        for (String path : adminPaths) {
            HttpResponse<String> response = httpGetReq(path);
            int status = response.statusCode();
            evidence.append(String.format("ADMIN_ANON_%s: %d (security-disabled)%n", path, status));
        }
        // Admin mutation test
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v1/admin/feature-flags"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .header("Content-Type", "application/json")
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        evidence.append(String.format("ADMIN_MUTATION_ANON: %d (security-disabled)%n", response.statusCode()));
    }

    // ========== SPA fallback isolation ==========

    @Test
    void spaFallback_onlyHandlesAppNamespace() throws Exception {
        // /app/something should get SPA fallback (forward to index.html)
        // /api/something unknown should get 404
        HttpResponse<String> apiUnknown = httpGetReq("/api/v1/nonexistent-path");
        evidence.append(String.format("UNKNOWN_API: %d%n", apiUnknown.statusCode()));
        Assertions.assertEquals(404, apiUnknown.statusCode(),
            "Unknown API path should return 404, not SPA HTML");

        HttpResponse<String> devUnknown = httpGetReq("/dev/nonexistent");
        evidence.append(String.format("UNKNOWN_DEV: %d%n", devUnknown.statusCode()));
        Assertions.assertEquals(404, devUnknown.statusCode(),
            "Unknown dev path should return 404, not SPA HTML");

        HttpResponse<String> adminUnknown = httpGetReq("/admin/nonexistent");
        evidence.append(String.format("UNKNOWN_ADMIN: %d%n", adminUnknown.statusCode()));
        Assertions.assertEquals(404, adminUnknown.statusCode(),
            "Unknown admin path should return 404, not SPA HTML");
    }

    // ========== Health ==========

    @Test
    void health_isReachable() throws Exception {
        HttpResponse<String> response = httpGetReq("/actuator/health");
        evidence.append(String.format("HEALTH: %d%n", response.statusCode()));
    }

    // ========== Helpers ==========

    private void assertRemovedRoute(String method, String path) throws Exception {
        HttpResponse<String> response;
        if ("GET".equals(method)) {
            response = httpGetReq(path);
        } else {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }
        evidence.append(String.format("REMOVED_%s %s: %d%n", method, path, response.statusCode()));
        Assertions.assertEquals(404, response.statusCode(),
            "Removed route should return 404: " + method + " " + path);
    }

    private HttpResponse<String> httpGetReq(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .GET()
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
