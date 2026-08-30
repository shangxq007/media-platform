package com.example.platform.render.app.plan;

import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationDefinitionVersion;
import com.example.platform.operation.operation.OperationInstance;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationTarget;
import com.example.platform.operation.plan.ApplyContext;
import com.example.platform.operation.plan.AuthorizationDecision;
import com.example.platform.operation.plan.OperationPlanner;
import com.example.platform.operation.plan.TargetRevisionRef;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.timeline.adapter.JdbcEffectDefinitionVersionRegistry;
import com.example.platform.timeline.adapter.JdbcEffectSemanticSnapshotStore;
import com.example.platform.timeline.adapter.JdbcTimelineRevisionSemanticContextStore;
import com.example.platform.timeline.adapter.RevisionCommandApplyService;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.app.DefaultTimelineRevisionPersistence;
import com.example.platform.timeline.app.ProductCurrentRevisionHeadUpdateAdapter;
import com.example.platform.timeline.app.ProductCurrentRevisionService;
import com.example.platform.timeline.app.TimelineRevisionCommandConflictException;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.selection.ResolvedScope;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import com.example.platform.timeline.version.TimelineConflictException;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import com.example.platform.timeline.revisioncommand.RevisionCommandException;
import com.example.platform.timeline.revisioncommand.RevisionCommandPlan;
import com.example.platform.timeline.revisioncommand.RevisionCommandPlanDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port of the Operation transaction controls to Timeline's canonical writer.
 * The retired Render-side revision SQL path is not reconstructed: concurrency,
 * durable replay, immutable command context, stale-base handling, no-op rules,
 * canonical ref publication and rollback integrity are exercised through the
 * Timeline-owned command transaction.
 */
class OperationPlanConcurrencyIT extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-operation-it";
    private static DataSource dataSource;
    private static DSLContext dsl;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        RenderTestSchemaFixture.createSchema(dsl);
        dsl.execute("""
                create table if not exists apply_command (
                    apply_command_id varchar(64) primary key,
                    plan_digest varchar(64) not null,
                    fingerprint varchar(64) not null,
                    status varchar(16) not null,
                    result_revision_id varchar(64),
                    result_content_hash varchar(64),
                    result_status varchar(16),
                    project_id varchar(64),
                    command_domain varchar(32) not null,
                    created_at timestamp not null default current_timestamp,
                    completed_at timestamp)
                """);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void reset() {
        RenderTestSchemaFixture.truncate(dsl);
        dsl.execute("delete from apply_command");
        com.example.platform.shared.web.TenantContext.set(TENANT);
    }

    @AfterEach
    void clearTenant() {
        com.example.platform.shared.web.TenantContext.clear();
    }

    @Test
    void concurrentCommandsWithSameBaseYieldOneRevisionAndOneStaleBase() throws Exception {
        String productId = "operation-concurrency-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        TimelineRevisionSaveService seedWriter = writer(dsl);
        TimelineDocument base = document("base");
        var root = seedRoot(seedWriter, productId, base);

        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var applied = new AtomicInteger();
        var stale = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> runWriter(
                    productId, root.revisionId(), document("candidate-a"),
                    "command-a", ready, go, applied, stale));
            var second = pool.submit(() -> runWriter(
                    productId, root.revisionId(), document("candidate-b"),
                    "command-b", ready, go, applied, stale));
            ready.await();
            go.countDown();
            first.get();
            second.get();
        }

        assertEquals(1, applied.get());
        assertEquals(1, stale.get());
        assertEquals(1L, dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId))
                .and(TIMELINE_REVISION.PARENT_REVISION_ID.eq(root.revisionId()))
                .fetchOne(0, Long.class));
        assertEquals(1, dsl.fetchOne("select count(*) from timeline_revision_parent "
                + "where project_id = ? and parent_revision_id = ? and parent_order = 0",
                productId, root.revisionId()).get(0, Integer.class));
        assertEquals(1, dsl.fetchOne(
                "select count(*) from apply_command where project_id = ? and status = 'COMPLETED'",
                productId).get(0, Integer.class));
    }

    @Test
    void operationPlanCoordinatorPersistsCanonicalParentExactlyEqualToPlanBase() {
        String productId = "operation-plan-parent-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        TimelineRevisionSaveService service = writer(dsl);
        TimelineDocument base = editableDocument();
        var root = seedRoot(service, productId, base);
        String baseHash = new TimelineContentDigester().digest(base);
        var instance = new OperationInstance(OperationDefinition.V1.MOVE.definitionId(),
                OperationDefinitionVersion.of(1, 0), root.revisionId(), baseHash,
                new OperationTarget.ResolvedClipScopeTarget(new ResolvedScope(
                        root.revisionId(), baseHash, List.of(TimelineClipId.of("clip-1")),
                        SelectionSpec.ExpansionPolicy.EXACT)),
                new OperationParameters.MoveParameters(MediaTime.ofRational(1, 1), false),
                "parameters", null);
        var plan = new OperationPlanner().plan(instance, base);
        var authorization = AuthorizationDecision.allow(plan.planDigest(), "alice", productId,
                TENANT, RevisionRef.MAIN_REF, "policy-v1");
        var context = new ApplyContext("coordinator-parent-edge",
                new TargetRevisionRef(RevisionRef.MAIN_REF), root.revisionId(), TENANT,
                "alice", authorization);

        var result = new OperationPlanApplyService(service)
                .apply(plan, context, productId, base);

        assertEquals(root.revisionId(), result.parentRevisionId());
        assertEquals(root.revisionId(), dsl.fetchOne(
                "select parent_revision_id from timeline_revision_parent "
                        + "where project_id = ? and revision_id = ? and parent_order = 0",
                productId, result.newRevisionId()).get(0, String.class));
    }

    @Test
    void durableIdempotencyReplaysOriginalResult() {
        String productId = "operation-replay-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        TimelineRevisionSaveService service = writer(dsl);
        var root = seedRoot(service, productId, document("base"));
        var command = command("command-replay", "plan-a", "fingerprint-a");

        var first = service.saveRevisionForCommand(
                ref(productId), root.revisionId(), document("candidate"), "editor", command);
        var replay = service.saveRevisionForCommand(
                ref(productId), root.revisionId(), document("candidate"), "editor", command);

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(first.revisionId(), replay.revisionId());
        assertEquals(1, commandRows("command-replay"));
        assertEquals(2L, revisionRows(productId));
    }

    @Test
    void idempotencyKeyConflictOnDifferentPlan() {
        String productId = "operation-plan-conflict-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        TimelineRevisionSaveService service = writer(dsl);
        var root = seedRoot(service, productId, document("base"));
        service.saveRevisionForCommand(ref(productId), root.revisionId(), document("candidate-a"),
                "editor", command("command-plan-conflict", "plan-a", "fingerprint-a"));

        assertThrows(TimelineRevisionCommandConflictException.class, () ->
                service.saveRevisionForCommand(ref(productId), root.revisionId(), document("candidate-b"),
                        "editor", command("command-plan-conflict", "plan-b", "fingerprint-b")));
        assertEquals(2L, revisionRows(productId));
    }

    @Test
    void staleTargetRefRejected() {
        String productId = "operation-stale-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        TimelineRevisionSaveService service = writer(dsl);
        var root = seedRoot(service, productId, document("base"));
        service.saveRevisionForCommand(ref(productId), root.revisionId(), document("candidate-a"),
                "editor", command("command-advance", "plan-a", "fingerprint-a"));

        assertThrows(TimelineConflictException.class, () ->
                service.saveRevisionForCommand(ref(productId), root.revisionId(), document("candidate-b"),
                        "editor", command("command-stale", "plan-b", "fingerprint-b")));
        assertEquals(0, commandRows("command-stale"), "stale command claim must roll back");
        assertEquals(2L, revisionRows(productId));
    }

    @Test
    void noOpStaleHeadRejected_firstExecution() {
        String productId = "operation-noop-stale-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        TimelineRevisionSaveService service = writer(dsl);
        TimelineDocument base = document("base");
        var root = seedRoot(service, productId, base);
        String baseHash = new TimelineContentDigester().digest(base);
        var firstNoOp = service.recordNoOpCommand(ref(productId), root.revisionId(), baseHash,
                command("command-noop-ok", "plan-noop", "fingerprint-noop-ok"));
        assertFalse(firstNoOp.replayed());
        assertNull(firstNoOp.revisionId());

        service.saveRevisionForCommand(ref(productId), root.revisionId(), document("candidate"),
                "editor", command("command-move", "plan-move", "fingerprint-move"));
        assertThrows(TimelineConflictException.class, () ->
                service.recordNoOpCommand(ref(productId), root.revisionId(), baseHash,
                        command("command-noop-stale", "plan-noop", "fingerprint-noop-stale")));
        assertEquals(0, commandRows("command-noop-stale"), "stale no-op claim must roll back");
        assertEquals(2L, revisionRows(productId), "stale no-op creates no revision");
        assertEquals(1, dsl.fetchOne("select count(*) from timeline_revision_parent "
                + "where project_id = ?", productId).get(0, Integer.class),
                "stale no-op creates no parent edge");
    }

    @Test
    void completedNoOpReplayAfterHeadMove_returnsOriginalResult() {
        String productId = "operation-noop-replay-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        TimelineRevisionSaveService service = writer(dsl);
        TimelineDocument base = document("base");
        var root = seedRoot(service, productId, base);
        String baseHash = new TimelineContentDigester().digest(base);
        var command = command("command-noop-replay", "plan-noop", "fingerprint-noop");
        var first = service.recordNoOpCommand(ref(productId), root.revisionId(), baseHash, command);
        service.saveRevisionForCommand(ref(productId), root.revisionId(), document("candidate"),
                "editor", command("command-move-after-noop", "plan-move", "fingerprint-move"));

        var replay = service.recordNoOpCommand(ref(productId), root.revisionId(), baseHash, command);
        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertNull(replay.revisionId());
        assertEquals(baseHash, replay.timelineContentHash());
    }

    @Test
    void idempotencyReplayRejectsDifferentPrincipal() {
        assertImmutableCommandContextConflict("principal", "alice", "bob");
    }

    @Test
    void idempotencyReplayRejectsDifferentTargetRef() {
        assertImmutableCommandContextConflict("target-ref", "current", "other");
    }

    @Test
    void headAdvancesToNewRevisionOnApply() {
        String productId = "operation-head-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        TimelineRevisionSaveService service = writer(dsl);
        var root = seedRoot(service, productId, document("base"));
        var result = service.saveRevisionForCommand(
                ref(productId), root.revisionId(), document("candidate"), "editor",
                command("command-head", "plan-head", "fingerprint-head"));

        assertEquals(result.revisionId(), canonicalHead(productId));
        assertEquals(root.revisionId(), currentRevision(productId),
                "product pointer is a non-authoritative projection for H7");
        assertEquals(1L, dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(result.revisionId())).fetchOne(0, Long.class));
    }

    @Test
    void staleCanonicalRefAfterPersistenceRollsBackCounterGraphAndCommand() {
        String productId = "operation-rollback-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        TimelineRevisionSaveService seed = writer(dsl);
        var root = seedRoot(seed, productId, document("base"));
        long counterBefore = counterValue(productId);
        dsl.execute("delete from timeline_revision_ref where project_id = ? and ref_id = ?",
                productId, RevisionRef.MAIN_REF);

        assertThrows(TimelineConflictException.class, () -> seed.saveRevisionForCommand(
                ref(productId), root.revisionId(), document("candidate"), "editor",
                command("command-rollback", "plan-rollback", "fingerprint-rollback")));
        assertEquals(root.revisionId(), currentRevision(productId));
        assertEquals(1L, revisionRows(productId));
        assertEquals(0, dsl.fetchOne("select count(*) from timeline_revision_parent "
                + "where project_id = ?", productId).get(0, Integer.class));
        assertEquals(0, commandRows("command-rollback"));
        assertEquals(counterBefore, counterValue(productId));
    }

    @Test
    void genesisCommandCreatesUniqueCanonicalRefWithZeroParentEdges() {
        String productId = "operation-genesis-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        TimelineRevisionSaveService service = writer(dsl);

        var genesis = service.saveRevisionForCommand(
                ref(productId), null, document("genesis"), "editor",
                command("command-genesis", "plan-genesis", "fingerprint-genesis"));

        assertEquals(genesis.revisionId(), canonicalHead(productId));
        assertEquals(0, dsl.fetchOne("select count(*) from timeline_revision_parent "
                + "where project_id = ?", productId).get(0, Integer.class));
        assertNull(currentRevision(productId),
                "genesis does not need a product-pointer authority row transition");
    }

    @Test
    void canonicalRefAllowsApplyWhenProductPointerMismatches() {
        String productId = "operation-product-mismatch-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        TimelineRevisionSaveService service = writer(dsl);
        var root = seedRoot(service, productId, document("base"));
        dsl.update(PRODUCT).set(PRODUCT.CURRENT_REVISION_ID, (String) null)
                .where(PRODUCT.PRODUCT_ID.eq(productId)).execute();

        var applied = service.saveRevisionForCommand(
                ref(productId), root.revisionId(), document("candidate"), "editor",
                command("command-product-mismatch", "plan-product-mismatch",
                        "fingerprint-product-mismatch"));

        assertEquals(applied.revisionId(), canonicalHead(productId));
        assertNull(currentRevision(productId));
    }

    @Test
    void canonicalRefAllowsNoOpWhenProductPointerMismatches() {
        String productId = "operation-noop-product-mismatch-" + java.util.UUID.randomUUID().toString().replace("-", "");
        insertProduct(productId);
        TimelineRevisionSaveService service = writer(dsl);
        TimelineDocument base = document("base");
        var root = seedRoot(service, productId, base);
        dsl.update(PRODUCT).set(PRODUCT.CURRENT_REVISION_ID, (String) null)
                .where(PRODUCT.PRODUCT_ID.eq(productId)).execute();

        var noOp = service.recordNoOpCommand(ref(productId), root.revisionId(),
                new TimelineContentDigester().digest(base),
                command("noop-product-mismatch", "plan-noop-product", "fp-noop-product"));

        assertFalse(noOp.replayed());
        assertEquals(root.revisionId(), canonicalHead(productId));
        assertNull(currentRevision(productId));
    }

    @Test
    void concurrentGenesisWritersPublishExactlyOneRoot() throws Exception {
        String productId = "operation-genesis-race-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger applied = new AtomicInteger();
        AtomicInteger stale = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> runGenesisWriter(
                    productId, "genesis-a", go, applied, stale));
            var second = pool.submit(() -> runGenesisWriter(
                    productId, "genesis-b", go, applied, stale));
            go.countDown();
            first.get();
            second.get();
        }

        assertEquals(1, applied.get());
        assertEquals(1, stale.get());
        assertEquals(1L, revisionRows(productId));
        assertEquals(0, dsl.fetchOne("select count(*) from timeline_revision_parent "
                + "where project_id = ?", productId).get(0, Integer.class));
        assertEquals(1, dsl.fetchOne("select count(*) from timeline_revision_ref "
                + "where project_id = ? and ref_id = ?", productId, RevisionRef.MAIN_REF)
                .get(0, Integer.class));
    }

    @Test
    void failureImmediatelyAfterCommandClaimRollsBackClaim() {
        String productId = "operation-claim-failure-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        TimelineRevisionSaveService service = writer(dsl);
        var root = seedRoot(service, productId, document("base"));
        dsl.execute("create function h7_reject_counter_update() returns trigger language plpgsql as $$ "
                + "begin raise exception 'counter rejected'; end $$");
        dsl.execute("create trigger h7_reject_counter before update on project_revision_counter "
                + "for each row when (old.project_id = '" + productId + "') "
                + "execute function h7_reject_counter_update()");
        try {
            assertThrows(RuntimeException.class, () -> service.saveRevisionForCommand(
                    ref(productId), root.revisionId(), document("candidate"), "editor",
                    command("claim-failure", "plan-claim-failure", "fp-claim-failure")));
        } finally {
            dsl.execute("drop trigger h7_reject_counter on project_revision_counter");
            dsl.execute("drop function h7_reject_counter_update()");
        }
        assertEquals(0, commandRows("claim-failure"));
        assertEquals(1L, revisionRows(productId));
        assertEquals(root.revisionId(), canonicalHead(productId));
    }

    @Test
    void failureAfterRefPublicationStatementRollsBackEverything() {
        String productId = "operation-publication-failure-" + java.util.UUID.randomUUID().toString().replace("-", "");
        insertProduct(productId);
        TimelineRevisionSaveService service = writer(dsl);
        var root = seedRoot(service, productId, document("base"));
        long counterBefore = counterValue(productId);
        dsl.execute("create function h7_reject_ref_publication() returns trigger language plpgsql as $$ "
                + "begin raise exception 'ref publication rejected'; end $$");
        dsl.execute("create trigger h7_reject_ref after update on timeline_revision_ref "
                + "for each row when (old.project_id = '" + productId + "') "
                + "execute function h7_reject_ref_publication()");
        try {
            assertThrows(RuntimeException.class, () -> service.saveRevisionForCommand(
                    ref(productId), root.revisionId(), document("candidate"), "editor",
                    command("publication-failure", "plan-publication-failure",
                            "fp-publication-failure")));
        } finally {
            dsl.execute("drop trigger h7_reject_ref on timeline_revision_ref");
            dsl.execute("drop function h7_reject_ref_publication()");
        }
        assertEquals(0, commandRows("publication-failure"));
        assertEquals(1L, revisionRows(productId));
        assertEquals(0, dsl.fetchOne("select count(*) from timeline_revision_parent "
                + "where project_id = ?", productId).get(0, Integer.class));
        assertEquals(root.revisionId(), canonicalHead(productId));
        assertEquals(counterBefore, counterValue(productId));
    }

    @Test
    void revisionCommandHeadMoveMakesH7StaleAndH7MoveMakesRevisionCommandStale() {
        String projectA = "cross-head-rc-first-" + java.util.UUID.randomUUID();
        insertProduct(projectA);
        TimelineRevisionSaveService serviceA = writer(dsl);
        var rootA = seedRoot(serviceA, projectA, document("base-a"));
        var childA = serviceA.saveRevision(
                projectA, rootA.revisionId(), document("detached-child"), "seed");
        RevisionCommandApplyService revisionCommands = new RevisionCommandApplyService(dsl);
        var deleteMain = new RevisionCommandPlan.DeleteRefPlan(projectA, ref(projectA),
                rootA.revisionId(), RevisionCommandPlanDigest.deleteRef(
                        projectA, RevisionRef.MAIN_REF, rootA.revisionId()));
        revisionCommands.deleteRef(deleteMain, "rc-delete-main", "alice", projectA);
        var recreateMain = new RevisionCommandPlan.CreateRefPlan(projectA, ref(projectA),
                childA.revisionId(), RevisionCommandPlanDigest.createRef(
                        projectA, RevisionRef.MAIN_REF, childA.revisionId()));
        revisionCommands.createRef(recreateMain, "rc-recreate-main", "alice", projectA);

        assertThrows(TimelineConflictException.class, () -> serviceA.saveRevisionForCommand(
                ref(projectA), rootA.revisionId(), document("stale-h7"), "editor",
                command("stale-h7-after-rc", "plan-stale-h7", "fp-stale-h7")));

        String projectB = "cross-head-h7-first-" + java.util.UUID.randomUUID();
        insertProduct(projectB);
        TimelineRevisionSaveService serviceB = writer(dsl);
        var rootB = seedRoot(serviceB, projectB, document("base-b"));
        serviceB.saveRevisionForCommand(ref(projectB), rootB.revisionId(),
                document("h7-move"), "editor",
                command("h7-move-before-rc", "plan-h7-move", "fp-h7-move"));
        var staleDelete = new RevisionCommandPlan.DeleteRefPlan(projectB, ref(projectB),
                rootB.revisionId(), RevisionCommandPlanDigest.deleteRef(
                        projectB, RevisionRef.MAIN_REF, rootB.revisionId()));
        assertThrows(RevisionCommandException.class, () ->
                revisionCommands.deleteRef(staleDelete, "rc-stale-delete", "alice", projectB));
    }

    @Test
    void h7AndRevisionCommandShareFirstCounterBootstrapWithoutDuplicateNumbers() throws Exception {
        String productId = "cross-counter-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        TimelineRevisionSaveService seed = writer(dsl);
        var root = seedRoot(seed, productId, document("base"));
        RevisionRef feature = new RevisionRef(productId, "feature");
        RevisionCommandApplyService setupCommands = new RevisionCommandApplyService(dsl);
        setupCommands.createRef(new RevisionCommandPlan.CreateRefPlan(productId, feature,
                        root.revisionId(), RevisionCommandPlanDigest.createRef(
                                productId, feature.refId(), root.revisionId())),
                "counter-feature-create", "alice", productId);
        dsl.execute("delete from project_revision_counter where project_id = ?", productId);
        dsl.execute("update timeline_revision set revision_number = 1, internal_revision = 1 "
                + "where project_id = ? and id = ?", productId, root.revisionId());

        var restorePlan = new RevisionCommandPlan.RestoreRevisionPlan(productId,
                root.revisionId(), feature, root.revisionId(), "restore-content-hash",
                RevisionCommandPlanDigest.restore(productId, root.revisionId(), feature.refId(),
                        root.revisionId(), "restore-content-hash"));
        CountDownLatch go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var h7 = pool.submit(() -> {
                go.await();
                return writer(DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES))
                        .saveRevisionForCommand(ref(productId), root.revisionId(),
                                document("h7-candidate"), "editor",
                                command("cross-counter-h7", "plan-counter-h7", "fp-counter-h7"));
            });
            var rc = pool.submit(() -> {
                go.await();
                return new RevisionCommandApplyService(
                        DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES))
                        .restore(restorePlan, "cross-counter-rc", "alice", productId);
            });
            go.countDown();
            assertFalse(h7.get().replayed());
            assertTrue(rc.get().startsWith("APPLIED:"));
        }

        assertEquals(0, dsl.fetchOne("select count(*) from (select revision_number "
                + "from timeline_revision where project_id = ? group by revision_number "
                + "having count(*) > 1) duplicates", productId).get(0, Integer.class));
        assertEquals(1, dsl.fetchOne("select count(*) from project_revision_counter "
                + "where project_id = ?", productId).get(0, Integer.class));
    }

    private static Void runWriter(
            String productId, String baseRevisionId, TimelineDocument candidate,
            String commandId, CountDownLatch ready, CountDownLatch go,
            AtomicInteger applied, AtomicInteger stale) throws Exception {
        TimelineRevisionSaveService service = writer(DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES));
        ready.countDown();
        go.await();
        try {
            service.saveRevisionForCommand(ref(productId), baseRevisionId, candidate, "editor",
                    new TimelineRevisionSaveService.RevisionWriteCommand(
                            commandId, "plan-" + commandId, "fingerprint-" + commandId,
                            "OPERATION_PLAN", TENANT));
            applied.incrementAndGet();
        } catch (TimelineConflictException conflict) {
            stale.incrementAndGet();
        }
        return null;
    }

    private static Void runGenesisWriter(
            String productId, String commandId, CountDownLatch go,
            AtomicInteger applied, AtomicInteger stale) throws Exception {
        TimelineRevisionSaveService service = writer(
                DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES));
        go.await();
        try {
            service.saveRevisionForCommand(ref(productId), null, document(commandId), "editor",
                    command(commandId, "plan-" + commandId, "fingerprint-" + commandId));
            applied.incrementAndGet();
        } catch (TimelineConflictException conflict) {
            stale.incrementAndGet();
        }
        return null;
    }

    private static TimelineRevisionSaveService writer(DSLContext context) {
        var current = new ProductCurrentRevisionService(context);
        var pinValidator = org.mockito.Mockito.mock(
                com.example.platform.timeline.app.TimelineArtifactPinValidator.class);
        org.mockito.Mockito.when(pinValidator.validate(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new com.example.platform.timeline.app.TimelineArtifactPinValidator
                        .ValidationResult(true, List.of()));
        return new TimelineRevisionSaveService(
                context, current, new TimelineContentDigester(), new TimelineSnapshotService(context),
                pinValidator,
                org.mockito.Mockito.mock(com.example.platform.artifact.app.ArtifactPinService.class),
                new EffectSemanticSnapshotAuthority(
                        new JdbcEffectDefinitionVersionRegistry(context),
                        new JdbcEffectSemanticSnapshotStore(context)),
                new JdbcTimelineRevisionSemanticContextStore(context),
                new DefaultTimelineRevisionPersistence(),
                new ProductCurrentRevisionHeadUpdateAdapter(current));
    }

    private static TimelineRevisionSaveService.RevisionWriteCommand command(
            String id, String planDigest, String fingerprint) {
        return new TimelineRevisionSaveService.RevisionWriteCommand(
                id, planDigest, fingerprint, "OPERATION_PLAN", TENANT);
    }

    private static void assertImmutableCommandContextConflict(
            String discriminator, String original, String changed) {
        String productId = "operation-" + discriminator + "-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        TimelineRevisionSaveService service = writer(dsl);
        var root = seedRoot(service, productId, document("base"));
        String commandId = "command-" + discriminator;
        service.saveRevisionForCommand(ref(productId), root.revisionId(), document("candidate"),
                "editor", command(commandId, "plan", discriminator + "=" + original));

        assertThrows(TimelineRevisionCommandConflictException.class, () ->
                service.saveRevisionForCommand(ref(productId), root.revisionId(), document("candidate"),
                        "editor", command(commandId, "plan", discriminator + "=" + changed)));
        assertEquals(2L, revisionRows(productId));
    }

    private static long revisionRows(String productId) {
        return dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId)).fetchOne(0, Long.class);
    }

    private static RevisionRef ref(String productId) {
        return RevisionRef.main(productId);
    }

    private static com.example.platform.timeline.version.TimelineRevision seedRoot(
            TimelineRevisionSaveService service, String productId, TimelineDocument document) {
        var root = service.saveRevision(productId, null, document, "seed");
        dsl.insertInto(com.example.platform.typedschema.jooq.generated.tables.TimelineRevisionRef
                        .TIMELINE_REVISION_REF)
                .set(com.example.platform.typedschema.jooq.generated.tables.TimelineRevisionRef
                        .TIMELINE_REVISION_REF.PROJECT_ID, productId)
                .set(com.example.platform.typedschema.jooq.generated.tables.TimelineRevisionRef
                        .TIMELINE_REVISION_REF.REF_ID, RevisionRef.MAIN_REF)
                .set(com.example.platform.typedschema.jooq.generated.tables.TimelineRevisionRef
                        .TIMELINE_REVISION_REF.HEAD_REVISION_ID, root.revisionId())
                .execute();
        return root;
    }

    private static int commandRows(String commandId) {
        return dsl.fetchOne("select count(*) from apply_command where apply_command_id = ?", commandId)
                .get(0, Integer.class);
    }

    private static String currentRevision(String productId) {
        return dsl.select(PRODUCT.CURRENT_REVISION_ID).from(PRODUCT)
                .where(PRODUCT.PRODUCT_ID.eq(productId)).fetchOne(PRODUCT.CURRENT_REVISION_ID);
    }

    private static String canonicalHead(String productId) {
        return dsl.fetchOne("select head_revision_id from timeline_revision_ref "
                        + "where project_id = ? and ref_id = ?", productId, RevisionRef.MAIN_REF)
                .get(0, String.class);
    }

    private static long counterValue(String productId) {
        return dsl.fetchOne("select next_revision_number from project_revision_counter "
                        + "where project_id = ?", productId)
                .get(0, Long.class);
    }

    private static TimelineDocument document(String title) {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("video-1", "Main", TrackType.VIDEO, List.of())),
                new TimelineMetadata(title, "", Map.of()));
    }

    private static TimelineDocument editableDocument() {
        TimelineClip clip = new TimelineClip(
                "clip-1", "asset", "stream", "artifact",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                MediaTime.ZERO, MediaTime.ofRational(10, 1), MediaTime.ZERO,
                MediaTime.ofRational(10, 1), "MEDIA_STREAM");
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("video-1", "Main", TrackType.VIDEO, List.of(clip))),
                new TimelineMetadata("editable", "", Map.of()));
    }

    private static void insertProduct(String productId) {
        if (productId.length() > 64) {
            throw new IllegalArgumentException("productId exceeds varchar(64): " + productId.length());
        }
        dsl.insertInto(PRODUCT)
                .set(PRODUCT.PRODUCT_ID, productId)
                .set(PRODUCT.PRODUCT_TYPE, "video")
                .set(PRODUCT.REPRESENTATION_KIND, "master")
                .set(PRODUCT.STATUS, "REGISTERED")
                .set(PRODUCT.TENANT_ID, TENANT)
                .set(PRODUCT.CREATED_AT, java.time.LocalDateTime.now())
                .set(PRODUCT.UPDATED_AT, java.time.LocalDateTime.now())
                .execute();
    }
}
