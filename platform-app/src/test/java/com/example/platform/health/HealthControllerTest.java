package com.example.platform.health;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.outbox.operations.OutboxOperationalQuery;
import com.example.platform.render.api.RenderOperationalQuery;
import com.example.platform.identity.app.AccountMembershipService;
import com.example.platform.shared.authorization.CanonicalActor;
import java.util.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "app.security.enabled=true","app.security.oauth2.enabled=false","app.identity.api-key-auth-enabled=false",
 "app.outbox.dispatcher-enabled=false","storage.s3.enabled=false","app.security.jwt.secret-key=ep13-health-http-key-at-least-256-bits"})
@ActiveProfiles({"test","preview"})
class HealthControllerTest extends PostgresTestContainerSupport {
 @LocalServerPort int port;
 @Autowired JdbcTemplate db; @Autowired OutboxOperationalQuery outbox; @Autowired RenderOperationalQuery render;
 @Autowired AccountMembershipService accounts; @Autowired PlatformTransactionManager tm;
 final HttpClient client=HttpClient.newHttpClient();
 HttpResponse<String> get(String path,String token) throws Exception {
  var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
  if(token!=null)r.header("Authorization","Bearer "+token);
  return client.send(r.GET().build(),HttpResponse.BodyHandlers.ofString());
 }
 String token(boolean admin){
  String id=UUID.randomUUID().toString(),tenant="t-"+id;
  db.update("insert into tenant(id,name,status,created_at) values (?,?,'ACTIVE',now())",tenant,tenant);
  db.update("insert into \"user\"(id,tenant_id,username,email,role,status,created_at) values (?,?,?,?,'ADMIN','ACTIVE',now())",id,tenant,id,id+"@test.invalid");
  var operator=CanonicalActor.system("system:identity-provisioning",tenant);
  String account=accounts.provisionVerifiedAccount(operator,"urn:media-platform:local-hmac",id);
  accounts.linkMembership(operator,account,tenant,id);accounts.setPlatformAdministrator(operator,account,admin);
  return io.jsonwebtoken.Jwts.builder().subject(id).claim("tenantId",tenant).claim("roles",List.of("ADMIN"))
   .expiration(new Date(System.currentTimeMillis()+600000)).signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor("ep13-health-http-key-at-least-256-bits".getBytes(StandardCharsets.UTF_8))).compact();
 }
 @Test void actualHttpDistinguishesPlatformAdminFromTenantAdminAndPublicReadiness() throws Exception {
  assertEquals(401,get("/metrics/summary",null).statusCode());
  assertEquals(403,get("/metrics/summary",token(false)).statusCode());
  var allowed=get("/metrics/summary",token(true));assertEquals(200,allowed.statusCode(),allowed.body());
  assertTrue(allowed.body().contains("renderJobs"));assertTrue(allowed.body().contains("exportSessions"));
  assertEquals(200,get("/healthz",null).statusCode());
  var ready=get("/readyz",null);assertEquals(200,ready.statusCode());assertFalse(ready.body().contains("pendingCount"));
 }
 @Test void actualOwnerQueriesAreAccurateEmptyPopulatedAndReadOnly(){
  new TransactionTemplate(tm).executeWithoutResult(s->{
   db.update("delete from outbox_events");db.update("delete from client_export_session");db.update("delete from render_job");
   assertEquals(Map.of(),outbox.snapshot().counts());assertEquals(Map.of(),render.renderJobCounts());assertEquals(Map.of(),render.exportSessionCounts());
   db.update("insert into outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload,status,created_at,retry_count) values ('ep13-o','t','a','t',1,'{}','PENDING',now(),0)");
   db.update("insert into client_export_session(id,tenant_id,project_id,status) values ('ep13-e','t','p','CREATED')");
   db.update("insert into tenant(id,name,status,created_at) values ('t','t','ACTIVE',now())");
   db.update("insert into project(id,tenant_id,name,status,created_at) values ('p','t','p','ACTIVE',now())");
   db.update("insert into render_job(id,project_id,timeline_snapshot_id,profile,status,created_at,tenant_id,initiator_type,initiator_id,initiator_tenant_id) values ('ep13-r','p','s','x','QUEUED',now(),'t','USER','u','t')");
   assertEquals(Map.of("PENDING",1L),outbox.snapshot().counts());assertEquals(Map.of("CREATED",1L),render.exportSessionCounts());assertEquals(Map.of("QUEUED",1L),render.renderJobCounts());
   assertEquals(1,db.queryForObject("select count(*) from render_job where id='ep13-r'",Integer.class));s.setRollbackOnly();
  });
 }
 @Test void queryFailureIsNotHealthyOrEmptyAndRecoveryWorks() throws Exception {
  // Transactional table rename is visible only to this connection and always rolled back.
  new TransactionTemplate(tm).executeWithoutResult(s->{
   db.execute("alter table outbox_events rename to ep13_hidden_outbox");
   assertThrows(org.jooq.exception.DataAccessException.class,()->outbox.snapshot());s.setRollbackOnly();
  });
  assertNotNull(outbox.snapshot());
  var failed=org.mockito.Mockito.mock(OutboxOperationalQuery.class);
  org.mockito.Mockito.when(failed.snapshot()).thenThrow(new IllegalStateException("private SQL"));
  var c=new HealthController(db,db.getDataSource(),failed,render);
  var ready=c.readiness();assertEquals("degraded",ready.get("status"));assertFalse(ready.toString().contains("private SQL"));
  var request=new org.springframework.mock.web.MockHttpServletRequest();request.setAttribute("identity.platformAdministrator",true);
  assertEquals(Map.of("error","dependency unavailable"),c.metricsSummary(request).get("outbox"));
 }
}
