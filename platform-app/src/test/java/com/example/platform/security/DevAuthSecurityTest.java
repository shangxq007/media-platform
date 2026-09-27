package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * AUTH-UNPROTECTED-FIX-002 — the dev token endpoint is only reachable with the dev secret, and a
 * plain authenticated (JWT) caller is not sufficient.
 *
 * <p>Answers are asserted as a set because an anonymous caller can be answered by the entry point
 * (401) or the access-denied handler (403) depending on the chain; both mean "rejected".</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "preview"})
@TestPropertySource(properties = {
    "app.security.enabled=true",
    "app.security.oauth2.enabled=false",
    "app.security.jwt.secret-key=" + DevAuthSecurityTest.JWT_SECRET,
    "app.security.dev-auth-endpoint=true",
    "app.security.dev-auth-secret=" + DevAuthSecurityTest.DEV_SECRET,
    "app.identity.api-key-auth-enabled=false",
    "platform.runtime.production-checks-enabled=false",
    "spring.mvc.throw-exception-if-no-handler-found=true",
    "spring.web.resources.add-mappings=false"
})
class DevAuthSecurityTest extends PostgresTestContainerSupport {

    static final String DEV_SECRET = "test-dev-auth-secret-value";
    static final String JWT_SECRET =
            "test-only-insecure-key-replace-in-production-min-256-bits!!";
    private static final Set<Integer> REJECTED = Set.of(401, 403);

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void devAuthWithoutSecretIsRejected() throws Exception {
        HttpResponse<String> response = postToken(null);
        assertTrue(REJECTED.contains(response.statusCode()),
                "missing dev secret must be rejected, was " + response.statusCode());
        assertFalse(response.body().contains("accessToken"),
                "no token may be minted without the dev secret");
    }

    @Test
    void devAuthWithWrongSecretIsRejected() throws Exception {
        HttpResponse<String> response = postToken("not-the-dev-secret");
        assertTrue(REJECTED.contains(response.statusCode()),
                "wrong dev secret must be rejected, was " + response.statusCode());
        assertFalse(response.body().contains("accessToken"));
    }

    @Test
    void devAuthRejectsAValidPlatformJwtWithoutTheDevSecret() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/dev/auth/token"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + platformJwt())
                .POST(HttpRequest.BodyPublishers.ofString("{\"tenantId\":\"tenant-1\",\"userId\":\"user-1\"}"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertTrue(REJECTED.contains(response.statusCode()),
                "a valid JWT alone must not reach the dev token mint, was " + response.statusCode());
        assertFalse(response.body().contains("accessToken"));
    }

    @Test
    void devAuthWithCorrectSecretIssuesTheAdminToken() throws Exception {
        HttpResponse<String> response = postToken(DEV_SECRET);

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("accessToken"), response.body());
    }

    /**
     * The URL-layer rule — not the controller — decides this case: a request to a non-existent
     * {@code /api/dev/auth/**} path without the dev secret is rejected (401/403) instead of falling
     * through to 404, while the same path with the secret does fall through to 404. Reverting the
     * rule to {@code permitAll} makes this test fail with 404.
     */
    @Test
    void devAuthUrlRuleDeniesUncoveredPathsWithoutTheSecret() throws Exception {
        HttpResponse<String> denied = postPath("/api/dev/auth/not-a-real-endpoint", null);
        assertTrue(REJECTED.contains(denied.statusCode()),
                "the dev auth URL rule must deny before the dispatcher, was " + denied.statusCode());

        HttpResponse<String> allowedThrough = postPath("/api/dev/auth/not-a-real-endpoint", DEV_SECRET);
        assertEquals(404, allowedThrough.statusCode(),
                "with the dev secret the request must fall through to the dispatcher");
    }

    private HttpResponse<String> postToken(String devSecret) throws Exception {
        return postPath("/api/dev/auth/token", devSecret);
    }

    private HttpResponse<String> postPath(String path, String devSecret) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"tenantId\":\"tenant-1\",\"userId\":\"user-1\"}"));
        if (devSecret != null) {
            builder.header(DevAuthSecretGuard.HEADER, devSecret);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static String platformJwt() {
        var key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("user-1")
                .claims(Map.of("tenantId", "tenant-1", "roles", List.of("USER", "ADMIN")))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();
    }
}
