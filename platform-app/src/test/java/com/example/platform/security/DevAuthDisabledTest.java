package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * AUTH-UNPROTECTED-FIX-002 — when {@code app.security.dev-auth-endpoint} is off (the default, and
 * therefore every production-like runtime) the route does not exist, even for a caller that knows
 * the dev secret.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "preview"})
@TestPropertySource(properties = {
    "app.security.enabled=true",
    "app.security.oauth2.enabled=false",
    "app.security.jwt.secret-key=" + DevAuthSecurityTest.JWT_SECRET,
    "app.security.dev-auth-endpoint=false",
    "app.security.dev-auth-secret=" + DevAuthSecurityTest.DEV_SECRET,
    "app.identity.api-key-auth-enabled=false",
    "platform.runtime.production-checks-enabled=false",
    "spring.mvc.throw-exception-if-no-handler-found=true",
    "spring.web.resources.add-mappings=false"
})
class DevAuthDisabledTest extends PostgresTestContainerSupport {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void devAuthEndpointDoesNotExistWhenThePropertyIsOff() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/dev/auth/token"))
                .header("Content-Type", "application/json")
                .header(DevAuthSecretGuard.HEADER, DevAuthSecurityTest.DEV_SECRET)
                .POST(HttpRequest.BodyPublishers.ofString("{\"tenantId\":\"tenant-1\",\"userId\":\"user-1\"}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode(), response.body());
    }
}
