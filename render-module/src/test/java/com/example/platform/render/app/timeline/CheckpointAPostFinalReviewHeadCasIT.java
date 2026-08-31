package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.timeline.app.TimelineRevisionRefHeadUpdateAdapter;
import com.example.platform.timeline.app.TimelineRevisionRefMutation;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import com.example.platform.timeline.version.TimelineConflictException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real PostgreSQL proof for the sole tenant/project/main ref CAS authority. */
class CheckpointAPostFinalReviewHeadCasIT extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-head-cas";
    private javax.sql.DataSource dataSource;
    private DSLContext dsl;
    private TimelineRevisionRefMutation mutation;
    private TimelineRevisionRefHeadUpdateAdapter adapter;

    @BeforeEach
    void setUp() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        com.example.platform.render.testsupport.RenderTestSchemaFixture.createSchema(dsl);
        com.example.platform.render.testsupport.RenderTestSchemaFixture.truncate(dsl);
        mutation = new TimelineRevisionRefMutation(dsl);
        adapter = new TimelineRevisionRefHeadUpdateAdapter(mutation);
    }

    @AfterEach
    void tearDown() {
        closeDataSource(dataSource);
    }

    private RevisionRef seed(String projectId) {
        com.example.platform.render.testsupport.RenderTestSchemaFixture.insertCanonicalProject(
                dsl, TENANT, projectId);
        int number = 1;
        for (String revisionId : new String[] {"R100", "RA", "RB"}) {
            dsl.execute("insert into timeline_snapshot "
                            + "(id, project_id, tenant_id, payload_json) values (?, ?, ?, '{}')",
                    projectId + "-snap-" + revisionId, projectId, TENANT);
            dsl.execute("insert into timeline_revision "
                            + "(id, project_id, tenant_id, revision_number, snapshot_id, "
                            + "internal_revision, content_hash, source, created_at) "
                            + "values (?, ?, ?, ?, ?, ?, ?, 'test', current_timestamp)",
                    projectId + "-" + revisionId, projectId, TENANT, number,
                    projectId + "-snap-" + revisionId, number, "hash-" + revisionId);
            number++;
        }
        RevisionRef ref = RevisionRef.main(TENANT, projectId);
        adapter.updateHeadTx(dsl, ref, null, projectId + "-R100");
        return ref;
    }

    @Test
    void casSingleWriterSucceeds() {
        RevisionRef ref = seed("head-cas-ok-" + java.util.UUID.randomUUID());
        adapter.updateHeadTx(dsl, ref, ref.projectId() + "-R100", ref.projectId() + "-RA");
        assertEquals(ref.projectId() + "-RA", mutation.currentHead(ref));
    }

    @Test
    void casStaleExpectationFailsClosed() {
        RevisionRef ref = seed("head-cas-stale-" + java.util.UUID.randomUUID());
        assertThrows(TimelineConflictException.class,
                () -> adapter.updateHeadTx(
                        dsl, ref, ref.projectId() + "-RB", ref.projectId() + "-RA"));
        assertEquals(ref.projectId() + "-R100", mutation.currentHead(ref));
    }

    @Test
    void concurrentWritersSingleWinner() throws Exception {
        RevisionRef ref = seed("head-cas-race-" + java.util.UUID.randomUUID());
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> race(ref, "RA", go, success, conflict));
            var b = pool.submit(() -> race(ref, "RB", go, success, conflict));
            go.countDown();
            a.get(20, TimeUnit.SECONDS);
            b.get(20, TimeUnit.SECONDS);
        }
        assertEquals(1, success.get());
        assertEquals(1, conflict.get());
        assertTrue(java.util.Set.of(ref.projectId() + "-RA", ref.projectId() + "-RB")
                .contains(mutation.currentHead(ref)));
    }

    private Void race(RevisionRef ref, String next, CountDownLatch go,
                      AtomicInteger success, AtomicInteger conflict) throws Exception {
        go.await();
        try {
            new TimelineRevisionRefHeadUpdateAdapter(new TimelineRevisionRefMutation(dsl))
                    .updateHeadTx(dsl, ref, ref.projectId() + "-R100", ref.projectId() + "-" + next);
            success.incrementAndGet();
        } catch (TimelineConflictException expected) {
            conflict.incrementAndGet();
        }
        return null;
    }
}
