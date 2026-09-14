package com.example.platform.identity;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
class WorkspacePoolAuthorizationTest extends WorkspaceAuthorityHttpTest {
    @Override String token(String user,String tenant) {
        return io.jsonwebtoken.Jwts.builder().subject(user).claim("tenantId",tenant).claim("roles",List.of("MEMBER"))
                .expiration(new Date(System.currentTimeMillis()+600000)).signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
    @Test void independentOrdinaryNonmemberCanAllocateAndForeignTenantCanReadPool() throws Exception {
        String ws=create();
        context.getBean(com.example.platform.entitlement.app.WorkspaceEntitlementPoolService.class).createPool(ws,"review.feature",100,"MONTHLY",creator);
        var body=new HashMap<String,Object>();body.put("memberId",outsider);body.put("featureKey","review.feature");body.put("quotaAmount",10);
        body.put("startsAt",java.time.Instant.now().toString());body.put("sourceRef","probe-"+UUID.randomUUID());body.put("idempotencyKey","probe-"+UUID.randomUUID());body.put("reason","independent review");body.put("traceId","probe");
        assertEquals(403,call("POST","/api/workspaces/"+ws+"/entitlements/grants",body,outsider).statusCode(),"canonical route rejects same actor");
        assertEquals(200,add(ws,member,"VIEWER",creator).statusCode());
        var valid=new HashMap<>(body);valid.put("memberId",member);valid.put("sourceRef","valid-"+UUID.randomUUID());valid.put("idempotencyKey","valid-"+UUID.randomUUID());
        assertEquals(200,call("POST","/api/workspaces/"+ws+"/entitlements/grants",valid,creator).statusCode(),"positive canonical allocation");
        var allocation=call("POST","/api/workspaces/"+ws+"/entitlements/pool/allocate",body,outsider);
        long illicit=count("select count(*) from workspace_member_entitlement_grant where workspace_id=? and member_id=? and granted_by=?",ws,outsider,creator);
        String other=jdbc.queryForObject("select tenant_id from \"user\" where id=?",String.class,foreign);
        var foreignRead=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/workspaces/"+ws+"/entitlements/pool"))
                .header("Authorization","Bearer "+token(foreign,other)).GET().build(),HttpResponse.BodyHandlers.ofString());
        System.out.println("POOL_BOUNDARY_PROBE ordinaryNonmemberAllocation="+allocation.statusCode()+" forgedActorGrants="+illicit+" foreignTenantRead="+foreignRead.statusCode());
        assertAll(()->assertEquals(403,allocation.statusCode()),()->assertEquals(0,illicit),()->assertEquals(403,foreignRead.statusCode()));
    }

    String poolPath(String ws) { return "/api/workspaces/"+ws+"/entitlements/pool"; }
    String setupPool() throws Exception {
        String ws=create();
        context.getBean(com.example.platform.entitlement.app.WorkspaceEntitlementPoolService.class)
                .createPool(ws,"review.feature",100,"MONTHLY",creator);
        assertEquals(200,add(ws,member,"VIEWER",creator).statusCode());
        return ws;
    }
    Map<String,Object> allocation(String target) {
        return Map.of("memberId",target,"featureKey","review.feature","quotaAmount",10,
                "sourceRef","ir-"+UUID.randomUUID(),"idempotencyKey","ir-"+UUID.randomUUID(),"reason","regression","traceId","ir");
    }
    java.util.List<?> state(String ws) {
        return List.of(jdbc.queryForList("select * from workspace_entitlement_pool where workspace_id=? order by id",ws),
                jdbc.queryForList("select * from workspace_member_entitlement_grant where workspace_id=? order by id",ws),
                jdbc.queryForList("select * from entitlement_command_audit where tenant_id=? order by id",tenant),
                jdbc.queryForList("select * from audit_records where resource_type='WORKSPACE_POOL' and resource_id=? order by id",ws));
    }
    @Test void managerAllocationIgnoresForgedOwnerHeaderAndMemberReadsRemainAvailable() throws Exception {
        String ws=setupPool();assertEquals(200,add(ws,outsider,"ADMIN",creator).statusCode());
        var result=call("POST",poolPath(ws)+"/allocate",allocation(member),outsider);
        assertEquals(200,result.statusCode(),result.body());
        assertEquals(outsider,json.readTree(result.body()).get("grantedBy").asText());
        assertEquals(1,count("select count(*) from workspace_member_entitlement_grant where workspace_id=? and granted_by=?",ws,outsider));
        assertEquals(1,count("select count(*) from entitlement_command_audit where tenant_id=? and actor=?",tenant,outsider));
        assertEquals(1,count("select count(*) from audit_records where resource_id=? and action='workspace.pool.allocated' and actor_id=?",ws,outsider));
        assertEquals(10,count("select used_quota from workspace_entitlement_pool where workspace_id=?",ws));
        assertEquals(200,call("GET",poolPath(ws),null,member).statusCode());
        assertEquals(200,call("POST","/api/workspaces/"+ws+"/entitlements/preview",Map.of("userId",member,"preset","default_720p"),member).statusCode());
        var reclaim=Map.of("memberId",member,"featureKey","review.feature","quotaAmount",5);
        assertEquals(200,call("POST",poolPath(ws)+"/reclaim",reclaim,outsider).statusCode());
        assertEquals(5,count("select used_quota from workspace_entitlement_pool where workspace_id=?",ws));
        assertEquals(1,count("select count(*) from audit_records where resource_id=? and action='workspace.pool.reclaimed' and actor_id=?",ws,outsider));
    }
    @Test void allNativeResourceEntrypointsRejectNonmembersAndForeignTenantsWithoutMutation() throws Exception {
        String ws=setupPool();var allowed=call("POST",poolPath(ws)+"/allocate",allocation(member),creator);
        assertEquals(200,allowed.statusCode(),allowed.body());String grant=json.readTree(allowed.body()).get("id").asText();
        var before=state(ws);
        String base="/api/workspaces/"+ws+"/entitlements";
        for(String actor:List.of(outsider,foreign)) {
            String scope=actor.equals(foreign)?jdbc.queryForObject("select tenant_id from \"user\" where id=?",String.class,foreign):tenant;
            for(String suffix:List.of("/pool","/grants","/pool/allocate","/pool/reclaim","/grants/"+grant+"/revoke","/preview")) {
                boolean read=suffix.equals("/pool")||suffix.equals("/grants");
                Object body=suffix.equals("/preview")?Map.of("userId",member,"preset","default_720p"):
                        suffix.endsWith("/revoke")?Map.of("memberId",member,"expectedVersion",0,"sourceRef","ir","idempotencyKey",UUID.randomUUID().toString(),"reason","ir","traceId","ir"):allocation(member);
                var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+base+suffix))
                        .header("Authorization","Bearer "+token(actor,scope)).header("X-User-ID",creator).header("Content-Type","application/json");
                var r=http.send(builder.method(read?"GET":"POST",read?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(403,r.statusCode(),suffix+": "+r.body());assertEquals(before,state(ws));
            }
        }
        assertEquals(401,call("GET",poolPath(ws),null,null).statusCode());assertEquals(before,state(ws));
    }
    @Test void invalidTargetMembershipRejectsAndRemovedLocalTargetsCanBeReclaimedOrRevoked() throws Exception {
        String ws=setupPool();var before=state(ws);
        // Historical foreign member is representable without disabling any constraint.
        jdbc.update("insert into workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values (?,?,?,'VIEWER','ACTIVE',now(),now())","ir-"+UUID.randomUUID(),ws,foreign);
        for(String target:List.of(outsider,foreign,"missing-"+UUID.randomUUID())) {
            for(String suffix:List.of("/allocate","/reclaim")) {
                assertEquals(403,call("POST",poolPath(ws)+suffix,allocation(target),creator).statusCode());assertEquals(before,state(ws));
            }
        }
        var r=call("POST",poolPath(ws)+"/allocate",allocation(member),creator);assertEquals(200,r.statusCode(),r.body());
        String grant=json.readTree(r.body()).get("id").asText();
        assertEquals(204,call("DELETE",path(ws)+"/members/"+member,null,creator).statusCode());
        before=state(ws);assertEquals(403,call("POST",poolPath(ws)+"/allocate",allocation(member),creator).statusCode());assertEquals(before,state(ws));
        assertEquals(200,call("POST",poolPath(ws)+"/reclaim",allocation(member),creator).statusCode());
        assertEquals(0,count("select used_quota from workspace_entitlement_pool where workspace_id=?",ws));
        assertEquals(200,call("POST","/api/workspaces/"+ws+"/entitlements/grants/"+grant+"/revoke",Map.of("memberId",member,"expectedVersion",0,"sourceRef","ir","idempotencyKey",UUID.randomUUID().toString(),"reason","ir","traceId","ir"),creator).statusCode());
        assertEquals(1,count("select count(*) from workspace_member_entitlement_grant where id=? and status='REVOKED'",grant));
    }
    @Test void failedAllocationRollsBackGrantQuotaCommandAuditAndPoolAudit() throws Exception {
        String ws=setupPool();var before=state(ws);
        var controller=context.getBean(com.example.platform.identity.api.WorkspaceController.class);
        asActor(creator,()->assertThrows(IllegalStateException.class,()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(t->{
            controller.createWorkspaceGrant(ws,new com.example.platform.identity.api.WorkspaceController.CreateWorkspaceGrantRequest(member,"review.feature",10,null,null,"ir",UUID.randomUUID().toString(),"ir","ir"),outsider);
            assertEquals(10,count("select used_quota from workspace_entitlement_pool where workspace_id=?",ws));
            assertEquals(1,count("select count(*) from workspace_member_entitlement_grant where workspace_id=?",ws));
            throw new IllegalStateException("failure after all owner writes");
        })));
        assertEquals(before,state(ws));assertEquals(200,call("POST",poolPath(ws)+"/allocate",allocation(member),creator).statusCode());
    }
    @Test void membershipRevocationHoldsWorkspaceLockUntilCommitBeforePoolMutation() throws Exception {
        for(boolean revokeManager:List.of(false,true)) {
            String ws=setupPool();assertEquals(200,add(ws,outsider,"ADMIN",creator).statusCode());var before=state(ws);
            var held=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
            try(var threads=java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var first=threads.submit(()->asActor(creator,()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(t->{
                    owner.removeMember(ws,revokeManager?outsider:member);held.countDown();
                    try {assertTrue(release.await(20,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException ex){throw new RuntimeException(ex);}return null;
                })));
                try {
                    assertTrue(held.await(20,java.util.concurrent.TimeUnit.SECONDS));
                    var second=threads.submit(()->call("POST",poolPath(ws)+"/allocate",allocation(member),outsider));
                    awaitWorkspaceLock(second);assertEquals(before,state(ws));release.countDown();
                    first.get(20,java.util.concurrent.TimeUnit.SECONDS);assertEquals(403,second.get(20,java.util.concurrent.TimeUnit.SECONDS).statusCode());
                } finally {release.countDown();}
            }
            assertEquals(before,state(ws));
        }
    }
    void awaitWorkspaceLock(java.util.concurrent.Future<?> pending) {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);boolean blocked=false;
        while(System.nanoTime()<deadline&&!pending.isDone()) {
            if(count("select count(*) from pg_stat_activity where pid<>pg_backend_pid() and wait_event_type='Lock' and query like '%workspace%' and query like '%for update%'")>0){blocked=true;break;}
            Thread.onSpinWait();
        }
        assertTrue(blocked,"competing production request must wait on the owner row lock");
    }

    @Test void actualHttpMutationFailureRollsBackGrantQuotaAndAuditsThenRecovers() throws Exception {
        String ws=setupPool();var before=state(ws);String fault="ir_pool_fault_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("create function "+fault+"() returns trigger language plpgsql as $$ begin raise exception 'controlled pool audit failure'; return new; end $$");
        try {
            jdbc.execute("create trigger "+fault+" before insert on audit_records for each row when (new.resource_id='"+ws+"' and new.action='workspace.pool.allocated') execute function "+fault+"()");
            var response=call("POST",poolPath(ws)+"/allocate",allocation(member),creator);
            assertEquals(500,response.statusCode(),response.body());assertEquals(before,state(ws));
        } finally {
            jdbc.execute("drop trigger if exists "+fault+" on audit_records");jdbc.execute("drop function "+fault+"()");
        }
        assertEquals(200,call("POST",poolPath(ws)+"/allocate",allocation(member),creator).statusCode());
    }
    @Test void grantResourceScopeAndMemberPreviewRejectCrossWorkspaceTargets() throws Exception {
        String first=setupPool(),second=setupPool();
        var granted=call("POST",poolPath(second)+"/allocate",allocation(member),creator);assertEquals(200,granted.statusCode(),granted.body());
        String grant=json.readTree(granted.body()).get("id").asText();var before=state(first);var otherBefore=state(second);
        var denied=call("POST","/api/workspaces/"+first+"/entitlements/grants/"+grant+"/revoke",Map.of("memberId",member,"expectedVersion",0,"sourceRef","ir","idempotencyKey",UUID.randomUUID().toString(),"reason","ir","traceId","ir"),creator);
        assertEquals(404,denied.statusCode(),denied.body());assertEquals(before,state(first));assertEquals(otherBefore,state(second));
        assertEquals(403,call("POST","/api/workspaces/"+first+"/entitlements/preview",Map.of("userId",creator,"preset","default_720p"),member).statusCode());
        assertEquals(403,call("POST","/api/workspaces/"+first+"/entitlements/preview",Map.of("userId",outsider,"preset","default_720p"),creator).statusCode());
    }

    @Test void inactiveMemberCannotReceiveAllocationOrPreview() throws Exception {
        String ws=setupPool();jdbc.update("update \"user\" set status='INACTIVE' where id=?",member);var before=state(ws);
        assertEquals(403,call("POST",poolPath(ws)+"/allocate",allocation(member),creator).statusCode());
        assertEquals(403,call("POST","/api/workspaces/"+ws+"/entitlements/preview",Map.of("userId",member,"preset","default_720p"),creator).statusCode());
        assertEquals(before,state(ws));
    }
    @Test void allocationLockPersistsThroughTransactionAndSerializesLaterRevocation() throws Exception {
        String ws=setupPool();var held=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var controller=context.getBean(com.example.platform.identity.api.WorkspaceController.class);
        try(var threads=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=threads.submit(()->asActor(creator,()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(t->{
                controller.createWorkspaceGrant(ws,new com.example.platform.identity.api.WorkspaceController.CreateWorkspaceGrantRequest(member,"review.feature",10,null,null,"ir",UUID.randomUUID().toString(),"ir","ir"),outsider);
                held.countDown();try {assertTrue(release.await(20,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException ex){throw new RuntimeException(ex);}return null;
            })));
            try {
                assertTrue(held.await(20,java.util.concurrent.TimeUnit.SECONDS));
                var second=threads.submit(()->call("DELETE",path(ws)+"/members/"+member,null,creator));
                awaitWorkspaceLock(second);assertEquals(0,count("select used_quota from workspace_entitlement_pool where workspace_id=?",ws));
                release.countDown();first.get(20,java.util.concurrent.TimeUnit.SECONDS);assertEquals(204,second.get(20,java.util.concurrent.TimeUnit.SECONDS).statusCode());
            } finally {release.countDown();}
        }
        assertEquals(10,count("select used_quota from workspace_entitlement_pool where workspace_id=?",ws));
        assertEquals(1,count("select count(*) from workspace_member_entitlement_grant where workspace_id=? and member_id=? and granted_by=?",ws,member,creator));
        assertEquals(403,call("POST",poolPath(ws)+"/allocate",allocation(member),creator).statusCode());
    }
}
