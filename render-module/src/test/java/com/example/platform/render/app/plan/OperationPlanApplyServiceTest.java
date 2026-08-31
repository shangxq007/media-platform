package com.example.platform.render.app.plan;

import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationDefinitionVersion;
import com.example.platform.operation.operation.OperationInstance;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationTarget;
import com.example.platform.operation.plan.ApplyContext;
import com.example.platform.operation.plan.ApplyResult;
import com.example.platform.operation.plan.AuthorizationDecision;
import com.example.platform.operation.plan.OperationPlanner;
import com.example.platform.operation.plan.PlanErrorCode;
import com.example.platform.operation.plan.PlanException;
import com.example.platform.operation.plan.TargetRevisionRef;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.app.TimelineMutationContext;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.selection.ResolvedScope;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OperationPlanApplyServiceTest {

    @Test
    void springAutodetectsOnlyConstructorWithoutAutowiredSelector() {
        var constructors = OperationPlanApplyService.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(0, List.of(constructors).stream()
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .count());

        TimelineRevisionSaveService writer = mock(TimelineRevisionSaveService.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TimelineRevisionSaveService.class, () -> writer);
            context.register(OperationPlanApplyService.class);
            context.refresh();

            assertSame(writer, context.getBean(TimelineRevisionSaveService.class));
            OperationPlanApplyService bean = context.getBean(OperationPlanApplyService.class);
            assertNotNull(bean);
            assertSame(bean, context.getBean(OperationPlanApplyService.class));
        }
    }

    @Test
    void noOpUsesDurableCanonicalCommandWithoutCreatingRevision() {
        TimelineDocument base = base();
        String hash = new TimelineContentDigester().digest(base);
        var instance = new OperationInstance(OperationDefinition.V1.MOVE.definitionId(),
                OperationDefinitionVersion.of(1, 0), "R0", hash,
                new OperationTarget.ResolvedClipScopeTarget(new ResolvedScope(
                        "R0", hash, List.of(TimelineClipId.of("clip-1")),
                        SelectionSpec.ExpansionPolicy.EXACT)),
                new OperationParameters.MoveParameters(MediaTime.ZERO, false), "parameters", null);
        var plan = new OperationPlanner().plan(instance, "R0", base);
        assertTrue(plan.noOp());

        TimelineRevisionSaveService writer = mock(TimelineRevisionSaveService.class);
        when(writer.recordNoOpCommand(
                any(TimelineMutationContext.class),
                eq(RevisionRef.main("tenant-a", "project")), eq("R0"), eq(hash), any()))
                .thenReturn(new TimelineRevisionSaveService.RevisionWriteResult(null, "R0", hash, false));
        var authorization = AuthorizationDecision.allow(
                plan.planDigest(), "alice", "project", "tenant-a",
                OperationPlanApplyService.CURRENT_REVISION_REF, "policy-v1");
        CanonicalActor actor = CanonicalActor.user(
                "alice", "tenant-a", java.util.Set.of(), "test");
        var context = new ApplyContext("command-noop",
                new TargetRevisionRef(OperationPlanApplyService.CURRENT_REVISION_REF),
                "R0", "tenant-a", actor,
                authorization);

        ApplyResult result = new OperationPlanApplyService(writer)
                .apply(plan, context, "project", base);
        assertEquals(ApplyResult.NO_OP, result.status());
        ArgumentCaptor<TimelineMutationContext> mutationContext =
                ArgumentCaptor.forClass(TimelineMutationContext.class);
        verify(writer).recordNoOpCommand(
                mutationContext.capture(),
                eq(RevisionRef.main("tenant-a", "project")), eq("R0"), eq(hash),
                argThat(command -> command.commandId().equals("command-noop")
                        && command.tenantId().equals("tenant-a")));
        assertEquals("tenant-a", mutationContext.getValue().tenantId());
        assertEquals("project", mutationContext.getValue().projectId());
        assertEquals(actor, mutationContext.getValue().actor());
        verify(writer, never()).saveRevisionForCommand(
                any(TimelineMutationContext.class), any(), any(), any(), any());
    }

    @Test
    void authorizationContextMismatchFailsBeforeWriterInvocation() {
        TimelineDocument base = base();
        String hash = new TimelineContentDigester().digest(base);
        var instance = new OperationInstance(OperationDefinition.V1.MOVE.definitionId(),
                OperationDefinitionVersion.of(1, 0), "R0", hash,
                new OperationTarget.ResolvedClipScopeTarget(new ResolvedScope(
                        "R0", hash, List.of(TimelineClipId.of("clip-1")),
                        SelectionSpec.ExpansionPolicy.EXACT)),
                new OperationParameters.MoveParameters(MediaTime.ZERO, false), "parameters", null);
        var plan = new OperationPlanner().plan(instance, "R0", base);

        assertThrows(PlanException.class,
                () -> new OperationPlanner().plan(instance, "R-other", base));
        TimelineRevisionSaveService writer = mock(TimelineRevisionSaveService.class);
        var authorization = AuthorizationDecision.allow(
                plan.planDigest(), "bob", "project", "tenant-a",
                OperationPlanApplyService.CURRENT_REVISION_REF, "policy-v1");
        var context = new ApplyContext("command-mismatch",
                new TargetRevisionRef(OperationPlanApplyService.CURRENT_REVISION_REF),
                "R0", "tenant-a",
                com.example.platform.shared.authorization.CanonicalActor.user(
                        "alice", "tenant-a", java.util.Set.of(), "test"),
                authorization);

        PlanException failure = assertThrows(PlanException.class,
                () -> new OperationPlanApplyService(writer)
                        .apply(plan, context, "project", base));
        assertEquals(PlanErrorCode.AUTHORIZATION_CONTEXT_MISMATCH, failure.code());
        verifyNoInteractions(writer);
    }

    @Test
    void commandFingerprintBindsTenantPrincipalAndTargetRef() {
        String alice = OperationPlanApplyService.fingerprint(
                "plan", new TargetRevisionRef("current"), "R0", "project", "tenant-a", "alice",
                "timeline.move", "parameters");
        String bob = OperationPlanApplyService.fingerprint(
                "plan", new TargetRevisionRef("current"), "R0", "project", "tenant-a", "bob",
                "timeline.move", "parameters");
        String branch = OperationPlanApplyService.fingerprint(
                "plan", new TargetRevisionRef("branch"), "R0", "project", "tenant-a", "alice",
                "timeline.move", "parameters");
        String otherTenant = OperationPlanApplyService.fingerprint(
                "plan", new TargetRevisionRef("current"), "R0", "project", "tenant-b", "alice",
                "timeline.move", "parameters");
        assertNotEquals(alice, bob);
        assertNotEquals(alice, branch);
        assertNotEquals(alice, otherTenant);
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
