package com.example.platform.testinfra;

import com.example.platform.artifact.domain.*;
import com.example.platform.execution.admission.*;
import com.example.platform.execution.binding.ProviderBindingEntryService;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.shared.test.TestDatabaseConnectionProvider;
import com.example.platform.workerfabric.admission.PlatformCapabilityExecutionService;
import com.example.platform.workerfabric.reuse.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real logical Execution SQL/transactions/row contention. Native-output fixture facts model
 * the separately-tested native commit boundary; this test is not an HTTP/media E2E. */
class PlatformCapabilityTerminalRacePostgresTest {
    @AfterEach void clear() { TenantContext.clear(); }
    record Fixture(ArtifactCapabilityExecutionPlan plan, ExecutableTaskId task,
            TaskRuntimeExecution execution, RuntimeClosedLoopTaskResult result) {}
    Fixture ready(PlatformCapabilityExecutionPostgresTest.World w) throws Exception {
        var plan = new PlatformCapabilityExecutionPostgresTest().plan("terminal-race", "source-a", "0.25", "media.thumbnail");
        var owner = w.owner(w.db); w.tx(() -> owner.admit(plan));
        w.tx(() -> owner.begin("tenant-a", "project-a", plan.executionId(), 1));
        var task = new ProviderBindingEntryService().bind(plan.inputs().physicalPlan(), plan.inputs().candidates(), List.of()).executableTaskGraph().tasks().getFirst().id();
        w.db.execute("insert into worker_fabric.wf_execution_backend_selection(selection_id,task_id,backend,placement_authority_scope,active,selected_at) values('race-selection',?,'NATIVE_PULL_WORKER','PLATFORM_MANAGED',true,now())", task.sha256Hex());
        w.db.execute("insert into worker_fabric.wf_execution_ownership_generation(task_id,generation,created_at) values(?,1,now())", task.sha256Hex());
        w.db.execute("insert into worker_fabric.wf_execution_attempt(attempt_id,task_id,generation,backend,state,backend_selection_id,created_at,updated_at) values('race-attempt',?,1,'NATIVE_PULL_WORKER','CREATED','race-selection',now(),now())", task.sha256Hex());
        w.db.execute("insert into worker_fabric.wf_task_ownership(task_id,current_generation,claimable,updated_at) values(?,1,true,now())", task.sha256Hex());
        var execution = mock(TaskRuntimeExecution.class, RETURNS_DEEP_STUBS);
        when(execution.artifactCommitMetadata().tenantId()).thenReturn("tenant-a");
        when(execution.artifactCommitMetadata().projectId()).thenReturn("project-a");
        when(execution.runtimeContext().platformExecutionAttemptId().value()).thenReturn("race-attempt");
        when(execution.runtimeContext().platformOwnershipGeneration().value()).thenReturn(1L);
        var artifact = new Artifact(new ArtifactId("race-image"), "tenant-a", ContentDigest.sha256("a".repeat(64)), 1,
                ArtifactMediaType.IMAGE, ArtifactKind.DERIVED_MEDIA, ArtifactState.AVAILABLE, 1, Instant.EPOCH);
        var result = mock(RuntimeClosedLoopTaskResult.class, RETURNS_DEEP_STUBS);
        when(result.durableArtifactCommit().artifactCommitResult().artifact()).thenReturn(artifact);
        when(result.durableArtifactCommit().storagePublication().objectId().value()).thenReturn("race-object");
        return new Fixture(plan, task, execution, result);
    }
    RuntimeClosedLoopTaskResult nativeCallbackFixture(DSLContext db, Fixture f, AtomicInteger callbacks) {
        callbacks.incrementAndGet();
        db.execute("insert into artifacts.artifact(id,tenant_id,project_id,content_digest,byte_length,media_type,artifact_kind,state,schema_version,created_at) values('race-image','tenant-a','project-a',?,1,'IMAGE','DERIVED_MEDIA','AVAILABLE',1,now())", "a".repeat(64));
        db.execute("update worker_fabric.wf_execution_attempt set state='SUCCEEDED',updated_at=now() where attempt_id='race-attempt'");
        db.execute("insert into worker_fabric.wf_completion_event(completion_event_id,task_id,attempt_id,generation,artifact_id,completed_at) values('race-native-completion',?,'race-attempt',1,'race-image',now())", f.task().sha256Hex());
        return f.result();
    }
    interface Action<T> { T apply(PlatformCapabilityExecutionService owner, DSLContext db) throws Exception; }
    <T> T connection(PlatformCapabilityExecutionPostgresTest.World w, AtomicInteger backend, Action<T> action) throws Exception {
        TenantContext.set("tenant-a");
        try (var c = TestDatabaseConnectionProvider.fromControlledLaunch().openConnectionForSchema(w.fixture.schema.name())) {
            var db = DSL.using(c, SQLDialect.POSTGRES, w.db.settings());
            db.execute("set lock_timeout='5s'"); db.execute("set statement_timeout='10s'"); c.setAutoCommit(false);
            backend.set(db.fetchOne("select pg_backend_pid()").get(0, Integer.class));
            try { var result = action.apply(w.owner(db,c), db); c.commit(); return result; }
            catch (Exception failure) { c.rollback(); throw failure; }
        } finally { TenantContext.clear(); }
    }
    void waitForActualDatabaseLock(DSLContext observer, AtomicInteger blocked, AtomicInteger blocker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (blocked.get() > 0 && Boolean.TRUE.equals(observer.fetchOne("select ? = any(pg_blocking_pids(?))", blocker.get(), blocked.get()).get(0, Boolean.class))) return;
            Thread.sleep(10);
        }
        fail("second live PostgreSQL backend never contended on the first transaction's lock");
    }
    @Test void committedPublicationWinsTheRealRowLockAndCancelCannotReplaceItsTerminalOrSettleTwice() throws Exception {
        try (var w = new PlatformCapabilityExecutionPostgresTest.World(); var pool = Executors.newFixedThreadPool(2)) {
            var f = ready(w); var callbackEntered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var publisherPid = new AtomicInteger(); var cancelPid = new AtomicInteger(); var callbacks = new AtomicInteger();
            var publication = pool.submit(() -> connection(w,publisherPid,(owner,db) -> owner.publish("tenant-a","project-a",f.plan().executionId(),1,f.task(),f.execution(),() -> {
                callbackEntered.countDown();
                try { assertTrue(release.await(4,TimeUnit.SECONDS)); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted); }
                return nativeCallbackFixture(db,f,callbacks);
            })));
            Future<PlatformExecutionAdmissionPort.CapabilityExecution> cancellation = null;
            try {
                assertTrue(callbackEntered.await(4,TimeUnit.SECONDS));
                cancellation = pool.submit(() -> connection(w,cancelPid,(owner,db) -> owner.cancel("tenant-a","project-a",f.plan().executionId(),1)));
                waitForActualDatabaseLock(w.db,cancelPid,publisherPid);
                assertFalse(cancellation.isDone()); assertEquals(0, w.count("artifact")); assertEquals(0,w.count("platform_execution_result"));
            } finally { release.countDown(); }
            assertSame(f.result(),publication.get(8,TimeUnit.SECONDS));
            assertEquals(PlatformExecutionState.COMPLETED,cancellation.get(8,TimeUnit.SECONDS).state());
            assertEquals(1,callbacks.get()); assertEquals(1,w.count("artifact")); assertEquals(1,w.count("platform_execution_result"));
            assertEquals(1,w.db.fetchOne("select count(*) from worker_fabric.wf_completion_event where task_id=?",f.task().sha256Hex()).get(0,Integer.class));
            assertEquals(1,w.count("quota_usage_operation"));
            assertEquals(0, w.db.fetchOne("select signed_delta from entitlement.quota_usage_operation").get(0, java.math.BigDecimal.class).compareTo(java.math.BigDecimal.ONE));
            var saved = w.db.fetchOne("select attempt_id,native_task_id,native_generation,ownership_generation,artifact_id from execution.platform_execution_result where execution_id=?",f.plan().executionId());
            assertEquals("race-attempt",saved.get(0)); assertEquals(f.task().sha256Hex(),saved.get(1));
            assertEquals(1L,saved.get(2,Long.class)); assertEquals(1L,saved.get(3,Long.class)); assertEquals("race-image",saved.get(4));
            assertEquals("race-image",w.owner(w.db).find("tenant-a","project-a",f.plan().executionId()).orElseThrow().artifactId());
            assertThrows(com.example.platform.workerfabric.domain.CapabilityExecutionCompletionPort.AlreadyCompleted.class,
                    () -> w.tx(() -> { try { return w.owner(w.db).publish("tenant-a","project-a",f.plan().executionId(),1,f.task(),f.execution(),() -> nativeCallbackFixture(w.db,f,callbacks)); }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); } }));
            assertEquals(1,callbacks.get()); assertEquals(1,w.count("quota_usage_operation"));
        }
    }
    @Test void cancellationWinsTheRealRowLockAndWaitingPublicationCannotReachItsCallback() throws Exception {
        try (var w = new PlatformCapabilityExecutionPostgresTest.World(); var pool = Executors.newFixedThreadPool(2)) {
            var f = ready(w); var cancelled = new CountDownLatch(1); var release = new CountDownLatch(1);
            var cancelPid = new AtomicInteger(); var publisherPid = new AtomicInteger(); var callbacks = new AtomicInteger();
            var cancellation = pool.submit(() -> connection(w,cancelPid,(owner,db) -> {
                var result = owner.cancel("tenant-a","project-a",f.plan().executionId(),1); cancelled.countDown();
                assertTrue(release.await(4,TimeUnit.SECONDS)); return result;
            }));
            Future<?> publication = null;
            try {
                assertTrue(cancelled.await(4,TimeUnit.SECONDS));
                publication = pool.submit(() -> connection(w,publisherPid,(owner,db) -> owner.publish("tenant-a","project-a",f.plan().executionId(),1,f.task(),f.execution(),() -> nativeCallbackFixture(db,f,callbacks))));
                waitForActualDatabaseLock(w.db,publisherPid,cancelPid); assertFalse(publication.isDone());
            } finally { release.countDown(); }
            assertEquals(PlatformExecutionState.CANCELLED,cancellation.get(8,TimeUnit.SECONDS).state());
            var waitingPublication = publication;
            var rejected = assertThrows(ExecutionException.class,() -> waitingPublication.get(8,TimeUnit.SECONDS));
            assertInstanceOf(CancellationException.class,rejected.getCause());
            assertEquals(0,callbacks.get()); assertEquals(0,w.count("artifact")); assertEquals(0,w.count("platform_execution_result"));
            assertEquals(0,w.count("quota_usage_operation")); assertEquals(0,w.count("wf_completion_event"));
            assertEquals(PlatformExecutionState.CANCELLED,w.owner(w.db).find("tenant-a","project-a",f.plan().executionId()).orElseThrow().state());
        }
    }
}
