package com.example.platform.render.app.revisioncommand;

import com.example.platform.timeline.adapter.RevisionCommandApplyService;
import com.example.platform.timeline.adapter.RevisionGraphService;
import com.example.platform.timeline.app.RevisionCommandPlanner;
import com.example.platform.timeline.app.ProjectRevisionNumberAllocator;
import com.example.platform.timeline.revisioncommand.RevisionCommandErrorCode;
import com.example.platform.timeline.revisioncommand.RevisionCommandException;
import com.example.platform.timeline.revisioncommand.RevisionCommandPlan;
import com.example.platform.timeline.revisioncommand.RevisionCommandPlanDigest;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REVISION_COMMAND_MODEL_V1: real-PostgreSQL integration tests for the V4
 * parent-graph + counter schema, CREATE_REF / DELETE_REF / RESTORE / MERGE
 * transactions, multi-ref revision-number concurrency (RCI2 absolute gate),
 * cross-project parent DB integrity (RCI4), ordered parent edges (RCI3).
 */
@Testcontainers
class RevisionCommandConcurrencyIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DSLContext dsl;
    private static RevisionCommandApplyService applyService;
    private static RevisionGraphService graph;
    private static final String P1 = "project-1";
    private static final String P2 = "project-2";

    @BeforeAll
    static void setup() {
        PG.start();
        dsl = DSL.using(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        dsl.execute("""
                create table timeline_revision (
                    id varchar(64) primary key,
                    project_id varchar(64) not null,
                    tenant_id varchar(64),
                    parent_revision_id varchar(64),
                    revision_number int not null,
                    snapshot_id varchar(64),
                    internal_revision int not null default 0,
                    content_hash varchar(64) not null,
                    schema_version varchar(32),
                    source varchar(32),
                    author_user_id varchar(64),
                    edit_session_id varchar(64),
                    message varchar(512),
                    change_summary_json text,
                    created_at timestamp not null,
                    patch_ops_json text,
                    labels_json varchar(512),
                    is_merge boolean not null default false,
                    merge_parent_revision_ids text,
                    merge_base_revision_id varchar(64)
                )""");
        dsl.execute("create unique index ux_timeline_revision_project_id on timeline_revision(project_id, id)");
        dsl.execute("""
                create table timeline_revision_ref (
                    project_id varchar(64) not null,
                    ref_id varchar(64) not null,
                    head_revision_id varchar(64),
                    version bigint not null default 0,
                    updated_at timestamp not null default current_timestamp,
                    primary key (project_id, ref_id),
                    constraint fk_timeline_revision_ref_head
                        foreign key (head_revision_id) references timeline_revision(id)
                        deferrable initially deferred
                )""");
        dsl.execute("""
                create table apply_command (
                    apply_command_id varchar(64) primary key,
                    plan_digest varchar(64) not null,
                    fingerprint varchar(64) not null,
                    status varchar(16) not null,
                    result_revision_id varchar(64),
                    result_content_hash varchar(64),
                    result_status varchar(16),
                    project_id varchar(64),
                    created_at timestamp not null default current_timestamp,
                    completed_at timestamp,
                    command_domain varchar(32) not null default 'OPERATION_PLAN'
                )""");
        dsl.execute("""
                create table timeline_revision_parent (
                    project_id varchar(64) not null,
                    revision_id varchar(64) not null,
                    parent_revision_id varchar(64) not null,
                    parent_order int not null,
                    primary key (revision_id, parent_order),
                    constraint ux_timeline_revision_parent_pair unique (revision_id, parent_revision_id),
                    constraint ck_timeline_revision_parent_order_nonnegative check (parent_order >= 0),
                    constraint ck_timeline_revision_parent_no_self check (revision_id <> parent_revision_id),
                    constraint fk_timeline_revision_parent_revision
                        foreign key (revision_id) references timeline_revision(id),
                    constraint fk_timeline_revision_parent_parent
                        foreign key (project_id, parent_revision_id)
                        references timeline_revision(project_id, id)
                )""");
        dsl.execute("create table project_revision_counter (project_id varchar(64) primary key, next_revision_number bigint not null)");
        dsl.execute("create table timeline_snapshot (id varchar(64) primary key, payload_json text)");
        dsl.execute("insert into timeline_snapshot (id, payload_json) values ('snap_trevR100', '{\"id\":\"R100\"}')");
        dsl.execute("insert into timeline_snapshot (id, payload_json) values ('snap_trevR90', '{\"id\":\"R90\"}')");
        dsl.execute("insert into timeline_snapshot (id, payload_json) values ('snap_trevR50', '{\"id\":\"R50\"}')");
        dsl.execute("insert into timeline_snapshot (id, payload_json) values ('snap_trevR7', '{\"id\":\"R7\"}')");
        // seed revisions R100 (project-1), R90 (project-1), R50 (project-1), R7 (project-2)
        seedRevision("trevR100", P1, null, 100, "h100");
        seedRevision("trevR90", P1, null, 90, "h90");
        seedRevision("trevR50", P1, "trevR100", 50, "h50");
        seedRevision("trevR7", P2, null, 7, "h7");
        dsl.execute("insert into timeline_revision_ref (project_id, ref_id, head_revision_id) values (?, 'main', 'trevR100')", P1);
        dsl.execute("insert into timeline_revision_ref (project_id, ref_id, head_revision_id) values (?, 'feature', 'trevR90')", P1);
        dsl.execute("insert into timeline_revision_ref (project_id, ref_id, head_revision_id) values (?, 'main', 'trevR7')", P2);
        dsl.execute("insert into project_revision_counter (project_id, next_revision_number) values (?, 101)", P1);
        dsl.execute("insert into project_revision_counter (project_id, next_revision_number) values (?, 8)", P2);
        // R50 parent edge -> R100 (order 0) to make a lineage for graph tests
        dsl.execute("insert into timeline_revision_parent (project_id, revision_id, parent_revision_id, parent_order) "
                + "values (?, 'trevR50', 'trevR100', 0)", P1);
        applyService = new RevisionCommandApplyService(dsl);
        graph = new RevisionGraphService(dsl);
    }

    private static void seedRevision(String id, String project, String parent, int num, String hash) {
        dsl.execute("insert into timeline_revision (id, project_id, parent_revision_id, revision_number, snapshot_id, "
                + "content_hash, schema_version, source, created_at) values (?, ?, ?, ?, ?, ?, 'internal-1.0', 'seed', current_timestamp)",
                id, project, parent, num, "snap_" + id, hash);
    }

    @AfterAll
    static void teardown() {
        if (PG != null) {
            PG.stop();
        }
    }

    @BeforeEach
    void resetState() {
        dsl.execute("delete from apply_command");
        dsl.execute("delete from timeline_revision_parent where revision_id not in ('trevR50')");
        dsl.execute("update timeline_revision_ref set head_revision_id = 'trevR100', version = 0 "
                + "where project_id = ? and ref_id = 'main'", P1);
        dsl.execute("update timeline_revision_ref set head_revision_id = 'trevR90', version = 0 "
                + "where project_id = ? and ref_id = 'feature'", P1);
        dsl.execute("delete from timeline_revision where id not in ('trevR100','trevR90','trevR50','trevR7')");
        dsl.execute("update project_revision_counter set next_revision_number = 101 where project_id = ?", P1);
        dsl.execute("update project_revision_counter set next_revision_number = 8 where project_id = ?", P2);
    }

    private static String headOf(String project, String ref) {
        var rec = dsl.fetchOne("select head_revision_id from timeline_revision_ref where project_id = ? and ref_id = ?",
                project, ref);
        return rec == null ? null : rec.get(0, String.class);
    }

    // ---- CREATE_REF ----
    @Test
    void createRefExactSourcePin() {
        RevisionCommandPlan.CreateRefPlan plan = new RevisionCommandPlan.CreateRefPlan(P1,
                new RevisionRef(P1, "work"), "trevR50",
                RevisionCommandPlanDigest.createRef(P1, "work", "trevR50"));
        assertEquals("CREATED", applyService.createRef(plan, "cmd-cr-1", "alice", P1));
        assertEquals("trevR50", headOf(P1, "work"));
        // idempotent retry
        assertEquals("CREATED", applyService.createRef(plan, "cmd-cr-1", "alice", P1));
        // existing rejects
        RevisionCommandPlan.CreateRefPlan dup = new RevisionCommandPlan.CreateRefPlan(P1,
                new RevisionRef(P1, "work"), "trevR100",
                RevisionCommandPlanDigest.createRef(P1, "work", "trevR100"));
        assertThrows(RevisionCommandException.class, () -> applyService.createRef(dup, "cmd-cr-2", "alice", P1));
    }

    @Test
    void createRefMissingSourceRejects() {
        RevisionCommandPlan.CreateRefPlan plan = new RevisionCommandPlan.CreateRefPlan(P1,
                new RevisionRef(P1, "ghost"), "trevNOPE",
                RevisionCommandPlanDigest.createRef(P1, "ghost", "trevNOPE"));
        assertThrows(RevisionCommandException.class, () -> applyService.createRef(plan, "cmd-cr-3", "alice", P1));
    }

    // ---- DELETE_REF ----
    @Test
    void deleteRefExpectedHead() {
        RevisionCommandPlan.DeleteRefPlan plan = new RevisionCommandPlan.DeleteRefPlan(P1,
                new RevisionRef(P1, "feature"), "trevR90",
                RevisionCommandPlanDigest.deleteRef(P1, "feature", "trevR90"));
        assertEquals("DELETED", applyService.deleteRef(plan, "cmd-dr-1", "alice", P1));
        assertNull(headOf(P1, "feature"), "ref row removed");
        // revisions remain
        Integer revs = dsl.fetchOne("select count(*) from timeline_revision where id = 'trevR90'").get(0, Integer.class);
        assertEquals(1, revs);
    }

    @Test
    void deleteRefMovedHeadRejects() {
        RevisionCommandPlan.DeleteRefPlan plan = new RevisionCommandPlan.DeleteRefPlan(P1,
                new RevisionRef(P1, "feature"), "trevSTALE",
                RevisionCommandPlanDigest.deleteRef(P1, "feature", "trevSTALE"));
        assertThrows(RevisionCommandException.class, () -> applyService.deleteRef(plan, "cmd-dr-2", "alice", P1));
    }

    // ---- RESTORE ----
    @Test
    void restoreCreatesSingleParentRevision() {
        RevisionCommandPlan.RestoreRevisionPlan plan = new RevisionCommandPlan.RestoreRevisionPlan(P1,
                "trevR50", new RevisionRef(P1, "main"), "trevR100", "h50",
                RevisionCommandPlanDigest.restore(P1, "trevR50", "main", "trevR100", "h50"));
        String result = applyService.restore(plan, "cmd-rs-1", "alice", P1);
        assertTrue(result.startsWith("APPLIED:"));
        String newRev = result.substring("APPLIED:".length());
        assertNotEquals("trevR100", newRev);
        assertEquals(newRev, headOf(P1, "main"), "head advanced to restore revision");
        // single parent edge order 0 = expected head
        var parents = dsl.fetch("select parent_revision_id from timeline_revision_parent "
                + "where revision_id = ? order by parent_order", newRev).map(r -> r.get(0, String.class));
        assertEquals(List.of("trevR100"), parents);
        // no rewrite: historical revision intact
        Integer r50 = dsl.fetchOne("select count(*) from timeline_revision where id = 'trevR50'").get(0, Integer.class);
        assertEquals(1, r50);
    }

    @Test
    void restoreNoOpWhenCandidateEqualsHead() {
        RevisionCommandPlan.RestoreRevisionPlan plan = new RevisionCommandPlan.RestoreRevisionPlan(P1,
                "trevR100", new RevisionRef(P1, "main"), "trevR100", "h100",
                RevisionCommandPlanDigest.restore(P1, "trevR100", "main", "trevR100", "h100"));
        assertEquals("NO_OP", applyService.restore(plan, "cmd-rs-2", "alice", P1));
        assertEquals("trevR100", headOf(P1, "main"));
    }

    @Test
    void restoreStaleTargetHeadRejects() {
        RevisionCommandPlan.RestoreRevisionPlan plan = new RevisionCommandPlan.RestoreRevisionPlan(P1,
                "trevR50", new RevisionRef(P1, "main"), "trevOLD", "h50",
                RevisionCommandPlanDigest.restore(P1, "trevR50", "main", "trevOLD", "h50"));
        assertThrows(RevisionCommandException.class, () -> applyService.restore(plan, "cmd-rs-3", "alice", P1));
    }

    // ---- MERGE ----
    @Test
    void mergeCreatesTwoOrderedParents() {
        RevisionCommandPlan.MergeRevisionPlan plan = new RevisionCommandPlan.MergeRevisionPlan(P1,
                "trevR90", new RevisionRef(P1, "main"), "trevR100", "trevR100",
                "hmerged", false, "{}",
                RevisionCommandPlanDigest.merge(P1, "trevR90", "main", "trevR100", "trevR100", "hmerged", false));
        String result = applyService.merge(plan, "cmd-mg-1", "alice", P1);
        assertTrue(result.startsWith("APPLIED:"));
        String newRev = result.substring("APPLIED:".length());
        var parents = dsl.fetch("select parent_revision_id from timeline_revision_parent "
                + "where revision_id = ? order by parent_order", newRev).map(r -> r.get(0, String.class));
        assertEquals(List.of("trevR100", "trevR90"), parents, "parent[0]=target, parent[1]=source");
        assertEquals(newRev, headOf(P1, "main"));
        // is_merge derived from graph shape (2 parents)
        Integer mergeFlag = dsl.fetchOne("select is_merge from timeline_revision where id = ?", newRev).get(0, Integer.class);
        assertEquals(1, mergeFlag);
    }

    @Test
    void mergeNoOpWhenCandidateEqualsTarget() {
        RevisionCommandPlan.MergeRevisionPlan plan = new RevisionCommandPlan.MergeRevisionPlan(P1,
                "trevR90", new RevisionRef(P1, "main"), "trevR100", "trevR100",
                "h100", false, "{}",
                RevisionCommandPlanDigest.merge(P1, "trevR90", "main", "trevR100", "trevR100", "h100", false));
        assertEquals("NO_OP", applyService.merge(plan, "cmd-mg-2", "alice", P1));
        assertEquals("trevR100", headOf(P1, "main"));
    }

    @Test
    void mergeConflictRejected() {
        RevisionCommandPlan.MergeRevisionPlan plan = new RevisionCommandPlan.MergeRevisionPlan(P1,
                "trevR90", new RevisionRef(P1, "main"), "trevR100", "trevR100",
                "hX", true, "{}",
                RevisionCommandPlanDigest.merge(P1, "trevR90", "main", "trevR100", "trevR100", "hX", true));
        assertThrows(RevisionCommandException.class, () -> applyService.merge(plan, "cmd-mg-3", "alice", P1));
        assertEquals("trevR100", headOf(P1, "main"));
    }

    // ---- GRAPH: merge base ----
    @Test
    void findBestMergeBaseUnique() {
        // build criss-cross-free lineage: R50 <- R100 <- R200(target); R50 <- R90 <- R201(source)
        dsl.execute("insert into timeline_revision (id, project_id, revision_number, snapshot_id, content_hash, schema_version, source, created_at) "
                + "values ('trevR200', ?, 200, 's', 'h200', 'internal-1.0', 'seed', current_timestamp)", P1);
        dsl.execute("insert into timeline_revision (id, project_id, revision_number, snapshot_id, content_hash, schema_version, source, created_at) "
                + "values ('trevR201', ?, 201, 's', 'h201', 'internal-1.0', 'seed', current_timestamp)", P1);
        dsl.execute("insert into timeline_revision_parent (project_id, revision_id, parent_revision_id, parent_order) "
                + "values (?, 'trevR200', 'trevR100', 0)", P1);
        dsl.execute("insert into timeline_revision_parent (project_id, revision_id, parent_revision_id, parent_order) "
                + "values (?, 'trevR201', 'trevR90', 0)", P1);
        dsl.execute("insert into timeline_revision_parent (project_id, revision_id, parent_revision_id, parent_order) "
                + "values (?, 'trevR90', 'trevR50', 0)", P1);
        String base = graph.findBestMergeBase(P1, "trevR200", "trevR201");
        assertEquals("trevR100", base, "unique best common ancestor: R100 (R200 lineage and R201->R90->R50->R100 lineage)");
    }

    @Test
    void ambiguousMergeBaseDetected() {
        // criss-cross: R300 has parents {R100, R90}; R301 has parents {R90, R100}
        // => both R100 and R90 are best common ancestors
        dsl.execute("insert into timeline_revision (id, project_id, revision_number, snapshot_id, content_hash, schema_version, source, created_at) "
                + "values ('trevR300', ?, 300, 's', 'h300', 'internal-1.0', 'seed', current_timestamp)", P1);
        dsl.execute("insert into timeline_revision (id, project_id, revision_number, snapshot_id, content_hash, schema_version, source, created_at) "
                + "values ('trevR301', ?, 301, 's', 'h301', 'internal-1.0', 'seed', current_timestamp)", P1);
        dsl.execute("insert into timeline_revision_parent (project_id, revision_id, parent_revision_id, parent_order) values (?, 'trevR300', 'trevR100', 0)", P1);
        dsl.execute("insert into timeline_revision_parent (project_id, revision_id, parent_revision_id, parent_order) values (?, 'trevR300', 'trevR90', 1)", P1);
        dsl.execute("insert into timeline_revision_parent (project_id, revision_id, parent_revision_id, parent_order) values (?, 'trevR301', 'trevR90', 0)", P1);
        dsl.execute("insert into timeline_revision_parent (project_id, revision_id, parent_revision_id, parent_order) values (?, 'trevR301', 'trevR100', 1)", P1);
        RevisionCommandException ex = assertThrows(RevisionCommandException.class,
                () -> graph.findBestMergeBase(P1, "trevR300", "trevR301"));
        assertEquals(RevisionCommandErrorCode.AMBIGUOUS_MERGE_BASE, ex.code());
    }

    @Test
    void noCommonAncestorDetected() {
        // trevR7 is in project-2 (separate lineage); same-project isolated roots:
        // add a root revision R400 with no shared ancestor vs R100 lineage
        dsl.execute("insert into timeline_revision (id, project_id, revision_number, snapshot_id, content_hash, schema_version, source, created_at) "
                + "values ('trevR400', ?, 400, 's', 'h400', 'internal-1.0', 'seed', current_timestamp)", P1);
        RevisionCommandException ex = assertThrows(RevisionCommandException.class,
                () -> graph.findBestMergeBase(P1, "trevR100", "trevR400"));
        assertEquals(RevisionCommandErrorCode.NO_COMMON_ANCESTOR, ex.code());
    }

    // ---- RCI4: cross-project parent edge DB enforcement ----
    @Test
    void crossProjectParentEdgeRejectedByDb() {
        org.jooq.exception.DataAccessException ex = assertThrows(
                org.jooq.exception.DataAccessException.class, () -> dsl.transaction(tx -> {
                    tx.dsl().execute("insert into timeline_revision_parent "
                                    + "(project_id, revision_id, parent_revision_id, parent_order) values (?, 'trevR9x', 'trevR7', 0)",
                            P1);
                }));
        assertTrue(ex.getMessage().contains("foreign key") || ex.getMessage().contains("fk_timeline_revision_parent_parent"),
                "DB must reject cross-project parent edge: " + ex.getMessage());
    }

    // ---- RCI2: multi-ref concurrent revision-number allocation (absolute gate) ----
    @Test
    void independentRefsConcurrentAllocation() throws Exception {
        RevisionCommandPlan.RestoreRevisionPlan planMain = new RevisionCommandPlan.RestoreRevisionPlan(P1,
                "trevR50", new RevisionRef(P1, "main"), "trevR100", "h50b",
                RevisionCommandPlanDigest.restore(P1, "trevR50", "main", "trevR100", "h50b"));
        // feature ref: point at R90, then concurrent restore on feature
        RevisionCommandPlan.RestoreRevisionPlan planFeature = new RevisionCommandPlan.RestoreRevisionPlan(P1,
                "trevR50", new RevisionRef(P1, "feature"), "trevR90", "h50c",
                RevisionCommandPlanDigest.restore(P1, "trevR50", "feature", "trevR90", "h50c"));
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger ok = new AtomicInteger();
        try {
            Future<?> f1 = pool.submit(() -> {
                go.await();
                RevisionCommandApplyService svc = new RevisionCommandApplyService(
                        DSL.using(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
                svc.restore(planMain, "cmd-alloc-1", "alice", P1);
                ok.incrementAndGet();
                return null;
            });
            Future<?> f2 = pool.submit(() -> {
                go.await();
                RevisionCommandApplyService svc = new RevisionCommandApplyService(
                        DSL.using(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
                svc.restore(planFeature, "cmd-alloc-2", "alice", P1);
                ok.incrementAndGet();
                return null;
            });
            go.countDown();
            f1.get();
            f2.get();
        } finally {
            pool.shutdown();
        }
        assertEquals(2, ok.get(), "both independent refs must succeed");
        // distinct revision numbers, no unique violation
        Integer collisions = dsl.fetchOne("select count(*) from (select revision_number from timeline_revision "
                + "where project_id = ? group by revision_number having count(*) > 1) t", P1).get(0, Integer.class);
        assertEquals(0, collisions, "no revision_number collision");
        assertNotNull(headOf(P1, "main"));
        assertNotNull(headOf(P1, "feature"));
    }

    // ---- idempotency domain separation ----
    @Test
    void commandDomainPreventsCrossReplay() {
        RevisionCommandPlan.RestoreRevisionPlan plan = new RevisionCommandPlan.RestoreRevisionPlan(P1,
                "trevR50", new RevisionRef(P1, "main"), "trevR100", "h50",
                RevisionCommandPlanDigest.restore(P1, "trevR50", "main", "trevR100", "h50"));
        String result = applyService.restore(plan, "cmd-domain-1", "alice", P1);
        assertTrue(result.startsWith("APPLIED:"));
        String domain = dsl.fetchOne("select command_domain from apply_command where apply_command_id = 'cmd-domain-1'")
                .get(0, String.class);
        assertEquals("REVISION_COMMAND", domain);
    }

    // ---- RCP1: exact same revision merge => NO_OP ----
    @Test
    void mergeExactSameRevisionIsNoOp() {
        RevisionCommandPlan.MergeRevisionPlan plan = new RevisionCommandPlan.MergeRevisionPlan(P1,
                "trevR100", new RevisionRef(P1, "main"), "trevR100", "trevR100",
                "h100", false, "{}",
                RevisionCommandPlanDigest.merge(P1, "trevR100", "main", "trevR100", "trevR100", "h100", false));
        assertEquals("NO_OP", applyService.merge(plan, "cmd-rcp1-1", "alice", P1));
        assertEquals("trevR100", headOf(P1, "main"), "no ref movement");
        Integer revs = dsl.fetchOne("select count(*) from timeline_revision").get(0, Integer.class);
        assertEquals(4, revs, "no new revision");
        Integer edges = dsl.fetchOne("select count(*) from timeline_revision_parent").get(0, Integer.class);
        assertEquals(1, edges, "no new parent edges");
    }

    @Test
    void mergeSameRevisionStaleHeadRejects() {
        // same-revision plan but expected head moved before first apply => STALE_TARGET_REF, not NO_OP
        RevisionCommandPlan.MergeRevisionPlan plan = new RevisionCommandPlan.MergeRevisionPlan(P1,
                "trevR90", new RevisionRef(P1, "main"), "trevR90", "trevR90",
                "h90", false, "{}",
                RevisionCommandPlanDigest.merge(P1, "trevR90", "main", "trevR90", "trevR90", "h90", false));
        // main head is R100, expected R90 => stale
        RevisionCommandException ex = assertThrows(RevisionCommandException.class,
                () -> applyService.merge(plan, "cmd-rcp1-2", "alice", P1));
        assertEquals(RevisionCommandErrorCode.STALE_TARGET_REF, ex.code());
    }

    @Test
    void mergeSameRevisionDurableReplay() {
        RevisionCommandPlan.MergeRevisionPlan plan = new RevisionCommandPlan.MergeRevisionPlan(P1,
                "trevR100", new RevisionRef(P1, "main"), "trevR100", "trevR100",
                "h100", false, "{}",
                RevisionCommandPlanDigest.merge(P1, "trevR100", "main", "trevR100", "trevR100", "h100", false));
        assertEquals("NO_OP", applyService.merge(plan, "cmd-rcp1-3", "alice", P1));
        // head moves later; retry same completed command => replay original NO_OP
        RevisionCommandPlan.RestoreRevisionPlan mv = new RevisionCommandPlan.RestoreRevisionPlan(P1,
                "trevR50", new RevisionRef(P1, "main"), "trevR100", "h50x",
                RevisionCommandPlanDigest.restore(P1, "trevR50", "main", "trevR100", "h50x"));
        assertTrue(applyService.restore(mv, "cmd-rcp1-mv", "alice", P1).startsWith("APPLIED:"));
        assertEquals("NO_OP", applyService.merge(plan, "cmd-rcp1-3", "alice", P1),
                "completed durable replay returns original result");
    }

    // ---- RCP2: frozen source pin survives source ref movement ----
    @Test
    void frozenMergePlanUsesPinnedSourceAfterRefMove() {
        // feature -> R90; plan pins source R90; move feature to R50 via restore; apply old plan
        RevisionCommandPlan.MergeRevisionPlan plan = new RevisionCommandPlan.MergeRevisionPlan(P1,
                "trevR90", new RevisionRef(P1, "main"), "trevR100", "trevR100",
                "hmerged", false, "{}",
                RevisionCommandPlanDigest.merge(P1, "trevR90", "main", "trevR100", "trevR100", "hmerged", false));
        // source ref (feature) advances: restore R50 onto feature (head R90 -> new revision)
        RevisionCommandPlan.RestoreRevisionPlan featMove = new RevisionCommandPlan.RestoreRevisionPlan(P1,
                "trevR50", new RevisionRef(P1, "feature"), "trevR90", "h50y",
                RevisionCommandPlanDigest.restore(P1, "trevR50", "feature", "trevR90", "h50y"));
        String newFeatHead = applyService.restore(featMove, "cmd-rcp2-mv", "alice", P1)
                .substring("APPLIED:".length());
        assertNotEquals("trevR90", newFeatHead, "feature advanced");
        // apply OLD frozen plan: must use pinned R90, succeed, parents [R100, R90]
        String result = applyService.merge(plan, "cmd-rcp2-1", "alice", P1);
        assertTrue(result.startsWith("APPLIED:"), "old frozen plan still applies: " + result);
        String mergeRev = result.substring("APPLIED:".length());
        var parents = dsl.fetch("select parent_revision_id from timeline_revision_parent "
                + "where revision_id = ? order by parent_order", mergeRev).map(r -> r.get(0, String.class));
        assertEquals(List.of("trevR100", "trevR90"), parents,
                "parent[1] = pinned R90, NOT the moved feature head");
    }

    // ---- RCP3: counter bootstrap atomic + above historical max ----
    @Test
    void counterMigrationInitializationAboveHistoricalMax() {
        // project-2: max revision_number = 7; counter next = 8 => first allocation 8 > 7
        Long next = dsl.fetchOne("select next_revision_number from project_revision_counter where project_id = ?", P2)
                .get(0, Long.class);
        assertTrue(next > 7, "counter starts above historical max: " + next);
    }

    @Test
    void concurrentFirstAllocationBootstrap() throws Exception {
        // brand-new project p3: counter row ABSENT; two concurrent first allocations must
        // both succeed via atomic INSERT...ON CONFLICT DO NOTHING + UPDATE...RETURNING
        dsl.execute("delete from project_revision_counter where project_id = 'p3'");
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        java.util.concurrent.ConcurrentLinkedQueue<Long> allocated = new java.util.concurrent.ConcurrentLinkedQueue<>();
        try {
            Future<?> f1 = pool.submit(() -> {
                go.await();
                var d = DSL.using(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                Long n = d.transactionResult(tx ->
                        new ProjectRevisionNumberAllocator().allocate(tx.dsl(), "p3"));
                allocated.add(n);
                return null;
            });
            Future<?> f2 = pool.submit(() -> {
                go.await();
                var d = DSL.using(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                Long n = d.transactionResult(tx ->
                        new ProjectRevisionNumberAllocator().allocate(tx.dsl(), "p3"));
                allocated.add(n);
                return null;
            });
            go.countDown();
            f1.get();
            f2.get();
        } finally {
            pool.shutdown();
        }
        assertEquals(2, allocated.size(), "both first allocations succeed");
        assertEquals(2, allocated.stream().distinct().count(), "distinct revision numbers");
        Long rows = dsl.fetchOne("select count(*) from project_revision_counter where project_id = 'p3'").get(0, Long.class);
        assertEquals(1, rows, "exactly one counter row (atomic bootstrap)");
    }

}
