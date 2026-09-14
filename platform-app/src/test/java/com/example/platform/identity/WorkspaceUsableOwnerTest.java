package com.example.platform.identity;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Durable regressions from independent Workspace authority counterexamples. */
class WorkspaceUsableOwnerTest extends WorkspaceAuthorityHttpTest {
    @Test void independentPoolRoutesMustDenyNonmembers() throws Exception {
        String ws=create();
        var pool=context.getBean(com.example.platform.entitlement.app.WorkspaceEntitlementPoolService.class)
                .createPool(ws,"review.feature",100,"MONTHLY",creator);
        jdbc.update("update workspace_entitlement_pool set used_quota=40 where id=?",pool.id());
        String endpoint="/api/workspaces/"+ws+"/entitlements/pool";
        assertEquals(200,call("GET",endpoint,null,creator).statusCode());
        var read=call("GET",endpoint,null,outsider);
        var reclaim=call("POST",endpoint+"/reclaim",Map.of("memberId",creator,"featureKey","review.feature","quotaAmount",10),outsider);
        long usage=count("select used_quota from workspace_entitlement_pool where id=?",pool.id());
        System.out.println("POOL_PROBE outsider read="+read.statusCode()+" reclaim="+reclaim.statusCode()+" persistedUsage="+usage);
        assertAll(()->assertEquals(403,read.statusCode()),()->assertEquals(403,reclaim.statusCode()),()->assertEquals(40,usage));
    }
    @Test void independentLegacyForeignOwnerMustNotPermitLastUsableOwnerRemoval() throws Exception {
        String ws=create();
        // Baseline addMember accepted arbitrary user IDs; schema has no tenant FK constraint.
        jdbc.update("insert into workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values (?,?,?,'OWNER','ACTIVE',now(),now())","legacy-"+UUID.randomUUID(),ws,foreign);
        var result=call("DELETE",path(ws)+"/members/"+creator,null,creator);
        long usable=count("select count(*) from workspace_member m join workspace w on w.id=m.workspace_id join \"user\" u on u.id=m.user_id where w.id=? and m.role='OWNER' and m.status='ACTIVE' and u.status='ACTIVE' and u.tenant_id=w.tenant_id",ws);
        System.out.println("OWNER_PROBE removal="+result.statusCode()+" usableOwners="+usable);
        assertAll(()->assertEquals(409,result.statusCode()),()->assertEquals(1,usable));
    }
    @Test void independentLockHeldUntilCommitSerializesCompetingOwnerRemoval() throws Exception {
        String ws=create();assertEquals(200,add(ws,member,"OWNER",creator).statusCode());
        var firstWrote=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var threads=Executors.newFixedThreadPool(2)) {
            var first=threads.submit(()->asActor(creator,()->new TransactionTemplate(transactions).execute(s->{
                owner.removeMember(ws,creator);firstWrote.countDown();
                try { if(!release.await(20,TimeUnit.SECONDS))throw new AssertionError("release timeout"); }
                catch(InterruptedException e){throw new RuntimeException(e);}return null;
            })));
            assertTrue(firstWrote.await(20,TimeUnit.SECONDS));
            var second=threads.submit(()->call("DELETE",path(ws)+"/members/"+member,null,member));
            boolean blocked=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            try {
                while(System.nanoTime()<deadline && !second.isDone()) {
                    if(count("select count(*) from pg_stat_activity where pid<>pg_backend_pid() and wait_event_type='Lock' and query like '%workspace%' and query like '%for update%'")>0){blocked=true;break;}
                    Thread.onSpinWait();
                }
                assertTrue(blocked,"Observe PostgreSQL lock wait, not just simultaneous thread release");
                assertEquals(2,members(ws),"uncommitted removal is invisible to other connections");
            } finally {release.countDown();}
            first.get(20,TimeUnit.SECONDS);assertEquals(409,second.get(20,TimeUnit.SECONDS).statusCode());
        } finally {release.countDown();}
        assertEquals(1,members(ws));assertEquals(200,call("GET",path(ws),null,member).statusCode());
        assertEquals(403,call("GET",path(ws),null,creator).statusCode());
    }

    @Test void missingAndInactiveHistoricalOwnersCannotJustifyRemovingTheUsableOwner() throws Exception {
        for(String kind:List.of("missing","inactive")) {
            String ws=create(),bad=kind.equals("missing")?"missing-"+UUID.randomUUID():user(tenant);
            if(kind.equals("inactive")) jdbc.update("update \"user\" set status='INACTIVE' where id=?",bad);
            jdbc.update("insert into workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values (?,?,?,'OWNER','ACTIVE',now(),now())","legacy-"+UUID.randomUUID(),ws,bad);
            long before=ownerAudits();
            assertEquals(409,call("DELETE",path(ws)+"/members/"+creator,null,creator).statusCode());
            assertEquals(1,count("select count(*) from workspace_member where workspace_id=? and user_id=? and status='ACTIVE'",ws,creator));
            assertEquals(1,count("select count(*) from workspace_member where workspace_id=? and user_id=? and status='ACTIVE'",ws,bad));
            assertEquals(before,ownerAudits());assertEquals(200,call("GET",path(ws),null,creator).statusCode());
        }
    }
    @Test void healthyOwnerRemovalRollbackAndRetryPreserveExactlyOneUsableOwner() throws Exception {
        String ws=create();assertEquals(200,add(ws,member,"OWNER",creator).statusCode());long before=ownerAudits();
        asActor(creator,()->assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(t->{
            owner.removeMember(ws,creator);assertEquals(1,members(ws));throw new IllegalStateException("owner removal failure");
        })));
        assertEquals(2,members(ws));assertEquals(before,ownerAudits());
        assertEquals(204,call("DELETE",path(ws)+"/members/"+creator,null,creator).statusCode());
        assertEquals(1,members(ws));assertEquals(409,call("DELETE",path(ws)+"/members/"+member,null,member).statusCode());
        assertEquals(1,members(ws));assertEquals(200,call("GET",path(ws),null,member).statusCode());
    }
}
