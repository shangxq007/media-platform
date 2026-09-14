package com.example.platform.identity;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.identity.app.WorkspaceService;
import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.api.workspace.*;
import com.example.platform.shared.web.TenantContext;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test","preview"})
@TestPropertySource(properties={"app.security.enabled=true","app.security.oauth2.enabled=false",
        "app.identity.api-key-auth-enabled=false","app.outbox.dispatcher-enabled=false","storage.s3.enabled=false",
        "app.security.jwt.secret-key=ep12-workspace-http-key-at-least-256-bits"})
class WorkspaceAuthorityHttpTest extends PostgresTestContainerSupport {
    static final String SECRET="ep12-workspace-http-key-at-least-256-bits";
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkspaceService owner;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.context.ApplicationContext context;
    final HttpClient http=HttpClient.newHttpClient();
    final ObjectMapper json=new ObjectMapper();
    String tenant,creator,member,outsider,foreign;
    @BeforeEach void scope(){
        tenant="wt-"+UUID.randomUUID();
        jdbc.update("insert into tenant(id,name,status,created_at) values (?,?,'ACTIVE',now())",tenant,"Workspace tests");
        creator=user(tenant);member=user(tenant);outsider=user(tenant);
        String other="other-"+UUID.randomUUID();
        jdbc.update("insert into tenant(id,name,status,created_at) values (?,?,'ACTIVE',now())",other,"Other tenant");
        foreign=user(other);
    }
    String user(String tenant){String id="wu-"+UUID.randomUUID();jdbc.update("insert into \"user\"(id,tenant_id,username,email,role,status,created_at) values (?,?,?,?,'MEMBER','ACTIVE',now())",id,tenant,id,id+"@test.invalid");
        var provisioning=context.getBean(com.example.platform.identity.app.AccountMembershipService.class);
        var operator=com.example.platform.shared.authorization.CanonicalActor.system("system:identity-provisioning",tenant);
        var account=provisioning.provisionVerifiedAccount(operator,"urn:media-platform:local-hmac",id);
        provisioning.linkMembership(operator,account,tenant,id);return id;}
    String token(String user,String tenant){return io.jsonwebtoken.Jwts.builder().subject(user).claim("tenantId",tenant).claim("roles",List.of("ADMIN"))
            .expiration(new Date(System.currentTimeMillis()+600000)).signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();}
    HttpResponse<String> call(String method,String path,Object body,String actor) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json").header("X-User-ID",creator);
        if(actor!=null)request.header("Authorization","Bearer "+token(actor,tenant));
        return http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    String create() throws Exception {var r=call("POST","/api/product/workspace",Map.of("name","Workspace","ownerId",creator),creator);assertEquals(200,r.statusCode(),r.body());return json.readTree(r.body()).get("workspaceId").asText();}
    String path(String workspace){return "/api/product/workspace/"+workspace;}
    HttpResponse<String> add(String ws,String target,String role,String actor) throws Exception{return call("POST",path(ws)+"/members",Map.of("userId",target,"role",role),actor);}
    long count(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
    String memberId(String ws,String user){return jdbc.queryForObject("select id from workspace_member where workspace_id=? and user_id=?",String.class,ws,user);}
    long ownerAudits(){return count("select count(*) from audit_records where action in ('WORKSPACE_CREATE','MEMBER_ADD','MEMBER_REMOVE','ROLE_ASSIGN','ROLE_REVOKE')");}
    long members(String ws){return count("select count(*) from workspace_member where workspace_id=? and status='ACTIVE'",ws);}
    @Test void createUsesDistinctCanonicalIdentityAndAuthenticatedOwnerAndSurvivesReconstruction() throws Exception {
        String ws=create();assertNotEquals(tenant,ws);assertEquals(1,members(ws));
        assertEquals(creator,jdbc.queryForObject("select user_id from workspace_member where workspace_id=? and role='OWNER'",String.class,ws));
        assertEquals(tenant,jdbc.queryForObject("select tenant_id from workspace where id=?",String.class,ws));
        assertEquals(1,count("select count(*) from audit_records where resource_id=? and action='WORKSPACE_CREATE'",ws));
        assertEquals(200,call("GET","/api/workspaces/"+ws,null,creator).statusCode());
        asActor(creator,()->{
            var rebuilt=new WorkspaceService(context.getBean(com.example.platform.identity.infrastructure.WorkspaceRepository.class),
                    context.getBean(com.example.platform.identity.infrastructure.WorkspaceMemberRepository.class),context.getBean(com.example.platform.identity.infrastructure.WorkspaceGroupRepository.class),
                    context.getBean(com.example.platform.identity.infrastructure.RoleRepository.class),context.getBean(com.example.platform.shared.audit.AuditPort.class),
                    context.getBean(com.example.platform.identity.api.authorization.CanonicalActorResolver.class),context.getBean(com.example.platform.identity.app.UserRepository.class));
            assertEquals(ws,rebuilt.getWorkspace(ws).id());assertEquals(creator,rebuilt.listMembers(ws).getFirst().userId());
        });
    }
    @Test void federationReadUsesActualOwnerContextAndRejectsAfterRemoval() throws Exception {
        String ws=create();assertEquals(200,add(ws,member,"VIEWER",creator).statusCode());
        var loader=context.getBean(com.example.platform.federation.graphql.dataloader.WorkspaceDataLoader.class);
        asActor(member,()->assertEquals(ws,loader.load(Set.of(ws)).toCompletableFuture().join().get(ws).get("id")));
        assertEquals(204,call("DELETE",path(ws)+"/members/"+member,null,creator).statusCode());
        asActor(member,()->assertThrows(CompletionException.class,()->loader.load(Set.of(ws)).toCompletableFuture().join()));
        assertEquals(1,members(ws));
    }
    @Test void noIdentityOrForgedOwnerCannotCreate() throws Exception {
        assertEquals(401,call("POST","/api/product/workspace",Map.of("name","bad"),null).statusCode());
        assertEquals(403,call("POST","/api/product/workspace",Map.of("name","bad","ownerId",outsider),creator).statusCode());
        assertEquals(403,call("POST","/api/workspaces?tenantId=other",Map.of("name","bad"),creator).statusCode());
        assertEquals(0,count("select count(*) from workspace where tenant_id=?",tenant));
        assertEquals(0,count("select count(*) from workspace_member where user_id=?",creator));
    }
    @Test void untrustedTenantOrNonmemberCannotReadMutateOrDiscover() throws Exception {
        String ws=create();long audits=count("select count(*) from audit_records"),ownerAudits=ownerAudits();
        for(String actor:List.of(outsider,foreign)){
            assertEquals(403,call("GET",path(ws),null,actor).statusCode());
            assertEquals(403,add(ws,member,"ADMIN",actor).statusCode());
            assertEquals(403,call("DELETE",path(ws)+"/members/"+creator,null,actor).statusCode());
        }
        assertEquals(401,call("GET",path(ws),null,null).statusCode());
        assertEquals(403,call("GET","/api/product/workspace/user/"+creator,null,outsider).statusCode());
        assertEquals(1,members(ws));assertEquals(ownerAudits,ownerAudits());assertTrue(count("select count(*) from audit_records") > audits, "Security denials remain audited");
    }
    @Test void aValidForeignTenantTokenCannotAccessOrMutateAnExistingWorkspace() throws Exception {
        String ws=create(),other=jdbc.queryForObject("select tenant_id from \"user\" where id=?",String.class,foreign);
        long before=ownerAudits();
        for(String method:List.of("GET","POST","DELETE")) {
            String suffix=method.equals("GET")?"":method.equals("POST")?"/members":"/members/"+creator;
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path(ws)+suffix))
                    .header("Authorization","Bearer "+token(foreign,other)).header("Content-Type","application/json")
                    .header("X-User-ID",creator).method(method,method.equals("POST")?
                            HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("userId",member,"role","ADMIN"))):HttpRequest.BodyPublishers.noBody()).build();
            var result=http.send(request,HttpResponse.BodyHandlers.ofString());assertEquals(403,result.statusCode(),result.body());
        }
        assertEquals(1,members(ws));assertEquals(before,ownerAudits());
    }
    @Test void validMembershipDuplicateConflictAndRemovalAffectDiscoveryAndRoleAuthorization() throws Exception {
        String ws=create();var first=add(ws,member,"EDITOR",creator);assertEquals(200,first.statusCode(),first.body());
        String id=memberId(ws,member);
        assertEquals(first.body(),add(ws,member,"EDITOR",creator).body());assertEquals(409,add(ws,member,"ADMIN",creator).statusCode());
        assertEquals(2,members(ws));assertEquals(1,count("select count(*) from audit_records where resource_id=? and action='MEMBER_ADD'",id));
        assertEquals(403,add(ws,outsider,"VIEWER",member).statusCode());
        assertEquals(200,call("GET",path(ws),null,member).statusCode());
        assertEquals(1,json.readTree(call("GET","/api/product/workspace/user/"+member,null,member).body()).size());
        String role="wr-"+UUID.randomUUID();jdbc.update("insert into role(id,role_key,name,scope,created_at) values (?,?,?,'WORKSPACE',now())",role,role,"Test");
        jdbc.update("insert into user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at) values (?,?,?,?,?,now())","ra-"+UUID.randomUUID(),tenant,ws,member,role);
        assertEquals(204,call("DELETE",path(ws)+"/members/"+member,null,creator).statusCode());
        assertEquals(0,count("select count(*) from user_role_assignment where workspace_id=? and user_id=?",ws,member));
        assertEquals(403,call("GET",path(ws),null,member).statusCode());
        assertEquals(0,json.readTree(call("GET","/api/product/workspace/user/"+member,null,member).body()).size());
        assertEquals(204,call("DELETE",path(ws)+"/members/"+member,null,creator).statusCode());
        assertEquals(200,add(ws,member,"VIEWER",creator).statusCode());assertEquals(2,members(ws));
        assertEquals(1,count("select count(*) from workspace_member where workspace_id=? and user_id=?",ws,member));
    }
    @Test void wrongWorkspaceMemberRoleCannotBeAssignedOrRevoked() throws Exception {
        String first=create(),second=create();var response=add(first,member,"EDITOR",creator);String id=memberId(first,member);
        assertEquals(403,call("GET",path(second),null,member).statusCode());
        long before=count("select count(*) from user_role_assignment");
        assertEquals(403,call("POST","/api/workspaces/"+second+"/members/"+id+"/roles",Map.of("roleKey","ADMIN","assignedBy",creator),creator).statusCode());
        assertEquals(403,call("DELETE","/api/workspaces/"+second+"/members/"+id+"/roles/ADMIN",null,creator).statusCode());
        assertEquals(before,count("select count(*) from user_role_assignment"));assertEquals(2,members(first));assertEquals(1,members(second));
    }
    @Test void assignedByCannotImpersonateAndRoleRevocationIsWorkspaceScoped() throws Exception {
        String first=create(),second=create();
        assertEquals(200,add(first,member,"EDITOR",creator).statusCode());String a=memberId(first,member);
        assertEquals(200,add(second,member,"EDITOR",creator).statusCode());String b=memberId(second,member);
        String role="wr-"+UUID.randomUUID();jdbc.update("insert into role(id,role_key,name,scope,created_at) values (?,?,?,'WORKSPACE',now())",role,role,"Scoped role");
        for(String suffix:List.of(first+"/members/"+a,second+"/members/"+b)){
            var result=call("POST","/api/workspaces/"+suffix+"/roles",Map.of("roleKey",role,"assignedBy",outsider),creator);
            assertEquals(200,result.statusCode(),result.body());
        }
        assertEquals(2,count("select count(*) from user_role_assignment where user_id=? and assigned_by=?",member,creator));
        assertEquals(0,count("select count(*) from user_role_assignment where user_id=? and assigned_by=?",member,outsider));
        assertEquals(200,call("POST","/api/workspaces/"+first+"/members/"+a+"/roles",Map.of("roleKey",role,"assignedBy",outsider),creator).statusCode());
        assertEquals(2,count("select count(*) from user_role_assignment where user_id=?",member));
        assertEquals(200,call("DELETE","/api/workspaces/"+first+"/members/"+a+"/roles/"+role,null,creator).statusCode());
        assertEquals(0,count("select count(*) from user_role_assignment where user_id=? and workspace_id=?",member,first));
        assertEquals(1,count("select count(*) from user_role_assignment where user_id=? and workspace_id=?",member,second));
    }
    @Test void concurrentAddsSerializeWithoutDuplicateStateOrAudit() throws Exception {
        String ws=create();var start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)){
            var a=workers.submit(()->{start.await();return add(ws,member,"EDITOR",creator);});
            var b=workers.submit(()->{start.await();return add(ws,member,"EDITOR",creator);});start.countDown();
            var ra=a.get(30,TimeUnit.SECONDS);var rb=b.get(30,TimeUnit.SECONDS);assertEquals(200,ra.statusCode(),ra.body());assertEquals(200,rb.statusCode(),rb.body());assertEquals(ra.body(),rb.body());
        }
        assertEquals(2,members(ws));assertEquals(1,count("select count(*) from workspace_member where workspace_id=? and user_id=?",ws,member));
    }
    @Test void concurrentOwnerDeparturePreservesOneOwner() throws Exception {
        String ws=create();assertEquals(409,call("DELETE",path(ws)+"/members/"+creator,null,creator).statusCode());
        assertEquals(200,add(ws,member,"OWNER",creator).statusCode());var start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)){
            var a=workers.submit(()->{start.await();return call("DELETE",path(ws)+"/members/"+creator,null,creator).statusCode();});
            var b=workers.submit(()->{start.await();return call("DELETE",path(ws)+"/members/"+member,null,member).statusCode();});start.countDown();
            assertEquals(List.of(204,409),java.util.stream.Stream.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS)).sorted().toList());
        }
        assertEquals(1,count("select count(*) from workspace_member where workspace_id=? and role='OWNER' and status='ACTIVE'",ws));
    }
    @Test void actualOwnerTransactionRollbackRemovesWorkspaceMemberAndAuditThenRetryWorks() throws Exception {
        long audit=count("select count(*) from audit_records");
        asActor(creator,()->assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{
            var created=owner.createWorkspace(tenant,new CreateWorkspaceRequest("rolled back",null,null));
            assertEquals(1,members(created.id()));throw new IllegalStateException("after owner writes");
        })));
        assertEquals(0,count("select count(*) from workspace where tenant_id=?",tenant));assertEquals(0,count("select count(*) from workspace_member where user_id=?",creator));assertEquals(audit,count("select count(*) from audit_records"));
        assertNotNull(create());
    }
    @Test void membershipRevocationRollbackRestoresMemberAndAssignments() throws Exception {
        String ws=create();assertEquals(200,add(ws,member,"EDITOR",creator).statusCode());
        String role="wr-"+UUID.randomUUID();jdbc.update("insert into role(id,role_key,name,scope,created_at) values (?,?,?,'WORKSPACE',now())",role,role,"Rollback role");
        jdbc.update("insert into user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at) values (?,?,?,?,?,now())","ra-"+UUID.randomUUID(),tenant,ws,member,role);
        long before=count("select count(*) from audit_records");
        asActor(creator,()->assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{owner.removeMember(ws,member);assertEquals(1,members(ws));assertEquals(0,count("select count(*) from user_role_assignment where workspace_id=? and user_id=?",ws,member));throw new IllegalStateException("after removal");})));
        assertEquals(1,count("select count(*) from user_role_assignment where workspace_id=? and user_id=?",ws,member));
        assertEquals(2,members(ws));assertEquals(before,count("select count(*) from audit_records"));assertEquals(200,call("GET",path(ws),null,member).statusCode());
    }
    @Test void workspaceEntitlementRoutesCannotBypassRevokedOrForeignMembership() throws Exception {
        String ws=create();assertEquals(200,add(ws,member,"VIEWER",creator).statusCode());
        assertEquals(204,call("DELETE",path(ws)+"/members/"+member,null,creator).statusCode());
        long grants=count("select count(*) from workspace_member_entitlement_grant");
        for(String actor:List.of(member,outsider,foreign)) {
            assertEquals(403,call("GET","/api/workspaces/"+ws+"/entitlements/grants",null,actor).statusCode());
            assertEquals(403,call("POST","/api/workspaces/"+ws+"/entitlements/grants",Map.of("memberId",creator,"featureKey","render","quotaAmount",1),actor).statusCode());
            assertEquals(403,call("POST","/api/workspaces/"+ws+"/entitlements/preview",Map.of("userId",creator,"preset","default_720p","outputFormat","mp4"),actor).statusCode());
        }
        assertEquals(grants,count("select count(*) from workspace_member_entitlement_grant"));
        var allowed=call("GET","/api/workspaces/"+ws+"/entitlements/grants",null,creator);
        assertEquals(200,allowed.statusCode(),allowed.body());assertEquals(0,json.readTree(allowed.body()).get("grants").size());
    }
    @Test void unsupportedMetadataRejectsExplicitlyAndUnknownRoleCannotMutate() throws Exception {
        String ws=create();assertEquals(410,call("POST",path(ws)+"/projects",Map.of("projectId",tenant),creator).statusCode());
        assertEquals(410,call("POST",path(ws)+"/sessions",Map.of("projectId",tenant),creator).statusCode());
        assertEquals(403,call("POST",path(ws)+"/projects",Map.of(),outsider).statusCode());
        assertEquals(400,add(ws,member,"SUPERUSER",creator).statusCode());assertEquals(403,add(ws,foreign,"EDITOR",creator).statusCode());assertEquals(1,members(ws));
    }
    void asActor(String actor,Runnable action){
        var request=new MockHttpServletRequest();request.setAttribute("jwt.subject",actor);request.setAttribute("auth.subject",actor);request.setAttribute("jwt.issuer","urn:media-platform:local-hmac");request.setAttribute("jwt.tenantId",tenant);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));TenantContext.set(tenant);
        try{action.run();}finally{RequestContextHolder.resetRequestAttributes();TenantContext.clear();}
    }
}
