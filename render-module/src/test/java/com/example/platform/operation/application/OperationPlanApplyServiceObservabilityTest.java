package com.example.platform.operation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationDefinitionVersion;
import com.example.platform.operation.operation.OperationInstance;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationTarget;
import com.example.platform.operation.plan.ApplyContext;
import com.example.platform.operation.plan.ApplyResult;
import com.example.platform.operation.plan.AuthorizationDecision;
import com.example.platform.operation.plan.OperationPlan;
import com.example.platform.operation.plan.OperationPlanner;
import com.example.platform.operation.plan.PlanErrorCode;
import com.example.platform.operation.plan.PlanException;
import com.example.platform.operation.plan.TargetRevisionRef;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.api.revision.TimelineMutationContext;
import com.example.platform.timeline.api.revision.TimelineRevisionCommands;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import com.example.platform.timeline.semantics.selection.ResolvedScope;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * OPERATION-OBSERVABILITY-BASELINE: pure unit tests asserting the baseline log
 * points on {@link OperationPlanApplyService} (no Spring context; logback
 * {@link ListAppender} captures the real SLF4J output).
 */
class OperationPlanApplyServiceObservabilityTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(OperationPlanApplyService.class);
        logger.setLevel(Level.TRACE);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void applyStartLogEmitted() {
        TimelineDocument base = base();
        OperationPlan plan = noOpPlan(base);
        TimelineRevisionSaveService writer = writerReturningNoOp(base);

        new OperationPlanApplyService(writer)
                .apply(plan, context(plan, null), "project", base);

        assertTrue(messages().stream().anyMatch(m ->
                        m.contains("operation.apply.start") && m.contains(plan.planDigest())),
                "expected operation.apply.start with planDigest: " + messages());
    }

    @Test
    void authorizationDeniedLogEmitted() {
        TimelineDocument base = base();
        OperationPlan plan = noOpPlan(base);
        TimelineRevisionSaveService writer = writerReturningNoOp(base);
        CanonicalActor actor = CanonicalActor.user("alice", "tenant-a", Set.of(), "test");
        AuthorizationDecision denied = AuthorizationDecision.deny(
                plan.planDigest(), "alice", "project", "tenant-a",
                OperationPlanApplyService.CURRENT_REVISION_REF, "policy-v1");
        ApplyContext context = new ApplyContext("command-denied",
                new TargetRevisionRef(OperationPlanApplyService.CURRENT_REVISION_REF),
                plan.baseRevisionId(), "tenant-a", actor, denied);

        PlanException failure = assertThrows(PlanException.class,
                () -> new OperationPlanApplyService(writer).apply(plan, context, "project", base));

        assertEquals(PlanErrorCode.AUTHORIZATION_DENIED, failure.code());
        assertTrue(messages().stream().anyMatch(m ->
                        m.contains("operation.authorization decision=denied")),
                "expected denied authorization log: " + messages());
    }

    @Test
    void applyFailLogEmitted() {
        TimelineDocument base = base();
        OperationPlan plan = noOpPlan(base);
        TimelineRevisionSaveService writer = writerReturningNoOp(base);
        CanonicalActor actor = CanonicalActor.user("alice", "tenant-a", Set.of(), "test");
        AuthorizationDecision allowed = AuthorizationDecision.allow(
                plan.planDigest(), "alice", "project", "tenant-a",
                OperationPlanApplyService.CURRENT_REVISION_REF, "policy-v1");
        // expectedHead != plan.baseRevisionId() -> STALE_BASE_REVISION branch.
        ApplyContext staleContext = new ApplyContext("command-stale",
                new TargetRevisionRef(OperationPlanApplyService.CURRENT_REVISION_REF),
                "R-other", "tenant-a", actor, allowed);

        PlanException failure = assertThrows(PlanException.class,
                () -> new OperationPlanApplyService(writer)
                        .apply(plan, staleContext, "project", base));

        assertEquals(PlanErrorCode.STALE_BASE_REVISION, failure.code());
        assertTrue(messages().stream().anyMatch(m ->
                        m.contains("operation.apply.fail")
                                && m.contains("failureCode=STALE_BASE_REVISION")),
                "expected operation.apply.fail STALE_BASE_REVISION: " + messages());
    }

    @Test
    void successDoesNotEmitFailLog() {
        TimelineDocument base = base();
        OperationPlan plan = noOpPlan(base);
        TimelineRevisionSaveService writer = writerReturningNoOp(base);

        new OperationPlanApplyService(writer)
                .apply(plan, context(plan, null), "project", base);

        assertTrue(messages().stream().noneMatch(m -> m.contains("operation.apply.fail")),
                "success must not emit operation.apply.fail: " + messages());
    }

    // ---- fixtures ----

    private ApplyContext context(OperationPlan plan, String expectedHead) {
        CanonicalActor actor = CanonicalActor.user("alice", "tenant-a", Set.of(), "test");
        AuthorizationDecision allowed = AuthorizationDecision.allow(
                plan.planDigest(), "alice", "project", "tenant-a",
                OperationPlanApplyService.CURRENT_REVISION_REF, "policy-v1");
        return new ApplyContext("command-1",
                new TargetRevisionRef(OperationPlanApplyService.CURRENT_REVISION_REF),
                expectedHead != null ? expectedHead : plan.baseRevisionId(),
                "tenant-a", actor, allowed);
    }

    private TimelineRevisionSaveService writerReturningNoOp(TimelineDocument base) {
        TimelineRevisionSaveService writer = mock(TimelineRevisionSaveService.class);
        when(writer.recordNoOpCommand(
                any(TimelineMutationContext.class),
                eq(RevisionRef.main("tenant-a", "project")),
                eq("R0"), eq(new TimelineContentDigester().digest(base)), any()))
                .thenReturn(new TimelineRevisionCommands.RevisionWriteResult(
                        null, "R0", new TimelineContentDigester().digest(base), false));
        return writer;
    }

    private static OperationPlan noOpPlan(TimelineDocument base) {
        String hash = new TimelineContentDigester().digest(base);
        var instance = new OperationInstance(OperationDefinition.V1.MOVE.definitionId(),
                OperationDefinitionVersion.of(1, 0), "R0", hash,
                new OperationTarget.ResolvedClipScopeTarget(new ResolvedScope(
                        "R0", hash, List.of(TimelineClipId.of("clip-1")),
                        SelectionSpec.ExpansionPolicy.EXACT)),
                new OperationParameters.MoveParameters(MediaTime.ZERO, false), "parameters", null);
        return new OperationPlanner().plan(instance, "R0", base);
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static TimelineDocument base() {
        TimelineClip clip = new TimelineClip(
                "clip-1", "asset", "stream", "artifact", "digest",
                MediaTime.ZERO, MediaTime.ofRational(10, 1),
                MediaTime.ZERO, MediaTime.ofRational(10, 1), "MEDIA_STREAM");
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("video-1", "Main", TrackType.VIDEO, List.of(clip))),
                TimelineMetadata.empty());
    }
}
