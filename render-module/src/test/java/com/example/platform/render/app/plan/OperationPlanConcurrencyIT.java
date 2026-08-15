package com.example.platform.render.app.plan;

import com.example.platform.render.domain.plan.ApplyContext;
import com.example.platform.render.domain.plan.ApplyResult;
import com.example.platform.render.domain.plan.AuthorizationDecision;
import com.example.platform.render.domain.plan.OperationPlan;
import com.example.platform.render.domain.plan.OperationPlanner;
import com.example.platform.render.domain.plan.PlanErrorCode;
import com.example.platform.render.domain.plan.PlanException;
import com.example.platform.render.domain.plan.TargetRevisionRef;
import com.example.platform.render.domain.operation.OperationDefinition;
import com.example.platform.render.domain.operation.OperationInstance;
import com.example.platform.render.domain.operation.OperationParameters;
import com.example.platform.render.domain.operation.OperationTarget;
import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineClipId;
import com.example.platform.render.domain.timeline.canonical.TimelineContentDigester;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.canonical.TimelineMetadata;
import com.example.platform.render.domain.timeline.canonical.TimelineTrack;
import com.example.platform.render.domain.timeline.canonical.TrackType;
import com.example.platform.render.domain.timeline.semantics.selection.ResolvedScope;
import com.example.platform.render.domain.timeline.semantics.selection.SelectionSpec;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.shared.time.MediaTime;
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
 * OPERATION_PLAN_TRANSACTION_MODEL_V1 (OPI1/OPI3 — FCV absolute gates):
 * REAL database-backed concurrent writer test + durable idempotency retry.
 * Two writers with the same expected head MUST yield exactly one SUCCESS and
 * one STALE_TARGET_REF (database-enforced CAS, never Java check-then-act).
 */
@Testcontainers
class OperationPlanConcurrencyIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DSLContext dsl;
    private static OperationPlanApplyService service;
    private static final String PROJECT = "project-1";

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
                    is_merge boolean not null default false
                )""");
        dsl.execute("""
                create table timeline_revision_ref (
                    project_id varchar(64) not null,
                    ref_id varchar(64) not null,
                    head_revision_id varchar(64),
                    version bigint not null default 0,
                    updated_at timestamp not null default current_timestamp,
                    primary key (project_id, ref_id)
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
                    completed_at timestamp
                )""");
        service = new OperationPlanApplyService(dsl);
        // seed base revision R100 + head row
        dsl.execute("insert into timeline_revision (id, project_id, revision_number, content_hash, source, created_at) "
                + "values ('trevR100', ?, 1, 'h-base', 'seed', current_timestamp)", PROJECT);
        dsl.execute("insert into timeline_revision_ref (project_id, ref_id, head_revision_id) values (?, 'main', 'trevR100')",
                PROJECT);
    }

    @BeforeEach
    void resetState() {
        // isolate tests from each other: reset head + remove child revisions/commands
        dsl.execute("delete from apply_command");
        dsl.execute("delete from timeline_revision where id <> 'trevR100'");
        dsl.execute("update timeline_revision_ref set head_revision_id = 'trevR100', version = 0 "
                + "where project_id = ? and ref_id = 'main'", PROJECT);
    }

    @AfterAll
    static void teardown() {
        if (PG != null) {
            PG.stop();
        }
    }

    private static TimelineClip clip(String id) {
        return new TimelineClip(id, "asset-1", "stream-1", "artifact-1", "digest-1",
                MediaTime.ofRational(0, 1), MediaTime.ofRational(4, 1),
                MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
    }

    private static TimelineDocument baseDoc() {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("t1", "main", TrackType.VIDEO, List.of(clip("clip-a")))),
                TimelineMetadata.empty());
    }

    private static OperationInstance instance(String baseHash, MediaTime delta) {
        return new OperationInstance(OperationDefinition.V1.MOVE.definitionId(), ContractVersion.of(1, 0),
                "trevR100", baseHash,
                new OperationTarget.ResolvedClipScopeTarget(new ResolvedScope("trevR100", baseHash,
                        List.of(TimelineClipId.of("clip-a")), SelectionSpec.ExpansionPolicy.EXACT)),
                new OperationParameters.MoveParameters(delta, false), "pd", null);
    }

    private static AuthorizationDecision auth(OperationPlan plan, String principal, String refId) {
        return AuthorizationDecision.allow(plan.planDigest(), principal, PROJECT, refId, "policy-v1");
    }

    @Test
    void concurrentWritersSameExpectedHeadExactlyOneWinner() throws Exception {
        TimelineDocument base = baseDoc();
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlanner planner = new OperationPlanner();
        OperationPlan planA = planner.plan(instance(baseHash, MediaTime.ofRational(1, 1)), base);
        OperationPlan planB = planner.plan(instance(baseHash, MediaTime.ofRational(2, 1)), base);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger stale = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> f1 = pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    service.apply(planA, new ApplyContext("cmd-A", new TargetRevisionRef("main"),
                            "trevR100", "p-a", auth(planA, "p-a", "main")), PROJECT);
                    success.incrementAndGet();
                } catch (PlanException e) {
                    if (e.code() == PlanErrorCode.STALE_TARGET_REF) {
                        stale.incrementAndGet();
                    } else {
                        throw e;
                    }
                }
                return null;
            });
            Future<?> f2 = pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    service.apply(planB, new ApplyContext("cmd-B", new TargetRevisionRef("main"),
                            "trevR100", "p-b", auth(planB, "p-b", "main")), PROJECT);
                    success.incrementAndGet();
                } catch (PlanException e) {
                    if (e.code() == PlanErrorCode.STALE_TARGET_REF) {
                        stale.incrementAndGet();
                    } else {
                        throw e;
                    }
                }
                return null;
            });
            ready.await();
            go.countDown();
            f1.get();
            f2.get();
        } finally {
            pool.shutdown();
        }
        assertEquals(1, success.get(), "exactly one writer must succeed");
        assertEquals(1, stale.get(), "exactly one writer must observe STALE_TARGET_REF");
        // exactly one authoritative child revision
        Integer children = dsl.fetchOne("select count(*) from timeline_revision where parent_revision_id = 'trevR100'")
                .get(0, Integer.class);
        assertEquals(1, children, "exactly one successful child revision");
    }

    @Test
    void durableIdempotencyReplaysOriginalResult() {
        TimelineDocument base = baseDoc();
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlanner planner = new OperationPlanner();
        OperationPlan plan = planner.plan(instance(baseHash, MediaTime.ofRational(1, 1)), base);
        ApplyContext ctx = new ApplyContext("cmd-idem-1", new TargetRevisionRef("main"),
                "trevR100", "p-x", auth(plan, "p-x", "main"));
        ApplyResult first = service.apply(plan, ctx, PROJECT);
        assertEquals(ApplyResult.APPLIED, first.status());
        ApplyResult replay = service.apply(plan, ctx, PROJECT);
        assertEquals(first.newRevisionId(), replay.newRevisionId(), "retry must replay original result");
        Integer count = dsl.fetchOne("select count(*) from apply_command where apply_command_id = 'cmd-idem-1'")
                .get(0, Integer.class);
        assertEquals(1, count);
    }

    @Test
    void idempotencyKeyConflictOnDifferentPlan() {
        TimelineDocument base = baseDoc();
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlanner planner = new OperationPlanner();
        OperationPlan plan1 = planner.plan(instance(baseHash, MediaTime.ofRational(1, 1)), base);
        OperationPlan plan2 = planner.plan(instance(baseHash, MediaTime.ofRational(2, 1)), base);
        service.apply(plan1, new ApplyContext("cmd-conflict-1", new TargetRevisionRef("main"),
                "trevR100", "p-x", auth(plan1, "p-x", "main")), PROJECT);
        PlanException ex = assertThrows(PlanException.class, () ->
                service.apply(plan2, new ApplyContext("cmd-conflict-1", new TargetRevisionRef("main"),
                        "trevR100", "p-x", auth(plan2, "p-x", "main")), PROJECT));
        assertEquals(PlanErrorCode.IDEMPOTENCY_KEY_CONFLICT, ex.code());
    }

    @Test
    void staleTargetRefRejected() {
        TimelineDocument base = baseDoc();
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlanner planner = new OperationPlanner();
        OperationPlan plan = planner.plan(instance(baseHash, MediaTime.ofRational(1, 1)), base);
        PlanException ex = assertThrows(PlanException.class, () ->
                service.apply(plan, new ApplyContext("cmd-stale-1", new TargetRevisionRef("main"),
                        "trevNOPE", "p-x", auth(plan, "p-x", "main")), PROJECT));
        assertEquals(PlanErrorCode.STALE_TARGET_REF, ex.code());
    }

    @Test
    void noOpStaleHeadRejected_firstExecution() {
        // OPC1 CASE B: plan is no-op vs R100 but head moved to R101 before first apply
        TimelineDocument base = baseDoc();
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlanner planner = new OperationPlanner();
        OperationPlan noOpPlan = planner.plan(instance(baseHash, MediaTime.ZERO), base); // move delta 0 => no-op
        assertTrue(noOpPlan.noOp());
        // head still R100 -> NO_OP success (CASE A)
        ApplyResult ok = service.apply(noOpPlan, new ApplyContext("cmd-noop-ok", new TargetRevisionRef("main"),
                "trevR100", "p-x", auth(noOpPlan, "p-x", "main")), PROJECT);
        assertEquals(ApplyResult.NO_OP, ok.status());
        // move head to R101
        TimelineDocument base2 = baseDoc();
        String baseHash2 = new TimelineContentDigester().digest(base2);
        OperationPlan movePlan = planner.plan(instance(baseHash2, MediaTime.ofRational(1, 1)), base2);
        service.apply(movePlan, new ApplyContext("cmd-move-1", new TargetRevisionRef("main"),
                "trevR100", "p-x", auth(movePlan, "p-x", "main")), PROJECT);
        // new NO_OP command, expected head R100, current R101 -> STALE_TARGET_REF
        OperationPlan noOp2 = planner.plan(instance(baseHash2, MediaTime.ZERO), base2);
        assertTrue(noOp2.noOp());
        PlanException ex = assertThrows(PlanException.class, () ->
                service.apply(noOp2, new ApplyContext("cmd-noop-stale", new TargetRevisionRef("main"),
                        "trevR100", "p-x", auth(noOp2, "p-x", "main")), PROJECT));
        assertEquals(PlanErrorCode.STALE_TARGET_REF, ex.code());
    }

    @Test
    void completedNoOpReplayAfterHeadMove_returnsOriginalResult() {
        // OPC1 CASE C: completed durable no-op replays original NO_OP after head moves
        TimelineDocument base = baseDoc();
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlanner planner = new OperationPlanner();
        OperationPlan noOpPlan = planner.plan(instance(baseHash, MediaTime.ZERO), base);
        ApplyContext ctx = new ApplyContext("cmd-noop-done", new TargetRevisionRef("main"),
                "trevR100", "p-x", auth(noOpPlan, "p-x", "main"));
        ApplyResult first = service.apply(noOpPlan, ctx, PROJECT);
        assertEquals(ApplyResult.NO_OP, first.status());
        // move head
        OperationPlan movePlan = planner.plan(instance(baseHash, MediaTime.ofRational(1, 1)), base);
        service.apply(movePlan, new ApplyContext("cmd-move-2", new TargetRevisionRef("main"),
                "trevR100", "p-x", auth(movePlan, "p-x", "main")), PROJECT);
        // replay completed command -> original NO_OP, NOT stale
        ApplyResult replay = service.apply(noOpPlan, ctx, PROJECT);
        assertEquals(ApplyResult.NO_OP, replay.status());
    }

    @Test
    void idempotencyReplayRejectsDifferentPrincipal() {
        // OPC2 CASE B: Bob cannot replay Alice's completed command
        TimelineDocument base = baseDoc();
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlanner planner = new OperationPlanner();
        OperationPlan plan = planner.plan(instance(baseHash, MediaTime.ofRational(1, 1)), base);
        ApplyContext alice = new ApplyContext("cmd-alice-1", new TargetRevisionRef("main"),
                "trevR100", "alice", auth(plan, "alice", "main"));
        ApplyResult first = service.apply(plan, alice, PROJECT);
        assertEquals(ApplyResult.APPLIED, first.status());
        PlanException ex = assertThrows(PlanException.class, () ->
                service.apply(plan, new ApplyContext("cmd-alice-1", new TargetRevisionRef("main"),
                        "trevR100", "bob", auth(plan, "bob", "main")), PROJECT));
        assertEquals(PlanErrorCode.IDEMPOTENCY_KEY_CONFLICT, ex.code());
    }

    @Test
    void idempotencyReplayRejectsDifferentTargetRef() {
        // OPC2 CASE C: same plan/principal, different target ref -> conflict
        TimelineDocument base = baseDoc();
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlanner planner = new OperationPlanner();
        OperationPlan plan = planner.plan(instance(baseHash, MediaTime.ofRational(1, 1)), base);
        ApplyContext ctx = new ApplyContext("cmd-ref-1", new TargetRevisionRef("main"),
                "trevR100", "alice", auth(plan, "alice", "main"));
        service.apply(plan, ctx, PROJECT);
        PlanException ex = assertThrows(PlanException.class, () ->
                service.apply(plan, new ApplyContext("cmd-ref-1", new TargetRevisionRef("other"),
                        "trevR100", "alice", auth(plan, "alice", "other")), PROJECT));
        assertEquals(PlanErrorCode.IDEMPOTENCY_KEY_CONFLICT, ex.code());
    }

    @Test
    void headAdvancesToNewRevisionOnApply() {
        // regression: CAS must set head to the new revision (not null)
        TimelineDocument base = baseDoc();
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlanner planner = new OperationPlanner();
        OperationPlan plan = planner.plan(instance(baseHash, MediaTime.ofRational(1, 1)), base);
        ApplyResult result = service.apply(plan, new ApplyContext("cmd-head-1", new TargetRevisionRef("main"),
                "trevR100", "alice", auth(plan, "alice", "main")), PROJECT);
        String head = dsl.fetchOne("select head_revision_id from timeline_revision_ref "
                + "where project_id = ? and ref_id = 'main'", PROJECT).get(0, String.class);
        assertEquals(result.newRevisionId(), head, "head must advance to the new revision");
    }

}
