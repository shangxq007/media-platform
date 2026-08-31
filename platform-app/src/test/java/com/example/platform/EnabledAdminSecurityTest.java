package com.example.platform;

import com.example.platform.security.JwtProperties;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Real TCP HTTP Admin security validation with security explicitly enabled.
 * Uses @MockitoBean for JwtDecoder to avoid OIDC issuer connection requirement.
 * The mock delegates to LegacyHmacJwtDecoder for actual token verification.
 *
 * Uses Testcontainers PostgreSQL for disposable database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "preview"})
@TestPropertySource(properties = {
    "app.security.enabled=true",
    "app.security.oauth2.enabled=true",
    "app.security.jwt.secret-key=test-only-insecure-key-replace-in-production-min-256-bits!!",
    "app.security.dev-auth-endpoint=true",
    "app.identity.api-key-auth-enabled=false",
    "spring.mvc.throw-exception-if-no-handler-found=true",
    "spring.web.resources.add-mappings=false"
})
class EnabledAdminSecurityTest extends PostgresTestContainerSupport {

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private com.example.platform.analytics.scheduler.AnalyticsRebuildJob analyticsRebuildJob;

    @MockitoBean
    private com.example.platform.extension.runtime.PluginRuntime pluginRuntime;

    @MockitoBean
    private com.example.platform.billing.app.SubscriptionBillingService subscriptionBillingService;

    @MockitoBean
    private com.example.platform.render.infrastructure.remote.RemoteRenderDispatcher remoteRenderDispatcher;

    @MockitoBean
    private com.example.platform.render.app.product.ProductRuntimeService productRuntimeService;

    @MockitoBean
    private com.example.platform.notification.app.NotificationChannelBindingService notificationChannelBindingService;

    @Autowired
    private JwtProperties jwtProperties;

    @LocalServerPort
    private int port;

    private HttpClient client;
    private String baseUrl;
    private JwtTestHelper jwtHelper;

    private static final StringBuilder evidence = new StringBuilder();

    @BeforeEach
    void setUp() {
        client = HttpClient.newHttpClient();
        baseUrl = "http://localhost:" + port;
        jwtHelper = new JwtTestHelper(jwtProperties);
        // Configure mock to delegate to real HMAC decode (jjwt, same logic as JwtAuthFilter)
        org.mockito.Mockito.when(jwtDecoder.decode(org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> decodeHmac(invocation.getArgument(0)));
    }


    private org.springframework.security.oauth2.jwt.Jwt decodeHmac(String token) {
        try {
            javax.crypto.SecretKey key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                    jwtProperties.secretKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            io.jsonwebtoken.Claims claims = io.jsonwebtoken.Jwts.parser()
                    .verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            java.time.Instant issuedAt = claims.getIssuedAt() != null
                    ? claims.getIssuedAt().toInstant()
                    : java.time.Instant.now();
            java.time.Instant expiresAt = claims.getExpiration() != null
                    ? claims.getExpiration().toInstant()
                    : issuedAt.plusSeconds(3600);
            org.springframework.security.oauth2.jwt.Jwt.Builder builder =
                    org.springframework.security.oauth2.jwt.Jwt.withTokenValue(token)
                            .header("alg", "HS256")
                            .subject(claims.getSubject())
                            .issuedAt(issuedAt)
                            .expiresAt(expiresAt);
            for (java.util.Map.Entry<String, Object> entry : claims.entrySet()) {
                String claimName = entry.getKey();
                if ("sub".equals(claimName) || "iss".equals(claimName)
                        || "iat".equals(claimName) || "exp".equals(claimName)) {
                    continue;
                }
                builder.claim(claimName, entry.getValue());
            }
            return builder.build();
        } catch (io.jsonwebtoken.JwtException ex) {
            throw new org.springframework.security.oauth2.jwt.JwtException("Invalid legacy HMAC JWT", ex);
        }
    }

    @AfterAll
    static void writeEvidence() throws Exception {
        Files.writeString(Path.of("/tmp/admin-security-evidence.txt"), evidence.toString());
    }

    // ========== Helper methods ==========

    private HttpResponse<String> httpGet(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> httpPost(String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "{}"))
            .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> httpPut(String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .method("PUT", HttpRequest.BodyPublishers.ofString(body != null ? body : "{}"))
            .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ========== Security-enabled baseline ==========

    @Test
    void securityEnabled_serverStarts() throws Exception {
        HttpResponse<String> response = httpGet("/actuator/health", null);
        evidence.append(String.format("HEALTH: %d%n", response.statusCode()));
        Assertions.assertEquals(200, response.statusCode(), "Health should return 200");
    }

    @Test
    void phaseZeroContainedRoutesRejectAnonymousAndOrdinaryUsersWithoutDispatch() throws Exception {
        String[] paths = {
            "/api/extensions/demo/execute",
            "/api/analytics/internal/rebuild-profiles",
            "/api/billing/cycles/process-due",
            "/api/remote-worker/register",
            "/api/products/product-1/dependencies",
            "/api/me/notification-channels/binding-1/verify",
        };

        for (String path : paths) {
            int anonymousStatus = httpPost(path, null, "{}").statusCode();
            Assertions.assertTrue(anonymousStatus == 401 || anonymousStatus == 403,
                    "Contained anonymous request must be rejected: " + path + " got " + anonymousStatus);

            int userStatus = httpPost(path, jwtHelper.nonAdminToken(), "{}").statusCode();
            Assertions.assertEquals(403, userStatus,
                    "Contained ordinary-user request must be denied: " + path);
        }

        org.mockito.Mockito.verifyNoInteractions(analyticsRebuildJob, pluginRuntime,
                subscriptionBillingService, remoteRenderDispatcher, productRuntimeService,
                notificationChannelBindingService);
    }

    @Test
    void removedFakeSchedulerAndNonMcpAliasAreDeniedForAuthenticatedUser() throws Exception {
        String token = jwtHelper.nonAdminToken();
        Assertions.assertEquals(403,
                httpPost("/api/internal/scheduler/run/demo", token, "{}").statusCode());
        Assertions.assertEquals(403,
                httpPost("/api/media/tools/render_timeline", token, "{}").statusCode());
    }

    // ========== Removed routes under security ==========

    @Test
    void removedRouteAliasesAreDeniedBeforeDispatch() throws Exception {
        String admin = jwtHelper.adminToken();
        String[][] routes = {
            {"GET", "/api/render/jobs/rj1"},
            {"GET", "/api/render/jobs"},
            {"POST", "/api/render/jobs"},
            {"POST", "/api/render/jobs/submit"},
            {"POST", "/api/render/jobs/rj1/retry"},
            {"POST", "/api/tenants/t1/projects/p1/render-jobs/rj1/execute-local"},
        };
        for (String[] route : routes) {
            HttpResponse<String> response;
            if ("GET".equals(route[0])) {
                response = httpGet(route[1], admin);
            } else {
                response = httpPost(route[1], admin, null);
            }
            evidence.append(String.format("REMOVED_%s %s: %d (authorized)%n", route[0], route[1], response.statusCode()));
            Assertions.assertEquals(403, response.statusCode(),
                "Fail-closed policy must deny before route dispatch: " + route[0] + " " + route[1]);
        }
    }

    // ========== Anonymous Admin access ==========

    @Test
    void anonymousAdmin_reads_rejected() throws Exception {
        String[] adminPaths = {
            "/api/admin/feature-flags",
            "/api/admin/billing/plans",
            "/api/admin/delivery/destinations",
            "/api/identity/admin/tenants",
        };
        for (String path : adminPaths) {
            HttpResponse<String> response = httpGet(path, null);
            int status = response.statusCode();
            evidence.append(String.format("ANON_ADMIN_READ %s: %d%n", path, status));
            Assertions.assertTrue(status == 401 || status == 403,
                "Anonymous admin read should be rejected: " + path + " got " + status);
        }
    }

    @Test
    void anonymousAdmin_mutations_rejected() throws Exception {
        String[][] mutations = {
            {"POST", "/api/admin/feature-flags"},
            {"PUT", "/api/admin/notifications/events/test"},
            {"POST", "/api/admin/notifications/deliveries/d1/retry"},
        };
        for (String[] mutation : mutations) {
            HttpResponse<String> response = httpPost(mutation[1], null, "{}");
            int status = response.statusCode();
            evidence.append(String.format("ANON_ADMIN_MUTATION %s %s: %d%n", mutation[0], mutation[1], status));
            Assertions.assertTrue(status == 401 || status == 403,
                "Anonymous admin mutation should be rejected: " + mutation[1] + " got " + status);
        }
    }

    // ========== Non-admin access ==========

    @Test
    void nonAdmin_reads_rejectedByAuthorization() throws Exception {
        String nonAdmin = jwtHelper.nonAdminToken();
        String[] adminPaths = {
            "/api/admin/feature-flags",
            "/api/admin/billing/plans",
            "/api/admin/platform/readiness",
        };
        for (String path : adminPaths) {
            HttpResponse<String> response = httpGet(path, nonAdmin);
            int status = response.statusCode();
            evidence.append(String.format("NONADMIN_ADMIN_READ %s: %d%n", path, status));
            // Non-admin authenticated user should get 403 (authorization rejected)
            Assertions.assertEquals(403, status,
                "Non-admin admin read should be rejected by authorization: " + path + " got " + status);
        }
    }

    @Test
    void nonAdmin_mutations_rejectedByAuthorization() throws Exception {
        String nonAdmin = jwtHelper.nonAdminToken();
        String[][] mutations = {
            {"POST", "/api/admin/feature-flags"},
            {"PUT", "/api/admin/notifications/events/test"},
        };
        for (String[] mutation : mutations) {
            HttpResponse<String> response;
            if ("PUT".equals(mutation[0])) {
                response = httpPut(mutation[1], nonAdmin, "{}");
            } else {
                response = httpPost(mutation[1], nonAdmin, "{}");
            }
            int status = response.statusCode();
            evidence.append(String.format("NONADMIN_ADMIN_MUTATION %s %s: %d%n", mutation[0], mutation[1], status));
            // Non-admin authenticated user should get 403 (authorization rejected)
            Assertions.assertEquals(403, status,
                "Non-admin admin mutation should be rejected by authorization: " + mutation[1] + " got " + status);
        }
    }

    // ========== Authorized Admin access ==========

    @Test
    void authorizedAdmin_read_reachesBoundary() throws Exception {
        String admin = jwtHelper.adminToken();
        HttpResponse<String> response = httpGet("/api/admin/feature-flags", admin);
        int status = response.statusCode();
        evidence.append(String.format("ADMIN_READ /admin/feature-flags: %d%n", status));
        // Should reach handler (200 or 404 for empty list, not 401/403)
        Assertions.assertTrue(status == 200 || status == 404,
            "Authorized admin read should reach handler: got " + status);
    }

    @Test
    void authorizedAdmin_mutation_invalidInput_returns400() throws Exception {
        String admin = jwtHelper.adminToken();
        // Empty body {} should fail Bean Validation (missing flagKey, flagType)
        HttpResponse<String> response = httpPost("/api/admin/feature-flags", admin, "{}");
        int status = response.statusCode();
        evidence.append(String.format("ADMIN_MUTATION_INVALID /admin/feature-flags: %d%n", status));
        // Invalid input should return 400 (validation failure), not 500
        Assertions.assertEquals(400, status,
            "Authorized admin mutation with invalid input should return 400: got " + status);
    }

    // ========== Identity admin route ==========

    @Test
    void identityAdminTenants_anonymous_rejected() throws Exception {
        HttpResponse<String> response = httpGet("/api/identity/admin/tenants", null);
        int status = response.statusCode();
        evidence.append(String.format("IDENTITY_ADMIN_ANON: %d%n", status));
        Assertions.assertTrue(status == 401 || status == 403,
            "Anonymous identity/admin should be rejected: got " + status);
    }

    @Test
    void identityAdminTenants_nonAdmin_rejected() throws Exception {
        String nonAdmin = jwtHelper.nonAdminToken();
        HttpResponse<String> response = httpGet("/api/identity/admin/tenants", nonAdmin);
        int status = response.statusCode();
        evidence.append(String.format("IDENTITY_ADMIN_NONADMIN: %d%n", status));
        Assertions.assertEquals(403, status,
            "Non-admin identity/admin should be rejected: got " + status);
    }

    @Test
    void identityAdminTenants_admin_reachesBoundary() throws Exception {
        String admin = jwtHelper.adminToken();
        HttpResponse<String> response = httpGet("/api/identity/admin/tenants", admin);
        int status = response.statusCode();
        evidence.append(String.format("IDENTITY_ADMIN_ADMIN: %d%n", status));
        // Should reach handler (200 or 404), not 401/403
        Assertions.assertTrue(status == 200 || status == 404,
            "Admin identity/admin should reach handler: got " + status);
    }

    // ========== Dev routes under security ==========

    @Test
    void devRoutes_absentUnderSecurity() throws Exception {
        String admin = jwtHelper.adminToken();
        String[] devPaths = {
            "/dev/storage-delivery-profiles",
            "/dev/ingest/preflight-policy",
        };
        for (String path : devPaths) {
            HttpResponse<String> response = httpGet(path, admin);
            evidence.append(String.format("DEV_%s: %d%n", path, response.statusCode()));
            Assertions.assertEquals(404, response.statusCode(),
                "Dev route should be absent: " + path);
        }
    }

    // ========== SPA fallback under security ==========

    @Test
    void spaFallback_notBackend() throws Exception {
        String admin = jwtHelper.adminToken();
        String[] paths = {
            "/api/does-not-exist",
            "/dev/does-not-exist",
            "/admin/does-not-exist",
        };
        for (String path : paths) {
            HttpResponse<String> response = httpGet(path, admin);
            evidence.append(String.format("UNKNOWN_%s: %d%n", path, response.statusCode()));
            Assertions.assertTrue(response.statusCode() == 403 || response.statusCode() == 404,
                "Unknown backend path must be denied or unmapped, never dispatched: " + path);
        }
    }

    // ========== Canonical routes under security ==========

    @Test
    void currentlyUnsafeCanonicalRenderRoutesAreContained() throws Exception {
        String admin = jwtHelper.adminToken();
        HttpResponse<String> response = httpGet("/api/tenants/t1/projects/p1/render-jobs", admin);
        evidence.append(String.format("CANONICAL_LIST: %d%n", response.statusCode()));
        Assertions.assertEquals(403, response.statusCode(),
            "Render routes remain contained until canonical route authority exists");
    }

    // ========== Error response safety ==========

    @Test
    void errorResponses_noSecretsExposed() throws Exception {
        // Check that 401/403 responses don't expose stack traces or internal details
        HttpResponse<String> anonResponse = httpGet("/api/admin/feature-flags", null);
        String body = anonResponse.body();
        evidence.append(String.format("ANON_ERROR_BODY_LENGTH: %d%n", body.length()));

        // Should not contain stack traces, SQL, credentials, etc.
        Assertions.assertFalse(body.contains("Exception"), "Error response should not contain exception class names");
        Assertions.assertFalse(body.contains("at com.example"), "Error response should not contain stack traces");
        Assertions.assertFalse(body.contains("jdbc:"), "Error response should not contain JDBC URLs");
        Assertions.assertFalse(body.contains("password"), "Error response should not contain password references");
    }
}
