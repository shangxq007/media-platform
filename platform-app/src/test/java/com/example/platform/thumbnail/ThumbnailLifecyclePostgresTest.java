package com.example.platform.thumbnail;

import static org.junit.jupiter.api.Assertions.*;
import com.example.platform.contract.media.ThumbnailContracts;
import com.example.platform.shared.test.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

/** Replaces the historical AtomicBoolean race model with actual production SQL and PG row locks. */
class ThumbnailLifecyclePostgresTest {
    static final String TENANT="tenant-thumb",PROJECT="project-thumb";
    static ThumbnailContracts.Request request(String key) {
        return new ThumbnailContracts.Request(TENANT,PROJECT,"source",.25,"jpeg",160,80,key);
    }
    static class World implements AutoCloseable {
        final TestDatabaseConnectionProvider provider=TestDatabaseConnectionProvider.fromControlledLaunch();
        final TestDatabaseSchema schema;final DSLContext db;final ThumbnailTaskStore store;
        World() throws Exception {
            schema=TestDatabaseSchema.open(provider);
            try(var in=getClass().getResourceAsStream("/db/migration/V1__baseline.sql")) {
                schema.executeScript(new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8));
            }
            db=schema.dsl();store=new ThumbnailTaskStore(db);
        }
        Connection connection() throws Exception {return provider.openConnectionForSchema(schema.name());}
        long count(){return db.fetchOne("select count(*) from media_thumbnail_task").get(0,Long.class);}
        @Override public void close() throws Exception {schema.close();}
    }
    @Test void duplicateAdmissionHasOneDurableWinner() throws Exception {
        try(var w=new World()) {
            var a=w.store.admit(request("dup"));var b=w.store.admit(request("dup"));
            assertTrue(a.created());assertFalse(b.created());assertEquals(a.taskId(),b.taskId());assertEquals(1,w.count());
        }
    }
    @Test void concurrentAdmissionUsesTheDatabaseUniquenessBoundary() throws Exception {
        try(var w=new World();var pool=Executors.newFixedThreadPool(2)) {
            var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
            Callable<ThumbnailTaskStore.Admission> action=()->{
                try(var c=w.connection()) {ready.countDown();assertTrue(start.await(5,TimeUnit.SECONDS));
                    return new ThumbnailTaskStore(DSL.using(c,SQLDialect.POSTGRES)).admit(request("race"));}
            };
            var a=pool.submit(action);var b=pool.submit(action);assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            var x=a.get(10,TimeUnit.SECONDS);var y=b.get(10,TimeUnit.SECONDS);
            assertEquals(x.taskId(),y.taskId());assertNotEquals(x.created(),y.created());assertEquals(1,w.count());
        }
    }
    @Test void conflictingIdempotencyNeverMutatesTheOriginalRequest() throws Exception {
        try(var w=new World()) {
            var a=w.store.admit(request("conflict"));
            assertThrows(IllegalArgumentException.class,()->w.store.admit(new ThumbnailContracts.Request(TENANT,PROJECT,"other",.25,"jpeg",160,80,"conflict")));
            assertEquals("source",w.store.request(TENANT,PROJECT,a.taskId()).orElseThrow().sourceAssetId());assertEquals(1,w.count());
        }
    }
    @Test void cancellationFencesPublicationAndRetry() throws Exception {
        try(var w=new World()) {
            var a=w.store.admit(request("cancel"));assertTrue(w.store.cancel(TENANT,PROJECT,a.taskId()));
            assertFalse(w.store.statusIfActive(a.taskId(),ThumbnailContracts.Status.COMMITTING,null,null));
            assertFalse(w.store.lockForCommit(TENANT,PROJECT,a.taskId()));assertFalse(w.store.completeLocked(TENANT,PROJECT,a.taskId(),"bad-artifact"));
            assertFalse(w.store.admit(request("cancel")).created());assertEquals(ThumbnailContracts.Status.CANCELLED,w.store.find(TENANT,PROJECT,a.taskId()).orElseThrow().status());
            assertNull(w.store.find(TENANT,PROJECT,a.taskId()).orElseThrow().artifactId());
        }
    }
    @Test void exactScopePreventsAnotherProjectFromCancellingOrCommitting() throws Exception {
        try(var w=new World()) {
            var a=w.store.admit(request("scope"));w.store.statusIfActive(a.taskId(),ThumbnailContracts.Status.COMMITTING,null,null);
            assertFalse(w.store.cancel("other",PROJECT,a.taskId()));assertFalse(w.store.cancel(TENANT,"other",a.taskId()));
            assertFalse(w.store.lockForCommit(TENANT,"other",a.taskId()));assertFalse(w.store.completeLocked("other",PROJECT,a.taskId(),"bad"));
            assertTrue(w.store.lockForCommit(TENANT,PROJECT,a.taskId()));assertNull(w.store.find(TENANT,PROJECT,a.taskId()).orElseThrow().artifactId());
        }
    }
    @Test void commitLockSerializesCancellationAndKeepsOneTerminalWinner() throws Exception {
        try(var w=new World();var pool=Executors.newSingleThreadExecutor()) {
            var a=w.store.admit(request("commit-race"));w.store.statusIfActive(a.taskId(),ThumbnailContracts.Status.COMMITTING,null,null);
            CompletableFuture<Boolean> cancel=new CompletableFuture<>();var attempting=new CountDownLatch(1);
            w.db.transaction(conf->{
                var owner=new ThumbnailTaskStore(DSL.using(conf));assertTrue(owner.lockForCommit(TENANT,PROJECT,a.taskId()));
                pool.submit(()->{try(var c=w.connection()) {DSL.using(c).execute("set lock_timeout='5s'");attempting.countDown();
                    cancel.complete(new ThumbnailTaskStore(DSL.using(c,SQLDialect.POSTGRES)).cancel(TENANT,PROJECT,a.taskId()));
                }catch(Exception e){cancel.completeExceptionally(e);}});
                try {assertTrue(attempting.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}
                assertTrue(owner.completeLocked(TENANT,PROJECT,a.taskId(),"winner"));
            });
            assertFalse(cancel.get(10,TimeUnit.SECONDS));assertFalse(w.store.completeLocked(TENANT,PROJECT,a.taskId(),"loser"));
            var result=w.store.find(TENANT,PROJECT,a.taskId()).orElseThrow();assertEquals(ThumbnailContracts.Status.COMPLETED,result.status());assertEquals("winner",result.artifactId());
        }
    }
}
