package com.example.platform.identity.app;

import com.example.platform.identity.authorization.ApiKeyCanonicalActorResolver;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.observability.context.TraceKeys;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static org.junit.jupiter.api.Assertions.*;

/** Real database/key validation and actual filter/resolver; no mocked authentication success. */
class ApiKeyAuthenticationContextTest extends PostgresTestContainerSupport {
    static DataSource dataSource;
    IdentityAccessService identity;
    ApiKeyAuthFilter filter;
    ApiKeyCanonicalActorResolver resolver = new ApiKeyCanonicalActorResolver();
    MockHttpServletRequest request;
    MockHttpServletResponse response;

    @BeforeAll static void database() {
        dataSource = createDataSource();
        new JdbcTemplate(dataSource).execute("CREATE TABLE api_key (id varchar(64) primary key, tenant_id varchar(64), fingerprint varchar(32), hashed_key varchar(128) unique, principal varchar(255), created_at timestamp, last_used_at timestamp, revoked_at timestamp)");
    }
    @AfterAll static void close() { closeDataSource(dataSource); }
    @BeforeEach void setup() {
        new JdbcTemplate(dataSource).execute("TRUNCATE api_key");
        var properties = new IdentityProperties();
        properties.setApiKeyAuthEnabled(true);
        properties.setApiKeys(Map.of());
        identity = new IdentityAccessService(properties, new ApiKeyRepository(DSL.using(dataSource, SQLDialect.POSTGRES)));
        identity.storeRecord(new ApiKeyRecord("key-1", "tenant-a", identity.fingerprint("isolated-key"), identity.hashApiKey("isolated-key"), "principal-a", Instant.now(), null, null));
        filter = new ApiKeyAuthFilter(identity, properties);
        request = new MockHttpServletRequest("GET", "/api/identity/overview");
        response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); TenantContext.clear(); MDC.clear(); }

    @Test void authenticatedKeyEstablishesActorButDiagnosticMutationDoesNotChangeIt() throws Exception {
        request.addHeader("X-API-Key", "isolated-key");
        request.addHeader("X-Tenant-ID", "foreign-tenant");
        filter.doFilter(request, response, (req, res) -> {
            var actor = resolver.resolveCurrentActor().orElseThrow();
            assertEquals("principal-a", actor.actorId());
            assertEquals("tenant-a", actor.tenantId());
            assertEquals(ActorType.API_KEY_PRINCIPAL, actor.actorType());
            MDC.put(TraceKeys.PRINCIPAL, "forged-admin");
            MDC.put(TraceKeys.TENANT_ID, "foreign-tenant");
            assertEquals(actor, resolver.resolveCurrentActor().orElseThrow());
        });
        assertEquals(200, response.getStatus());
        assertTrue(resolver.resolveCurrentActor().isEmpty());
        assertNull(TenantContext.get());
        assertNull(MDC.get(TraceKeys.PRINCIPAL));
    }
    @Test void diagnosticPrincipalAloneNeverAuthenticates() {
        MDC.put(TraceKeys.PRINCIPAL, "forged-admin");
        MDC.put(TraceKeys.TENANT_ID, "tenant-a");
        assertTrue(resolver.resolveCurrentActor().isEmpty());
    }
    @Test void missingInvalidAndRevokedKeysRejectBeforeApplication() throws Exception {
        filter.doFilter(request, response, (a,b) -> fail("Missing key reached application"));
        assertEquals(401, response.getStatus());
        request.addHeader("X-API-Key", "invalid-key"); response = new MockHttpServletResponse();
        filter.doFilter(request, response, (a,b) -> fail("Invalid key reached application"));
        assertEquals(401, response.getStatus());
        assertTrue(identity.revoke("isolated-key"));
        request.removeHeader("X-API-Key"); request.addHeader("X-API-Key", "isolated-key");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, (a,b) -> fail("Revoked key reached application"));
        assertEquals(401, response.getStatus());
        assertTrue(resolver.resolveCurrentActor().isEmpty());
    }
    @Test void exceptionAndWorkerThreadCannotLeakAuthentication() throws Exception {
        request.addHeader("X-API-Key", "isolated-key");
        try (var executor = Executors.newSingleThreadExecutor()) {
            assertThrows(jakarta.servlet.ServletException.class, () -> filter.doFilter(request, response, (a,b) -> {
                assertTrue(resolver.resolveCurrentActor().isPresent());
                try { assertTrue(executor.submit(() -> resolver.resolveCurrentActor().isEmpty()).get()); }
                catch (Exception e) { throw new RuntimeException(e); }
                throw new jakarta.servlet.ServletException("isolated downstream failure");
            }));
            assertTrue(executor.submit(() -> resolver.resolveCurrentActor().isEmpty()).get());
        }
        assertTrue(resolver.resolveCurrentActor().isEmpty());
        assertNull(TenantContext.get());
        assertNull(MDC.get(TraceKeys.TENANT_ID));
    }
}
