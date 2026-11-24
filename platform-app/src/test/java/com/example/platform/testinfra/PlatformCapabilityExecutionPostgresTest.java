package com.example.platform.testinfra;

import com.example.platform.execution.admission.*;
import com.example.platform.execution.binding.*;
import com.example.platform.execution.compatibility.*;
import com.example.platform.execution.domain.*;
import com.example.platform.execution.domain.provider.*;
import com.example.platform.execution.planning.*;
import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.artifact.infrastructure.JooqArtifactApplicationQuery;
import com.example.platform.workerfabric.admission.*;
import com.example.platform.workerfabric.infrastructure.JooqCompletedTaskOutputQuery;
import com.example.platform.shared.capability.*;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.outbox.app.*;
import com.example.platform.entitlement.app.*;
import com.example.platform.entitlement.infrastructure.QuotaUsageJdbcRepository;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real owner SQL, row locks, canonical #21/#22 facts and durable outbox in independent PG schemas. */
class PlatformCapabilityExecutionPostgresTest {
    @AfterEach void clear(){TenantContext.clear();}
    static class World implements AutoCloseable {
        final GraphRenderLifecycleIT.World fixture=new GraphRenderLifecycleIT.World();
        final DSLContext db=fixture.db;
        World() throws Exception {
            db.execute("insert into identity.workspace(id,tenant_id,name,created_at,updated_at) values('workspace-a','tenant-a','capability-test',now(),now())");
            db.execute("update identity.project set workspace_id='workspace-a' where id='project-a' and tenant_id='tenant-a'");
        }
        PlatformCapabilityExecutionService owner(DSLContext connection) { return owner(connection, fixture.schema.connection()); }
        PlatformCapabilityExecutionService owner(DSLContext connection, java.sql.Connection sql) {
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(sql,true));
            var quota=new QuotaConsumptionApplicationService(new QuotaPolicyService(),new QuotaUsageAuthority(new QuotaUsageJdbcRepository(jdbc)));
            return new PlatformCapabilityExecutionService(connection,new OutboxEventService(connection,3,mock(PostgresNotificationService.class),
                    new OutboxEventRouter(List.of(new ExecutionOutboxEvents()))),quota,new JooqCompletedTaskOutputQuery(connection),new JooqArtifactApplicationQuery(connection));
        }
        <T> T tx(java.util.function.Supplier<T> operation) throws Exception {return fixture.tx(operation);}
        long count(String table){return db.fetchOne("select count(*) from "+table).get(0,Long.class);}
        @Override public void close() throws Exception {fixture.close();}
    }
    ProviderCandidate candidate(String provider, String version) {
        var id=ProviderId.of(provider);var implementation=ProviderImplementationId.of(provider+".impl");
        var v=ProviderVersion.of(version);var contract=ProviderExecutionContractVersion.of(1,0);
        var profile=ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(1,0));
        var pin=new ProviderBindingPin(id,implementation,v,contract,profile,List.of());
        var range=ContractVersionRange.exactly(ContractVersion.of(1,0));
        var cover=CapabilityId.of("media.cover-image");var thumb=CapabilityId.of("media.thumbnail");
        return new ProviderCandidate(pin,new ProviderDescriptor(id,implementation,v,contract,profile),
                new ProviderExecutionContract(ProviderExecutionContractSchemaVersion.of(1),contract,List.of(
                        new ProviderCapabilityContractReference(cover,range),new ProviderCapabilityContractReference(thumb,range))),
                new ProviderCapabilityProfile(profile,List.of(ProviderCapabilitySupport.unpinned(cover,range),ProviderCapabilitySupport.unpinned(thumb,range))),
                new ProviderStaticCompatibility(ProviderStaticCompatibility.Knowledge.DECLARED,
                        List.of(ProviderStaticCompatibility.ArtifactRequirementKind.PINNED_SOURCE_INPUT,ProviderStaticCompatibility.ArtifactRequirementKind.FINAL_OUTPUT),
                        List.of(),List.of(StaticCompatibilityConstraint.ProviderDeviceKind.CPU),
                        List.of(StaticCompatibilityConstraint.ProviderRuntimeClass.NATIVE_PROCESS,StaticCompatibilityConstraint.ProviderRuntimeClass.ISOLATED_PROCESS),
                        List.of(ProviderStaticCompatibility.SandboxMode.SANDBOXED),List.of(ProviderStaticCompatibility.DeterminismClass.DETERMINISTIC),
                        List.of(),ProviderStaticCompatibility.LoweringSupport.SUPPORTED));
    }
    ArtifactCapabilityExecutionPlan plan(String key,String source,String seconds,String capability) {
        var identity=ArtifactCapabilityExecutionPlan.requestIdentity("tenant-a","project-a",capability,key);
        var request=new ArtifactCapabilityRequest(identity,capability,new ArtifactPin(new ArtifactId(source),ContentDigest.sha256("a".repeat(64))),
                MediaFrameExtractParametersV1.ofExactSeconds(seconds,RasterImageEncoding.Png.PNG,new FrameWidth.ExplicitPixels(160)));
        var physical=new PhysicalPlanningEntryService().planArtifactCapability(request,new ExecutionPlanId(UUID.randomUUID().toString()));
        var candidates=List.of(candidate("ffmpeg","1.1.0"));
        var graph=new ProviderBindingEntryService().bind(physical,candidates,List.of()).executableTaskGraph();
        return new ArtifactCapabilityExecutionPlan("tenant-a","project-a","workspace-a","user-a",key,new BoundGraphInputs(physical,candidates,List.of(),graph.digest()));
    }
    @Test void restartRestoresImmutableInputsAndScopedIdempotencyRejectsDifferentParameters() throws Exception {
        try(var w=new World()) {
            var plan=plan("stable","source-a","0.25","media.cover-image");var owner=w.owner(w.db);
            var accepted=w.tx(()->owner.admit(plan));assertTrue(accepted.newlyAdmitted());
            var restarted=w.owner(w.db);var replay=w.tx(()->restarted.admit(plan("stable","source-a","0.2500","media.cover-image")));
            assertFalse(replay.newlyAdmitted());assertEquals(accepted.handle(),replay.handle());assertEquals(plan.inputs(),replay.plan().inputs());
            assertThrows(com.example.platform.shared.web.PlatformException.class,()->w.tx(()->restarted.admit(plan("stable","source-b","0.25","media.cover-image"))));
            assertThrows(com.example.platform.shared.web.PlatformException.class,()->w.tx(()->restarted.admit(plan("stable","source-a","0.5","media.cover-image"))));
            var distinct=w.tx(()->restarted.admit(plan("stable","source-a","0.25","media.thumbnail")));
            assertNotEquals(accepted.executionId(),distinct.executionId());assertEquals(2,w.count("platform_execution_admission"));
            assertEquals(2,w.count("outbox_events"));assertEquals(0,w.count("platform_execution_result"));assertEquals(0,w.count("quota_usage_operation"));
            assertEquals(2,w.count("render_job")); // Fixture's two Render jobs; capability admission creates none.
        }
    }
    @Test void concurrentAdmissionHasExactlyOneDurableWinnerAndDispatchEvent() throws Exception {
        try(var w=new World();var pool=Executors.newFixedThreadPool(4)) {
            var plan=plan("race","source-a","0.25","media.thumbnail");var ready=new CountDownLatch(4);var start=new CountDownLatch(1);
            Callable<PlatformExecutionAdmissionPort.CapabilityExecution> call=()-> {
                TenantContext.set("tenant-a");
                try(var c=com.example.platform.shared.test.TestDatabaseConnectionProvider.fromControlledLaunch().openConnectionForSchema(w.fixture.schema.name())) {
                    var db=DSL.using(c,SQLDialect.POSTGRES,w.db.settings());db.execute("set lock_timeout='5s'");c.setAutoCommit(false);ready.countDown();assertTrue(start.await(5,TimeUnit.SECONDS));
                    try {var result=w.owner(db,c).admit(plan);c.commit();return result;}catch(RuntimeException failure){c.rollback();throw failure;}
                } finally {TenantContext.clear();}
            };
            var futures=new ArrayList<Future<PlatformExecutionAdmissionPort.CapabilityExecution>>();for(int i=0;i<4;i++)futures.add(pool.submit(call));
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();int winners=0;
            for(var f:futures){var result=f.get(10,TimeUnit.SECONDS);assertEquals(plan.executionId(),result.executionId());if(result.newlyAdmitted())winners++;}
            assertEquals(1,winners,"new durable admission winners");assertEquals(1,w.count("platform_execution_admission"),"schema-owned execution count");assertEquals(1,w.count("outbox_events"),"same-schema outbox count");
        }
    }
    @Test void outboxFailureRollsBackAcceptanceAndRetryCreatesOneRecord() throws Exception {
        try(var w=new World()) {
            var plan=plan("dispatch","source-a","0.25","media.cover-image");var owner=w.owner(w.db);
            w.db.execute("create function reject_capability_dispatch() returns trigger language plpgsql as $$ begin raise exception 'synthetic owned dispatch failure'; end $$");
            w.db.execute("create trigger reject_capability_dispatch before insert on outbox_events for each row execute function reject_capability_dispatch()");
            var rejected=assertThrows(org.jooq.exception.DataAccessException.class,()->w.tx(()->owner.admit(plan)));assertTrue(rejected.getMessage().contains("synthetic owned dispatch failure"));assertEquals(0,w.count("platform_execution_admission"));assertEquals(0,w.count("outbox_events"));
            w.db.execute("drop trigger reject_capability_dispatch on outbox_events");w.tx(()->owner.admit(plan));assertEquals(1,w.count("platform_execution_admission"));assertEquals(1,w.count("outbox_events"));
        }
    }
    @Test void cancelledStaleAndCrossProjectPublicationNeverReachArtifactCommit() throws Exception {
        try(var w=new World()) {
            var plan=plan("cancel","source-a","0.25","media.thumbnail");var owner=w.owner(w.db);
            var admitted=w.tx(()->owner.admit(plan));w.tx(()->owner.begin("tenant-a","project-a",admitted.executionId(),1));
            w.tx(()->owner.cancel("tenant-a","project-a",admitted.executionId(),1));
            var task=new ProviderBindingEntryService().bind(plan.inputs().physicalPlan(),plan.inputs().candidates(),List.of()).executableTaskGraph().tasks().getFirst().id();
            var calls=new AtomicInteger();
            assertThrows(java.util.concurrent.CancellationException.class,()->w.tx(()-> {try{return owner.publish("tenant-a","project-a",plan.executionId(),1,task,null,()->{calls.incrementAndGet();throw new java.io.IOException();});}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}));
            assertThrows(com.example.platform.shared.web.PlatformException.class,()->w.tx(()->owner.retry("tenant-a","project-a",plan.executionId(),1)));
            assertThrows(IllegalArgumentException.class,()->w.tx(()->owner.cancel("tenant-a","project-b",plan.executionId(),1)));
            assertTrue(owner.find("tenant-a","project-b",plan.executionId()).isEmpty());
            assertEquals(0,calls.get());assertEquals(0,w.count("artifact"));assertEquals(0,w.count("platform_execution_result"));assertEquals(0,w.count("quota_usage_operation"));
            assertEquals(PlatformExecutionState.CANCELLED,owner.find("tenant-a","project-a",plan.executionId()).orElseThrow().state());
        }
    }
    @Test void explicitRetryAdvancesGenerationAndRejectsOldCancellationFailureAndPublication() throws Exception {
        try(var w=new World()) {
            var plan=plan("retry","source-a","0.25","media.cover-image");var owner=w.owner(w.db);w.tx(()->owner.admit(plan));
            w.tx(()->owner.begin("tenant-a","project-a",plan.executionId(),1));w.tx(()->{owner.fail("tenant-a","project-a",plan.executionId(),1,"SYNTHETIC_FAILURE",false);return null;});
            var retry=w.tx(()->owner.retry("tenant-a","project-a",plan.executionId(),1));assertEquals(2,retry.generation());assertEquals(PlatformExecutionState.RETRYING,retry.state());
            w.tx(()->{owner.fail("tenant-a","project-a",plan.executionId(),1,"OLD_DELIVERY",true);return null;});
            assertThrows(com.example.platform.shared.web.PlatformException.class,()->w.tx(()->owner.cancel("tenant-a","project-a",plan.executionId(),1)));
            var task=new ProviderBindingEntryService().bind(plan.inputs().physicalPlan(),plan.inputs().candidates(),List.of()).executableTaskGraph().tasks().getFirst().id();var calls=new AtomicInteger();
            assertThrows(com.example.platform.shared.web.PlatformException.class,()->w.tx(()-> {try{return owner.publish("tenant-a","project-a",plan.executionId(),1,task,null,()->{calls.incrementAndGet();throw new java.io.IOException();});}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}));
            assertEquals(0,calls.get());assertFalse(owner.find("tenant-a","project-a",plan.executionId()).orElseThrow().retryBlocked());
            var begun=w.tx(()->owner.begin("tenant-a","project-a",plan.executionId(),2));assertEquals(begun.handle(),w.tx(()->owner.begin("tenant-a","project-a",plan.executionId(),2)).handle());
            assertEquals(2,w.count("outbox_events"));assertEquals(0,w.count("platform_execution_result"));assertEquals(0,w.count("quota_usage_operation"));
        }
    }
    @Test void uncertainPhysicalOutcomePersistsBlockedRetryAcrossServiceRestart() throws Exception {
        try(var w=new World()) {
            var plan=plan("uncertain","source-a","0.25","media.thumbnail");var owner=w.owner(w.db);w.tx(()->owner.admit(plan));w.tx(()->owner.begin("tenant-a","project-a",plan.executionId(),1));
            w.tx(()->{owner.fail("tenant-a","project-a",plan.executionId(),1,"EXECUTION_RECONCILIATION_REQUIRED",true);return null;});
            var restarted=w.owner(w.db);var failed=restarted.find("tenant-a","project-a",plan.executionId()).orElseThrow();
            assertTrue(failed.retryBlocked());assertEquals(PlatformExecutionState.FAILED,failed.state());
            assertThrows(com.example.platform.shared.web.PlatformException.class,()->w.tx(()->restarted.retry("tenant-a","project-a",plan.executionId(),1)));
            assertEquals(1,w.count("outbox_events"));assertEquals(0,w.count("platform_execution_result"));assertEquals(0,w.count("artifact"));
        }
    }
    @Test void retiredPurposeTablesAndSnapshotStoreAreAbsentFromTheActualSchema() throws Exception {
        try(var w=new World()) {
            for(String table:List.of("cover_image_task","media_thumbnail_task","wf_provider_candidate_snapshot"))
                assertNull(w.db.fetchOne("select to_regclass(?)",table).get(0),table+" must not retain a task or candidate authority");
            var owner=w.owner(w.db);var accepted=w.tx(()->owner.admit(plan("single-owner","source-a","0.25","media.cover-image")));
            assertEquals(1,w.count("platform_execution_admission"));assertEquals(0,w.count("platform_execution_result"));
            assertEquals(2,w.count("render_job"));assertEquals("source-a",accepted.plan().request().source().artifactId().value());
        }
    }
    @Test void persistedResultRejectsMismatchedNativeTaskAndGeneration() throws Exception {
        try(var w=new World()) {
            var plan=plan("native-fk","source-a","0.25","media.thumbnail");var owner=w.owner(w.db);w.tx(()->owner.admit(plan));
            for(boolean wrongTask:List.of(false,true)) {
                var failure=assertThrows(org.jooq.exception.IntegrityConstraintViolationException.class,()->w.tx(()-> {
                    w.db.execute("""
                        insert into execution.platform_execution_result(execution_id,attempt_id,ownership_generation,native_task_id,native_generation,
                            result_id,storage_receipt,artifact_id,materialization_state,reconciliation_state,created_at,updated_at)
                        values(?,'task-attempt-1',1,?,?,?,'receipt','not-an-output','ARTIFACT_COMMITTED','NONE',now(),now())
                        """,plan.executionId(),wrongTask?"d".repeat(64):GraphRenderLifecycleIT.TASK,wrongTask?1:2,"invalid-pair-"+wrongTask);
                    return null;
                }));
                assertEquals("23503",failure.sqlState());assertEquals(0,w.count("platform_execution_result"));
            }
            assertEquals(0,w.count("quota_usage_operation"));assertEquals(PlatformExecutionState.ADMITTED,owner.find("tenant-a","project-a",plan.executionId()).orElseThrow().state());
        }
    }

}
