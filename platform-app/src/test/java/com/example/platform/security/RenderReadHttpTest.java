package com.example.platform.security;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP/JWT/RBAC/application/repository path on a fresh Testcontainers database migrated by the complete Flyway stream. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "preview"})
@TestPropertySource(properties = {"app.security.enabled=true", "app.security.oauth2.enabled=false",
        "app.identity.api-key-auth-enabled=false", "app.outbox.dispatcher-enabled=false",
        "app.security.jwt.secret-key=render-read-http-test-only-key-at-least-256-bits-20260912"})
class RenderReadHttpTest extends PostgresTestContainerSupport {
    static final String SECRET = "render-read-http-test-only-key-at-least-256-bits-20260912";
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.context.ApplicationContext context;
    final ObjectMapper mapper = new ObjectMapper();
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    @BeforeEach void seed() {
        jdbc.execute("TRUNCATE TABLE render_job, project, workspace_member, workspace, tenant, user_role_assignment, role_permission, permission, role CASCADE");
        jdbc.execute("insert into tenant(id,name,status,created_at) values('read-tenant','Read test','ACTIVE',now()),('foreign-tenant','Foreign','ACTIVE',now())");
        jdbc.execute("insert into workspace(id,tenant_id,name,status,created_at,updated_at) values ('read-workspace','read-tenant','Read scope','ACTIVE',now(),now()),('foreign-workspace','foreign-tenant','Foreign scope','ACTIVE',now(),now())");
        jdbc.execute("insert into project(id,tenant_id,workspace_id,name,status,created_at) values('read-project','read-tenant','read-workspace','Readable','ACTIVE',now()),('empty-project','read-tenant','read-workspace','Empty','ACTIVE',now()),('hidden-project','read-tenant','read-workspace','Hidden','ACTIVE',now()),('foreign-project','foreign-tenant','foreign-workspace','Foreign','ACTIVE',now())");
        var accounts=context.getBean(com.example.platform.identity.app.AccountMembershipService.class);
        var operator=com.example.platform.shared.authorization.CanonicalActor.system("system:identity-provisioning",null);
        for(String member:List.of("reader","denied")) {
            jdbc.update("insert into \"user\"(id,tenant_id,username,email,role,status,created_at) values (?,'read-tenant',?,?,'MEMBER','ACTIVE',now()) on conflict(id) do nothing",member,member,member+"@test.invalid");
            String account=accounts.provisionVerifiedAccount(operator,"urn:media-platform:local-hmac",member);
            accounts.linkMembership(operator,account,"read-tenant",member);
            jdbc.update("insert into workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values (?,'read-workspace',?,'VIEWER','ACTIVE',now(),now())","member-"+member,member);
        }
        jdbc.execute("insert into role(id,role_key,name,scope,created_at) values('read-role','READ_TEST','Read-only test','WORKSPACE',now())");
        jdbc.execute("insert into permission(id,permission_key,name,created_at) values('read-permission','READ','Read',now())");
        jdbc.execute("insert into role_permission(id,role_id,permission_id,created_at) values('read-rp','read-role','read-permission',now())");
        jdbc.execute("insert into user_role_assignment(id,tenant_id,project_id,user_id,role_id,created_at) values('read-assignment','read-tenant','read-project','reader','read-role',now()),('empty-assignment','read-tenant','empty-project','reader','read-role',now())");
        // Existing synthetic terminal rows only: no submission, queueing, provider call or mutation HTTP request.
        jdbc.execute("insert into render_job(id,project_id,tenant_id,timeline_snapshot_id,profile,status,created_at,initiator_type,initiator_id,initiator_tenant_id) values('read-job','read-project','read-tenant','snapshot','read-profile','FAILED',now(),'USER','reader','read-tenant'),('hidden-job','hidden-project','read-tenant','hidden-snapshot','hidden-profile','COMPLETED',now(),'USER','other','read-tenant'),('foreign-job','foreign-project','foreign-tenant','foreign-snapshot','foreign-profile','COMPLETED',now(),'USER','other','foreign-tenant')");
    }
    String token(String user, String tenant) {
        var jwt = Jwts.builder().subject(user).claim("roles", List.of("VIEWER"))
                .expiration(new Date(System.currentTimeMillis() + 600000));
        if (tenant != null) jwt.claim("tenantId", tenant);
        return jwt.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
    HttpResponse<String> get(String path, String bearer) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    String jobs(String project) { return "/api/tenants/read-tenant/projects/" + project + "/render-jobs"; }
    @Test void authorizedDiscoveryResolutionListAndDetailUseTheExistingExactDto() throws Exception {
        assertEquals(1, context.getBeansOfType(com.example.platform.identity.api.project.ProjectReadQuery.class).size());
        assertSame(context.getBean(com.example.platform.identity.app.TenantProjectService.class), context.getBean(com.example.platform.identity.api.project.ProjectReadQuery.class));
        String auth = token("reader", "read-tenant");
        var discovery = get("/api/identity/tenants/read-tenant/projects", auth);
        assertEquals(200, discovery.statusCode());
        JsonNode projects = mapper.readTree(discovery.body());
        Set<String> ids = new HashSet<>(); projects.forEach(p -> { ids.add(p.get("id").asText()); assertEquals("read-tenant", p.get("tenantId").asText()); });
        assertEquals(Set.of("read-project", "empty-project"), ids);
        var scope = get("/api/identity/projects/read-project", auth); assertEquals(200, scope.statusCode());
        assertEquals("read-tenant", mapper.readTree(scope.body()).get("tenantId").asText());
        var list = get(jobs("read-project"), auth); assertEquals(200, list.statusCode());
        var rows = mapper.readTree(list.body()); assertEquals(1, rows.size());
        Set<String> fields = new HashSet<>(); rows.get(0).fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("id", "projectId", "timelineSnapshotId", "profile", "status"), fields);
        assertEquals("FAILED", rows.get(0).get("status").asText());
        var detail = get(jobs("read-project") + "/read-job", auth); assertEquals(200, detail.statusCode());
        assertEquals(rows.get(0), mapper.readTree(detail.body()));
        assertEquals(3, jdbc.queryForObject("select count(*) from render_job", Integer.class));
    }
    @Test void sameTenantWithoutReadPermissionIsDeniedAndCannotDiscoverTheInventory() throws Exception {
        String auth = token("denied", "read-tenant");
        var discovery = get("/api/identity/tenants/read-tenant/projects", auth); assertEquals(200, discovery.statusCode());
        assertEquals(0, mapper.readTree(discovery.body()).size());
        var list = get(jobs("read-project"), auth); assertEquals(403, list.statusCode()); assertFalse(list.body().contains("read-job"));
        assertEquals(403, get(jobs("read-project") + "/read-job", auth).statusCode());
    }
    @Test void missingAuthenticationOrTenantAndMismatchedTenantFailClosed() throws Exception {
        assertEquals(401, get(jobs("read-project"), null).statusCode());
        assertEquals(401, get(jobs("read-project"), token("reader", null)).statusCode());
        assertEquals(403, get("/api/tenants/foreign-tenant/projects/foreign-project/render-jobs", token("reader", "read-tenant")).statusCode());
        assertEquals(403, get("/api/identity/tenants/foreign-tenant/projects", token("reader", "read-tenant")).statusCode());
    }
    @Test void readableEmptyProjectAndForeignJobReferencesStayDistinctFromPermissionDenial() throws Exception {
        String auth = token("reader", "read-tenant");
        var empty = get(jobs("empty-project"), auth); assertEquals(200, empty.statusCode()); assertEquals(0, mapper.readTree(empty.body()).size());
        assertEquals(404, get(jobs("empty-project") + "/read-job", auth).statusCode());
        assertEquals(404, get(jobs("read-project") + "/absent-job", auth).statusCode());
        var foreign = get(jobs("read-project") + "/foreign-job", auth); assertEquals(404, foreign.statusCode()); assertFalse(foreign.body().contains("foreign-profile"));
        assertEquals(403, get(jobs("hidden-project"), auth).statusCode());
    }
    @Test void anEarlierResolvedContextAndUnchangedJwtCannotAuthorizeReadsAfterRevocation() throws Exception {
        String auth = token("reader", "read-tenant");
        assertEquals(200, get("/api/identity/projects/read-project", auth).statusCode());
        assertEquals(200, get(jobs("read-project"), auth).statusCode());
        jdbc.update("delete from user_role_assignment where id='read-assignment'");
        assertEquals(403, get(jobs("read-project"), auth).statusCode());
        assertEquals(403, get(jobs("read-project") + "/read-job", auth).statusCode());
        assertEquals(403, get("/api/identity/projects/read-project", auth).statusCode());
    }
    @Test void absentProjectCannotDeriveScopeFromAStaleGrant() throws Exception {
        jdbc.execute("insert into user_role_assignment(id,tenant_id,project_id,user_id,role_id,created_at) values('absent-assignment','read-tenant','absent-project','reader','read-role',now())");
        String auth = token("reader", "read-tenant");
        assertEquals(403, get("/api/identity/projects/absent-project", auth).statusCode());
        assertEquals(403, get(jobs("absent-project"), auth).statusCode());
    }
}
