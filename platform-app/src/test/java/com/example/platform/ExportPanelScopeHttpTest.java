package com.example.platform;

import com.example.platform.entitlement.api.EntitlementDecisionQuery;
import com.example.platform.entitlement.domain.AccessCheckRequest;
import com.example.platform.render.api.ExportOptionQuery;
import com.example.platform.shared.authorization.CanonicalActor;
import java.util.*;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real authentication, Identity authorization and Entitlement decisions; GraphQL is test-enabled only. */
class ExportPanelScopeHttpTest extends FederationOwnerReadsHttpTest {
 @MockitoSpyBean EntitlementDecisionQuery decisions;
 @MockitoSpyBean ExportOptionQuery options;
 HttpResponse<String> http(Actor a,String method,String path,Object data,String workspace) throws Exception {
  var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json").header("X-User-Id","forged").header("X-Roles","ADMIN").header("X-Platform-Administrator","true");
  if(a!=null)req.header("Authorization","Bearer "+a.token());if(workspace!=null)req.header("X-Workspace-Id",workspace);
  return client.send(req.method(method,data==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build(),HttpResponse.BodyHandlers.ofString());
 }
 HttpResponse<String> gql(Actor a,String q,String ws)throws Exception{return http(a,"POST","/graphql",Map.of("query",q),ws);}
 String workspace(Actor a)throws Exception{var r=http(a,"POST","/api/product/workspace",Map.of("name","Correction regression"),null);assertEquals(200,r.statusCode(),r.body());return json.readTree(r.body()).get("workspaceId").asText();}
 List<AccessCheckRequest> requests(){return mockingDetails(decisions).getInvocations().stream().filter(i->i.getMethod().getName().equals("evaluate")).map(i->(AccessCheckRequest)i.getArgument(0)).toList();}
 Actor sameTenant(Actor a){String id=UUID.randomUUID().toString();db.update("insert into \"user\"(id,tenant_id,username,email,role,status,created_at) values (?,?,?,?,'MEMBER','ACTIVE',now())",id,a.tenant(),id,id+"@test.invalid");var op=CanonicalActor.system("system:identity-provisioning",a.tenant());String account=accounts.provisionVerifiedAccount(op,"urn:media-platform:local-hmac",id);accounts.linkMembership(op,account,a.tenant(),id);String token=io.jsonwebtoken.Jwts.builder().subject(id).claim("tenantId",a.tenant()).claim("roles",List.of("ADMIN")).expiration(new Date(System.currentTimeMillis()+600000)).signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor("ep22-graphql-http-key-at-least-256-bits".getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();return new Actor(id,a.tenant(),token);}
 void grantRead(Actor a,String p){
  String id="r-"+UUID.randomUUID();db.update("insert into role(id,role_key,name,scope,created_at) values (?,?,?,'WORKSPACE',now())",id,id,id);
  db.update("insert into permission(id,permission_key,name,created_at) values (?,'READ','Read',now()) on conflict(permission_key) do nothing",id);
  String permission=db.queryForObject("select id from permission where permission_key='READ'",String.class);
  db.update("insert into role_permission(id,role_id,permission_id,created_at) values (?,?,?,now())",id,id,permission);
  db.update("insert into user_role_assignment(id,tenant_id,project_id,user_id,role_id,created_at) values (?,?,?,?,?,now())",id,a.tenant(),p,a.id(),id);
 }

 String project(Actor a, String ws) {
  String id = "export-" + UUID.randomUUID();
  db.update("insert into project(id,tenant_id,workspace_id,name,status,created_at) values (?,?,?,?,'ACTIVE',now())", id,a.tenant(),ws,"Export project");
  grantRead(a,id);
  return id;
 }
 String panel(String project) {
  return "{ exportPanelState(projectId:\""+project+"\") { project { id name } timelineSummary { durationSeconds } exportOptions { preset allowed reasonCode recommendedPreset providerCandidates } validation { allowed } } }";
 }
 void exactDecisionScope(Actor a, String ws) {
  assertFalse(requests().isEmpty(), "The real Entitlement decision must be invoked");
  for (var request : requests()) {
   assertEquals(a.tenant(), request.tenantId());
   assertEquals(a.id(), request.userId());
   assertEquals(a.id(), request.subjectId());
   assertEquals(ws, request.workspaceId());
  }
 }
 com.fasterxml.jackson.databind.JsonNode successfulPanel(Actor a, String p, String ws) throws Exception {
  var response=gql(a,panel(p),ws);
  assertEquals(200,response.statusCode(),response.body());
  var body=json.readTree(response.body());
  assertFalse(body.has("errors"),response.body());
  assertEquals(p,body.at("/data/exportPanelState/project/id").asText());
  return body.at("/data/exportPanelState/exportOptions");
 }
 Map<String,List<Map<String,Object>>> durableState() {
  var snapshot=new LinkedHashMap<String,List<Map<String,Object>>>();
  for(var table:List.of("render_job","project","workspace_member","outbox_events"))
   snapshot.put(table,db.queryForList("select * from "+table+" order by id"));
  return snapshot;
 }
 @Test void validatedWorkspaceReachesRealDecisionAndDefaultFreePanelSucceeds() throws Exception {
  var a=actor();String ws=workspace(a),p=project(a,ws);
  var expected=durableState();
  var policy=options.options("FREE");
  assertEquals("free_720p_watermarked",policy.getFirst().name());
  assertNull(policy.getFirst().provider());
  clearInvocations(decisions);
  var presets=successfulPanel(a,p,ws);
  exactDecisionScope(a,ws);
  assertEquals(1,presets.size());
  assertEquals("free_720p_watermarked",presets.get(0).get("preset").asText());
  assertTrue(presets.get(0).get("allowed").asBoolean());
  assertTrue(presets.get(0).get("reasonCode").isNull());
  assertTrue(presets.get(0).get("providerCandidates").isArray());
  assertEquals(0,presets.get(0).get("providerCandidates").size());
  assertEquals(expected,durableState());
 }
 @Test void workspaceAndProjectRejectionPrecedeDecisionAndSubsequentRequestsDoNotLeak() throws Exception {
  var owner=actor();var peer=sameTenant(owner);var foreign=actor();
  String ws=workspace(owner),p=project(owner,ws);
  String peerWs=workspace(peer),peerProject=project(peer,peerWs);
  var expected=durableState();
  clearInvocations(decisions);successfulPanel(owner,p,ws);exactDecisionScope(owner,ws);
  for(var rejected:List.of(peer,foreign)) {
   clearInvocations(decisions);
   var response=gql(rejected,panel(p),ws);
   assertTrue(response.statusCode()>=400||json.readTree(response.body()).has("errors"),response.body());
   assertTrue(requests().isEmpty(),"Unauthorized workspace must not reach Entitlement");
  }
  clearInvocations(decisions);
  var deniedProject=gql(peer,panel(p),null);
  assertTrue(json.readTree(deniedProject.body()).has("errors"),deniedProject.body());
  assertTrue(requests().isEmpty(),"Project authorization must still precede Entitlement");
  clearInvocations(decisions);successfulPanel(peer,peerProject,null);exactDecisionScope(peer,null);
  clearInvocations(decisions);successfulPanel(owner,p,null);exactDecisionScope(owner,null);
  clearInvocations(decisions);successfulPanel(peer,peerProject,peerWs);exactDecisionScope(peer,peerWs);
  assertEquals(expected,durableState());
 }
 @Test void neighboringOverviewTierAndCapabilitiesKeepTheValidatedScope() throws Exception {
  var a=actor();String ws=workspace(a);
  for(var selection:Arrays.asList(ws,null)) {
   clearInvocations(decisions);
   var response=gql(a,"{ meOverview { id currentTenant { tier } capabilities { featureKey allowed } } }",selection);
   assertFalse(json.readTree(response.body()).has("errors"),response.body());
   exactDecisionScope(a,selection);
  }
 }
 @Test void optionalProviderFormattingAndOwnerFailureRecoveryAreDistinct() throws Exception {
  var a=actor();String ws=workspace(a),p=project(a,ws);var expected=durableState();
  // Controlled owner projection tests formatting only, never provider execution/readiness.
  doReturn(List.of(new ExportOptionQuery.Option("controlled",true,"recommended","controlled-provider"))).when(options).options("FREE");
  try {
   var preset=successfulPanel(a,p,ws).get(0);
   assertEquals("controlled",preset.get("preset").asText());
   assertEquals("recommended",preset.get("recommendedPreset").asText());
   assertEquals(json.readTree("[\"controlled-provider\"]"),preset.get("providerCandidates"));
  } finally { doCallRealMethod().when(options).options("FREE"); }
  doThrow(new IllegalStateException("owner unavailable")).when(options).options("FREE");
  try {
   var failure=gql(a,panel(p),ws);var body=json.readTree(failure.body());
   assertTrue(body.has("errors"),failure.body());
   assertTrue(body.at("/data/exportPanelState").isNull()||body.at("/data").isNull(),failure.body());
  } finally { doCallRealMethod().when(options).options("FREE"); }
  var recovered=successfulPanel(a,p,ws).get(0);
  assertEquals("free_720p_watermarked",recovered.get("preset").asText());
  assertEquals(0,recovered.get("providerCandidates").size());
  assertEquals(expected,durableState());
 }
 @Test void actualOwnerDatabaseFailureDoesNotFabricatePanelAndRecoversWithoutWrites() throws Exception {
  var a=actor();String ws=workspace(a),p=project(a,ws);var expected=durableState();
  successfulPanel(a,p,ws);
  db.execute("alter table render_job rename to ep22_correction_hidden_render_job");
  try {
   clearInvocations(decisions);
   var response=gql(a,panel(p),ws);var body=json.readTree(response.body());
   assertTrue(body.has("errors"),response.body());
   assertTrue(body.at("/data/exportPanelState").isNull()||body.at("/data").isNull(),response.body());
   assertTrue(requests().isEmpty(),"Failed Render query must not continue into a successful panel");
  } finally { db.execute("alter table ep22_correction_hidden_render_job rename to render_job"); }
  successfulPanel(a,p,ws);
  assertEquals(expected,durableState());
 }

}
