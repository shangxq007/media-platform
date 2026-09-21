package com.example.platform;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.identity.app.AccountMembershipService;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.prompt.app.PromptTemplateService;
import com.example.platform.prompt.domain.*;
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
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.autoconfigure.exclude=io.temporal.spring.boot.autoconfigure.RootNamespaceAutoConfiguration,io.temporal.spring.boot.autoconfigure.NonRootNamespaceAutoConfiguration",
 "spring.graphql.enabled=true","spring.graphql.schema.locations=classpath*:graphql/**/,classpath:ep22-graphql/",
 "app.security.enabled=true","app.security.oauth2.enabled=false","app.identity.api-key-auth-enabled=false",
 "app.outbox.dispatcher-enabled=false","storage.s3.enabled=false","app.security.jwt.secret-key=ep22-graphql-http-key-at-least-256-bits"})
@ActiveProfiles({"test","preview"})
class FederationOwnerReadsHttpTest extends PostgresTestContainerSupport {
 @LocalServerPort int port; @Autowired JdbcTemplate db; @Autowired AccountMembershipService accounts;
 @Autowired PromptTemplateService prompts;
 final HttpClient client=HttpClient.newHttpClient();final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
 record Actor(String id,String tenant,String token){}
 Actor actor(){String id=UUID.randomUUID().toString(),tenant="t-"+id;
  db.update("insert into tenant(id,name,status,created_at) values (?,?,'ACTIVE',now())",tenant,tenant);
  db.update("insert into \"user\"(id,tenant_id,username,email,role,status,created_at) values (?,?,?,?,'MEMBER','ACTIVE',now())",id,tenant,id,id+"@test.invalid");
  var op=CanonicalActor.system("system:identity-provisioning",tenant);String account=accounts.provisionVerifiedAccount(op,"urn:media-platform:local-hmac",id);accounts.linkMembership(op,account,tenant,id);
  String token=io.jsonwebtoken.Jwts.builder().subject(id).claim("tenantId",tenant).claim("roles",List.of("ADMIN")).expiration(new Date(System.currentTimeMillis()+600000)).signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor("ep22-graphql-http-key-at-least-256-bits".getBytes(StandardCharsets.UTF_8))).compact();return new Actor(id,tenant,token);
 }
 HttpResponse<String> query(Actor a,String q) throws Exception {
  var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/graphql")).header("Content-Type","application/json").header("X-User-Id","forged").header("X-Roles","ADMIN");
  if(a!=null)req.header("Authorization","Bearer "+a.token());
  return client.send(req.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("query",q)))).build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void actualGraphQLUsesVerifiedIdentityAndRejectsForgedAdminAndAnonymous() throws Exception {
  var a=actor();var r=query(a,"{ meOverview { id displayName currentTenant { id name } } }");assertEquals(200,r.statusCode(),r.body());
  var body=json.readTree(r.body());assertFalse(body.has("errors"),r.body());assertEquals(a.id(),body.at("/data/meOverview/id").asText());assertEquals(a.tenant(),body.at("/data/meOverview/currentTenant/id").asText());
  assertEquals(401,query(null,"{ meOverview { id } }").statusCode());
  assertTrue(json.readTree(query(a,"{ adminDashboard { renderStats { submitted } } }").body()).has("errors"));
 }
 @Test void sameTemplateAcrossTenantsFiltersExecutionIdentityAndRecoversAfterMissing() throws Exception {
  var a=actor();var b=actor();String id="review-template-"+UUID.randomUUID();var now=java.time.OffsetDateTime.now();
  prompts.hydrateTemplate(new PromptTemplate(id,"Shared catalog","description","general",List.of(),a.id(),PromptTemplateStatus.ACTIVE,"v1",null,now,now));
  for(var actor:List.of(a,b))prompts.hydrateExecution(new PromptExecutionRun("execution-"+actor.id(),id,"v1",actor.tenant(),actor.id(),"test","test","hash","redacted","{}",null,PromptExecutionStatus.SUCCEEDED,PromptRiskLevel.LOW,0,0,now,now,null,null,null,null));
  String q="{ promptTemplateDetail(id: \""+id+"\") { id executions { executionId } } }";
  for(var actor:List.of(a,b)){var r=query(actor,q);var body=json.readTree(r.body());assertFalse(body.has("errors"),r.body());var runs=body.at("/data/promptTemplateDetail/executions");assertEquals(1,runs.size(),r.body());assertEquals("execution-"+actor.id(),runs.get(0).get("executionId").asText());}
  assertTrue(json.readTree(query(a,"{ promptTemplateDetail(id: \"missing\") { id } }").body()).has("errors"));
  assertFalse(json.readTree(query(a,q).body()).has("errors"));
 }
 @Test void foreignProjectAndWorkspaceSelectorsFailWithoutOwnerWrites() throws Exception {
  var a=actor();var b=actor();String project="p-"+UUID.randomUUID();
  db.update("insert into project(id,tenant_id,name,status,created_at) values (?,?,?,'ACTIVE',now())",project,b.tenant(),"Foreign");
  long before=db.queryForObject("select count(*) from render_job",Long.class);
  var denied=query(a,"{ exportPanelState(projectId: \""+project+"\") { project { id } } }");
  assertTrue(json.readTree(denied.body()).has("errors"),denied.body());
  var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/graphql")).header("Content-Type","application/json").header("Authorization","Bearer "+a.token()).header("X-Workspace-Id","foreign-workspace")
   .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("query","{ meOverview { id } }")))).build();
  var response=client.send(req,HttpResponse.BodyHandlers.ofString());
  assertTrue(response.statusCode()>=400 || json.readTree(response.body()).has("errors"),response.body());
  assertEquals(before,db.queryForObject("select count(*) from render_job",Long.class));
  var retry=query(a,"{ meOverview { id } }");assertFalse(json.readTree(retry.body()).has("errors"),retry.body());
 }
 @Test void sourceBoundaryHasNoPrivateOwnerImports() throws Exception {
  var root=java.nio.file.Path.of("").toAbsolutePath();while(!java.nio.file.Files.exists(root.resolve("AGENTS.md")))root=root.getParent();
  String health=java.nio.file.Files.readString(root.resolve("platform-app/src/main/java/com/example/platform/health/HealthController.java"));
  for(String table:List.of("outbox_events","client_export_session","render_job"))assertFalse(health.contains(table),"Displaced health table access: "+table);
  try(var files=java.nio.file.Files.walk(root.resolve("federation-query-module/src/main/java/com/example/platform/federation/graphql"))){for(var p:files.filter(x->x.toString().endsWith(".java")).toList()){
   String s=java.nio.file.Files.readString(p);for(String domain:List.of("identity","billing","render","prompt","extension"))for(String internal:List.of("app","infrastructure"))assertFalse(s.contains("import com.example.platform."+domain+"."+internal+"."),p.toString());
  }}
 }
}
