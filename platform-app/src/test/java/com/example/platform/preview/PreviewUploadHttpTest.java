package com.example.platform.preview;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test","preview"})
@TestPropertySource(properties={"app.security.enabled=true","app.security.oauth2.enabled=false",
        "app.identity.api-key-auth-enabled=false","app.outbox.dispatcher-enabled=false","storage.s3.enabled=false",
        "app.security.jwt.secret-key=ep16-isolated-preview-upload-key-at-least-256-bits"})
class PreviewUploadHttpTest extends PostgresTestContainerSupport {
    static final String SECRET="ep16-isolated-preview-upload-key-at-least-256-bits";
    static final Path ROOT=createRoot();
    static Path createRoot(){try{return Files.createTempDirectory("ep16-preview-http-");}catch(Exception e){throw new IllegalStateException(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("app.storage.local-root",ROOT::toString);}
    @AfterAll static void cleanup() throws Exception {try(var files=Files.walk(ROOT)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    final HttpClient http=HttpClient.newHttpClient();
    final ObjectMapper json=new ObjectMapper();
    String tenant;
    @BeforeEach void scope(){
        tenant="prev-"+UUID.randomUUID();
        jdbc.update("insert into tenant(id,name,status,created_at) values (?,?,'ACTIVE',now())",tenant,"Preview fixture");
        String role="prev-"+UUID.randomUUID(),permission="prev-"+UUID.randomUUID();
        jdbc.update("insert into role(id,role_key,name,scope,created_at) values (?,?,?,'TENANT',now())",role,role,"Preview upload");
        jdbc.update("insert into permission(id,permission_key,name,created_at) values (?,'WRITE','Write',now()) on conflict(permission_key) do nothing",permission);
        permission=jdbc.queryForObject("select id from permission where permission_key='WRITE'",String.class);
        jdbc.update("insert into role_permission(id,role_id,permission_id,created_at) values (?,?,?,now())","rp-"+UUID.randomUUID(),role,permission);
        jdbc.update("insert into user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at) values (?,?,?,'preview-user',?,now())","ra-"+UUID.randomUUID(),tenant,tenant,role);
    }
    String token(String user){return io.jsonwebtoken.Jwts.builder().subject(user).claim("tenantId",tenant).claim("roles",List.of("USER"))
            .expiration(new Date(System.currentTimeMillis()+600000)).signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();}
    byte[] media() throws Exception {try(var input=getClass().getResourceAsStream("/render-output-fixture.mp4")){return Objects.requireNonNull(input).readAllBytes();}}
    HttpResponse<String> upload(String key,String bearer,byte[] bytes,String tenantHeader) throws Exception {
        String boundary="ep16-boundary";
        var body=new java.io.ByteArrayOutputStream();
        body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"input.mp4\"\r\nContent-Type: video/mp4\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(bytes);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/preview/media"))
                .header("Content-Type","multipart/form-data; boundary="+boundary);
        if(key!=null)request.header("Idempotency-Key",key);
        if(bearer!=null)request.header("Authorization","Bearer "+bearer);
        if(tenantHeader!=null)request.header("X-Tenant-Id",tenantHeader);
        return http.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
    }
    long count(String table){return jdbc.queryForObject("select count(*) from "+table+" where tenant_id=?",Long.class,tenant);}
    @Test void realHttpUploadAndExplicitRetryKeepResponseAndOneProduct() throws Exception {
        String key="request-"+UUID.randomUUID();var first=upload(key,token("preview-user"),media(),null);
        assertEquals(200,first.statusCode(),first.body());assertEquals(key,first.headers().firstValue("Idempotency-Key").orElseThrow());
        var response=json.readTree(first.body());Set<String> fields=new HashSet<>();response.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("mediaId","size"),fields);assertEquals(Integer.toString(media().length),response.get("size").asText());
        var replay=upload(key,token("preview-user"),media(),null);assertEquals(200,replay.statusCode(),replay.body());assertEquals(response,json.readTree(replay.body()));
        assertEquals(1,count("product"));assertEquals(1,count("storage_write_intent"));assertEquals(0,count("media_asset"));
        assertEquals("READY",jdbc.queryForObject("select status from product where tenant_id=?",String.class,tenant));
        assertEquals(409,upload(key,token("preview-user"),new byte[]{1,2,3},null).statusCode());assertEquals(1,count("product"));
    }
    @Test void missingOrDeniedAuthenticationAndWrongTenantCannotWrite() throws Exception {
        assertEquals(401,upload("missing",null,media(),null).statusCode());
        assertEquals(403,upload("denied",token("denied"),media(),null).statusCode());
        assertEquals(403,upload("foreign",token("preview-user"),media(),"another-tenant").statusCode());
        assertEquals(0,count("storage_write_intent"));assertEquals(0,count("product"));
    }
    @Test void omittedKeyCreatesDistinctUploadsAndEmptyContentIsRejected() throws Exception {
        var first=upload(null,token("preview-user"),media(),null);var second=upload(null,token("preview-user"),media(),null);
        assertEquals(200,first.statusCode(),first.body());assertEquals(200,second.statusCode(),second.body());
        assertNotEquals(first.headers().firstValue("Idempotency-Key"),second.headers().firstValue("Idempotency-Key"));
        assertNotEquals(json.readTree(first.body()).get("mediaId"),json.readTree(second.body()).get("mediaId"));assertEquals(2,count("product"));
        assertEquals(400,upload("empty",token("preview-user"),new byte[0],null).statusCode());assertEquals(2,count("storage_write_intent"));
    }
}
