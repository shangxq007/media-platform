package com.example.platform.marketplace;
import static org.assertj.core.api.Assertions.*;
import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.api.workspace.*;
import com.example.platform.identity.app.*;
import com.example.platform.identity.domain.*;
import com.example.platform.identity.infrastructure.RoleRepository;
import com.example.platform.marketplace.api.*;
import com.example.platform.media.api.*;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.web.context.request.*;
import com.fasterxml.jackson.databind.*;
import java.time.Instant;
import java.util.*;

@SpringBootTest(classes=com.example.platform.PlatformApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test","preview"})
@TestPropertySource(properties={"app.security.enabled=true","app.security.oauth2.enabled=false","app.identity.api-key-auth-enabled=false","app.outbox.dispatcher-enabled=false","storage.s3.enabled=false","app.security.jwt.secret-key=marketplace-http-key-at-least-256-bits"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
abstract class MarketplaceTestSupport extends PostgresTestContainerSupport {
    static final ObjectMapper JSON=new ObjectMapper();
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired MarketplaceApi marketplace;
    @LocalServerPort int port;
    String tenant,user,workspace,project,outsider;
    @BeforeEach
    void fixture() {
        tenant = "market-" + UUID.randomUUID();
        jdbc.update(
                "insert into tenant(id,name,status,created_at) values (?,?,'ACTIVE',now())",
                tenant,
                "workflow test");
        user = member();
        outsider = member();
        as(
                user,
                () -> {
                    workspace =
                            context.getBean(WorkspaceService.class)
                                    .createWorkspace(
                                            tenant,
                                            new CreateWorkspaceRequest("workflow", null, null))
                                    .id();
                });
        var roles = context.getBean(RoleRepository.class);
        var permissionService = context.getBean(PermissionService.class);
        var role =
                context.getBean(RoleService.class)
                        .createRole(
                                "market-" + UUID.randomUUID(),
                                "Marketplace test",
                                null,
                                com.example.platform.identity.domain.Role.RoleScope.WORKSPACE);
        for (String key :
                List.of(
                        "CREATE",
                        "READ",
                        "WRITE",
                        "marketplace.manage",
                        "marketplace.review",
                        "marketplace.publish")) {
            var permission =
                    roles.findAllPermissions().stream()
                            .filter(p -> p.permissionKey().equals(key))
                            .findFirst()
                            .orElseGet(
                                    () ->
                                            permissionService.createPermission(
                                                    key, key, null, "PROJECT"));
            roles.saveRolePermission(
                    new RolePermission(
                            UUID.randomUUID().toString(),
                            role.id(),
                            permission.id(),
                            Instant.now()));
        }
        String membership =
                jdbc.queryForObject(
                        "select id from workspace_member where workspace_id=? and user_id=?",
                        String.class,
                        workspace,
                        user);
        as(
                user,
                () ->
                        context.getBean(WorkspaceService.class)
                                .assignRoleToMember(
                                        workspace,
                                        membership,
                                        new AssignRoleRequest(role.roleKey(), user)));
        as(
                user,
                () ->
                        project =
                                context.getBean(TenantProjectService.class)
                                        .createProject(
                                                tenant,
                                                new CreateProjectRequest(
                                                        "workflow", null, workspace))
                                        .id());
    }

    String member() {
        String id = "member-" + UUID.randomUUID();
        jdbc.update(
                "insert into \"user\"(id,tenant_id,username,email,role,status,created_at) values"
                        + " (?,?,?,?,'MEMBER','ACTIVE',now())",
                id,
                tenant,
                id,
                id + "@test.invalid");
        var accounts = context.getBean(AccountMembershipService.class);
        var actor = CanonicalActor.system("system:identity-provisioning", tenant);
        var account = accounts.provisionVerifiedAccount(actor, "urn:media-platform:local-hmac", id);
        accounts.linkMembership(actor, account, tenant, id);
        return id;
    }

    void as(String subject, Runnable action) {
        var request = new MockHttpServletRequest();
        request.setAttribute("auth.subject", subject);
        request.setAttribute("jwt.issuer", "urn:media-platform:local-hmac");
        request.setAttribute("jwt.tenantId", tenant);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TenantContext.set(tenant);
        try {
            action.run();
        } finally {
            RequestContextHolder.resetRequestAttributes();
            TenantContext.clear();
        }
    }

    java.net.http.HttpResponse<String> http(String subject, String method, String path, Object body)
            throws Exception {
        var request =
                java.net.http.HttpRequest.newBuilder(
                                java.net.URI.create("http://127.0.0.1:" + port + path))
                        .header("Content-Type", "application/json");
        if (subject != null) {
            String token =
                    io.jsonwebtoken.Jwts.builder()
                            .subject(subject)
                            .claim("tenantId", tenant)
                            .expiration(new java.util.Date(System.currentTimeMillis() + 600000))
                            .signWith(
                                    io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                            "marketplace-http-key-at-least-256-bits"
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)))
                            .compact();
            request.header("Authorization", "Bearer " + token);
        }
        return java.net.http.HttpClient.newHttpClient()
                .send(
                        request.method(
                                        method,
                                        body == null
                                                ? java.net.http.HttpRequest.BodyPublishers.noBody()
                                                : java.net.http.HttpRequest.BodyPublishers.ofString(
                                                        JSON.writeValueAsString(body)))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
    }


    String asset() {
        Asset[] a=new Asset[1];as(user,()->a[0]=context.getBean(MediaAssets.class).register(tenant,project,"marketplace/"+UUID.randomUUID()+".mp4","VIDEO","sample.mp4",1L,"checksum"));return a[0].id();
    }
    String root(){return "/api/projects/"+project+"/marketplace";}
    Map<String,Object> createBody(String asset,String command) {return Map.of("commandId",command,"subject",Map.of("kind","MEDIA_ASSET","assetId",Map.of("value",asset),"version","v1"),"title","Public title","summary","Public summary","description","Public description");}
    JsonNode response(java.net.http.HttpResponse<String> r,int status) throws Exception {assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(status);return JSON.readTree(r.body());}
    JsonNode create(String asset) throws Exception {return response(http(user,"POST",root()+"/listings",createBody(asset,UUID.randomUUID().toString())),201);}
    JsonNode submit(JsonNode listing) throws Exception {return response(http(user,"POST",root()+"/listings/"+listing.path("id").asText()+"/reviews",Map.of("commandId",UUID.randomUUID().toString(),"expectedVersion",listing.path("version").asLong(),"title","Review title","description","Private review details")),201);}
    JsonNode approve(JsonNode review) throws Exception {return response(http(user,"POST",root()+"/reviews/"+review.path("id").asText()+"/decisions",Map.of("commandId",UUID.randomUUID().toString(),"expectedVersion",review.path("version").asLong(),"decision","APPROVE")),200);}
    JsonNode publish(JsonNode review) throws Exception {return response(http(user,"POST",root()+"/listings/"+review.path("listingId").asText()+"/transitions",Map.of("commandId",UUID.randomUUID().toString(),"expectedVersion",review.path("version").asLong(),"transition","PUBLISH")),200);}
    long events(){return jdbc.queryForObject("select count(*) from outbox_events where payload like ?",Long.class,"%"+tenant+"%");}
    long listings(){return jdbc.queryForObject("select count(*) from marketplace_listing where tenant_id=? and admitted_at is not null",Long.class,tenant);}
    long reviews(){return jdbc.queryForObject("select count(*) from marketplace_review where tenant_id=?",Long.class,tenant);}
    long commands(){return jdbc.queryForObject("select count(*) from marketplace_command where tenant_id=?",Long.class,tenant);}
    List<Long> state(){return List.of(listings(),reviews(),commands(),events(),
        jdbc.queryForObject("select coalesce(sum(aggregate_version),0) from marketplace_listing where tenant_id=? and admitted_at is not null",Long.class,tenant),
        jdbc.queryForObject("select coalesce(sum(aggregate_version),0) from marketplace_review where tenant_id=?",Long.class,tenant),
        jdbc.queryForObject("select count(*) from marketplace_review_decision d join marketplace_review r on r.id=d.review_id where r.tenant_id=?",Long.class,tenant),
        jdbc.queryForObject("select count(*) from marketplace_review_comment c join marketplace_review r on r.id=c.review_id where r.tenant_id=?",Long.class,tenant),
        jdbc.queryForObject("select count(*) from marketplace_review_thread t join marketplace_review r on r.id=t.review_id where r.tenant_id=? and t.resolved",Long.class,tenant),
        jdbc.queryForObject("select coalesce(sum(case publish_status when 'PUBLISHED' then 1 when 'ARCHIVED' then 2 else 0 end),0) from media_asset where tenant_id=?",Long.class,tenant));}
    List<java.net.http.HttpResponse<String>> race(java.util.concurrent.Callable<java.net.http.HttpResponse<String>> a,java.util.concurrent.Callable<java.net.http.HttpResponse<String>> b) throws Exception {
        try(var connection=context.getBean(javax.sql.DataSource.class).getConnection();var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            connection.setAutoCommit(false);
            try(var lock=connection.prepareStatement("select id from workspace where id=? for update")){lock.setString(1,workspace);lock.executeQuery().close();}
            var first=pool.submit(a);var second=pool.submit(b);
            try {
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(()->jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'",Integer.class)>=2);
            } finally {connection.commit();}
            return List.of(first.get(20,java.util.concurrent.TimeUnit.SECONDS),second.get(20,java.util.concurrent.TimeUnit.SECONDS));
        }
    }
}
