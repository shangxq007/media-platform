package com.example.platform.render.app.operation;

import com.example.platform.operation.plan.AuthorizationDecision;
import com.example.platform.operation.plan.PlanErrorCode;
import com.example.platform.operation.plan.PlanException;
import com.example.platform.render.app.plan.OperationPlanApplyService;
import com.example.platform.render.domain.renderplan.VerifiedTimelineRevisionFactory;
import com.example.platform.shared.authorization.AuthorizationDecisionPort;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.app.InternalTimelineValidationService;
import com.example.platform.timeline.app.TimelineMutationContext;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.app.TimelineSourceReferenceValidator;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.semantics.effect.EffectSemanticContractVersion;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import com.example.platform.timeline.semantics.effect.TimelineRevisionEffectSemanticCommitment;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.timeline.version.TimelineRevisionSemanticContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Full H7 application path with the real operation/validation/apply coordinators. */
class H7FirstRealMediaCutTest {

    private static final String PROJECT = "timeline-T";
    private static final String TENANT = "tenant-A";
    private static final String BASE_REVISION = "revision-R0";
    private static final String DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final TimelineContentDigester DIGESTER = new TimelineContentDigester();

    @Test
    void previewAuthorizeAtomicApplyNewRevisionAndRenderHandoff() {
        TimelineDocument base = baseTimeline();
        String baseHash = DIGESTER.digest(base);
        TimelineRevision baseRevision = revision(BASE_REVISION, null, base, "base-author");
        AtomicReference<TimelineDocument> savedDocument = new AtomicReference<>();
        CanonicalActor applyingActor = CanonicalActor.user(
                "editor-1", TENANT, Set.of("EDITOR"), "test");

        TimelineRevisionSaveService writer = mock(TimelineRevisionSaveService.class);
        when(writer.findById(TENANT, BASE_REVISION)).thenReturn(baseRevision);
        when(writer.findPayloadDocument(TENANT, BASE_REVISION)).thenReturn(Optional.of(base));
        when(writer.saveRevisionForCommand(
                any(TimelineMutationContext.class),
                eq(RevisionRef.main(TENANT, PROJECT)), eq(BASE_REVISION),
                any(TimelineDocument.class),
                any(TimelineRevisionSaveService.RevisionWriteCommand.class)))
                .thenAnswer(invocation -> {
                    TimelineDocument document = invocation.getArgument(3, TimelineDocument.class);
                    savedDocument.set(document);
                    return new TimelineRevisionSaveService.RevisionWriteResult(
                            "revision-R1", BASE_REVISION, DIGESTER.digest(document), false);
                });

        TimelineSourceReferenceValidator sourceValidator = mock(TimelineSourceReferenceValidator.class);
        when(sourceValidator.validate(any(MediaStreamSourceBinding.class), eq(TENANT), eq(PROJECT), eq(TrackType.VIDEO)))
                .thenReturn(new TimelineSourceReferenceValidator.ValidationResult(true, List.of()));
        List<String> authorizationActions = new ArrayList<>();
        AuthorizationDecisionPort authorization = request -> {
            authorizationActions.add(request.action().permissionKey());
            assertEquals(PROJECT, request.resource().projectId());
            assertEquals(TENANT, request.resource().tenantId());
            String planDigest = request.context().additionalReadOnlySignals()
                    .get("operationPlanDigest");
            if ("WRITE".equals(request.action().permissionKey())) {
                assertNotNull(planDigest);
                assertFalse(planDigest.isBlank());
            }
            return com.example.platform.shared.authorization.AuthorizationDecision.allow("rbac-v1");
        };
        var apply = spy(new OperationPlanApplyService(writer));
        var service = new TimelineMediaClipOperationService(
                writer, sourceValidator, new InternalTimelineValidationService(), authorization, apply);
        AddMediaClipCommand request = request(baseHash);

        var preview = service.preview(TENANT, PROJECT, request, actor("editor-1", TENANT));
        assertEquals(TimelineMediaClipOperationService.OPERATION, preview.operation());
        assertEquals(MediaTime.ofRational(10, 1), preview.sourceRange().start());
        assertEquals(MediaTime.ofRational(20, 1), preview.sourceRange().end());
        assertEquals(MediaTime.ZERO, preview.placement().start());
        assertEquals(MediaTime.ofRational(10, 1), preview.placement().end());
        assertEquals(ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD),
                preview.temporalMapping());
        assertEquals(List.of("CANONICAL_TIMELINE_VALID"), preview.validation());
        assertTrue(preview.failures().isEmpty());

        var applied = service.authorizeAndApply(
                TENANT, PROJECT, request, preview.planDigest(), "apply-H7-1",
                applyingActor);
        assertEquals(List.of("READ", "READ", "WRITE"), authorizationActions,
                "preview and apply preparation authorize before disclosure, then exact-plan write authorizes");
        assertEquals("APPLIED", applied.status());
        assertEquals(BASE_REVISION, applied.parentRevisionId());
        assertEquals("revision-R1", applied.renderHandoff().timelineRevisionId());
        assertEquals(preview.candidateContentHash(),
                applied.renderHandoff().timelineContentHash());
        ArgumentCaptor<TimelineMutationContext> mutationContext =
                ArgumentCaptor.forClass(TimelineMutationContext.class);
        verify(writer, times(1)).saveRevisionForCommand(
                mutationContext.capture(),
                eq(RevisionRef.main(TENANT, PROJECT)), eq(BASE_REVISION),
                argThat(document -> DIGESTER.digest(document)
                        .equals(preview.candidateContentHash())),
                argThat(command -> command.commandId().equals("apply-H7-1")
                        && command.commandDomain().equals("OPERATION_PLAN")
                        && command.tenantId().equals(TENANT)));
        verify(apply, times(1)).apply(
                any(), any(), eq(PROJECT), eq(base));
        assertEquals(TENANT, mutationContext.getValue().tenantId());
        assertEquals(PROJECT, mutationContext.getValue().projectId());
        assertEquals(applyingActor, mutationContext.getValue().actor());
        verify(sourceValidator, times(2)).validate(
                argThat(binding -> binding.mediaAssetId().value().equals("media-S")
                        && binding.mediaStreamId().value().equals("stream-S-video")
                        && binding.artifactId().value().equals("artifact-S-v1")
                        && binding.contentDigest().canonicalValue().equals(DIGEST)
                        && binding.sourceRange().start().equals(MediaTime.ofRational(10, 1))
                        && binding.sourceRange().end().equals(MediaTime.ofRational(20, 1))),
                eq(TENANT), eq(PROJECT), eq(TrackType.VIDEO));

        TimelineRevision resultingRevision = revision(
                applied.newRevisionId(), BASE_REVISION,
                savedDocument.get(), "editor-1");
        var verifiedRenderInput = VerifiedTimelineRevisionFactory.verified(resultingRevision, DIGESTER);
        assertEquals("revision-R1", verifiedRenderInput.revision().revisionId());
        assertEquals(1, verifiedRenderInput.clips().size());
        assertEquals("clip-S-10-20", verifiedRenderInput.clips().getFirst().clipId());
        assertEquals(MediaTime.ofRational(10, 1),
                verifiedRenderInput.clips().getFirst().sourceRange().start());
    }

    @Test
    void staleBaseAndWrongPlanDigestFailBeforeApplyOrCanonicalMutation() {
        TimelineDocument base = baseTimeline();
        TimelineRevisionSaveService writer = mock(TimelineRevisionSaveService.class);
        when(writer.findById(TENANT, BASE_REVISION))
                .thenReturn(revision(BASE_REVISION, null, base, "base"));
        when(writer.findPayloadDocument(TENANT, BASE_REVISION)).thenReturn(Optional.of(base));
        TimelineSourceReferenceValidator sources = mock(TimelineSourceReferenceValidator.class);
        when(sources.validate(any(), anyString(), anyString(), any()))
                .thenReturn(new TimelineSourceReferenceValidator.ValidationResult(true, List.of()));
        OperationPlanApplyService apply = mock(OperationPlanApplyService.class);
        var service = new TimelineMediaClipOperationService(
                writer, sources, new InternalTimelineValidationService(),
                request -> com.example.platform.shared.authorization.AuthorizationDecision.allow("rbac"),
                apply);

        AddMediaClipCommand stale = request("not-the-base-hash");
        TimelineOperationException staleFailure = assertThrows(TimelineOperationException.class,
                () -> service.preview(TENANT, PROJECT, stale, actor("editor", TENANT)));
        assertEquals(TimelineOperationException.Code.STALE_BASE_REVISION, staleFailure.code());

        AddMediaClipCommand exact = request(DIGESTER.digest(base));
        var preview = service.preview(TENANT, PROJECT, exact, actor("editor", TENANT));
        TimelineOperationException changed = assertThrows(TimelineOperationException.class,
                () -> service.authorizeAndApply(TENANT, PROJECT, exact, "changed-plan",
                        "apply-H7-2", CanonicalActor.user("editor", TENANT, Set.of(), "test")));
        assertEquals(TimelineOperationException.Code.PLAN_CHANGED, changed.code());
        verify(apply, never()).apply(any(), any(), eq(PROJECT), eq(base));
        verify(writer, never()).saveRevisionForCommand(
                any(TimelineMutationContext.class),
                any(RevisionRef.class), any(), any(), any());
        assertNotNull(preview.planDigest());
    }

    @Test
    void nullPlanDigestFailsBeforeApplyOrCanonicalMutation() {
        TimelineDocument base = baseTimeline();
        TimelineRevisionSaveService writer = mock(TimelineRevisionSaveService.class);
        when(writer.findById(TENANT, BASE_REVISION))
                .thenReturn(revision(BASE_REVISION, null, base, "base"));
        when(writer.findPayloadDocument(TENANT, BASE_REVISION)).thenReturn(Optional.of(base));
        TimelineSourceReferenceValidator sources = mock(TimelineSourceReferenceValidator.class);
        when(sources.validate(any(), anyString(), anyString(), any()))
                .thenReturn(new TimelineSourceReferenceValidator.ValidationResult(true, List.of()));
        OperationPlanApplyService apply = mock(OperationPlanApplyService.class);
        var service = new TimelineMediaClipOperationService(
                writer, sources, new InternalTimelineValidationService(),
                request -> com.example.platform.shared.authorization.AuthorizationDecision.allow("rbac"),
                apply);

        AddMediaClipCommand exact = request(DIGESTER.digest(base));
        TimelineOperationException failure = assertThrows(TimelineOperationException.class,
                () -> service.authorizeAndApply(TENANT, PROJECT, exact, null,
                        "apply-H7-null-digest", CanonicalActor.user(
                                "editor", TENANT, Set.of(), "test")));

        assertEquals(TimelineOperationException.Code.PLAN_CHANGED, failure.code());
        verify(apply, never()).apply(any(), any(), eq(PROJECT), eq(base));
        verify(writer, never()).saveRevisionForCommand(
                any(TimelineMutationContext.class),
                any(RevisionRef.class), any(), any(), any());
    }

    @Test
    void operationPlanFailureIsTranslatedAtRenderApplicationBoundary() {
        TimelineDocument base = baseTimeline();
        TimelineRevisionSaveService writer = mock(TimelineRevisionSaveService.class);
        when(writer.findById(TENANT, BASE_REVISION))
                .thenReturn(revision(BASE_REVISION, null, base, "base"));
        when(writer.findPayloadDocument(TENANT, BASE_REVISION)).thenReturn(Optional.of(base));
        TimelineSourceReferenceValidator sources = mock(TimelineSourceReferenceValidator.class);
        when(sources.validate(any(), anyString(), anyString(), any()))
                .thenReturn(new TimelineSourceReferenceValidator.ValidationResult(true, List.of()));
        OperationPlanApplyService apply = mock(OperationPlanApplyService.class);
        when(apply.apply(any(), any(), eq(PROJECT), eq(base)))
                .thenThrow(new PlanException(PlanErrorCode.STALE_TARGET_REF, "current ref changed"));
        var service = new TimelineMediaClipOperationService(
                writer, sources, new InternalTimelineValidationService(),
                request -> com.example.platform.shared.authorization.AuthorizationDecision.allow("rbac"),
                apply);

        AddMediaClipCommand command = request(DIGESTER.digest(base));
        var preview = service.preview(TENANT, PROJECT, command, actor("editor", TENANT));
        TimelineOperationException failure = assertThrows(TimelineOperationException.class,
                () -> service.authorizeAndApply(TENANT, PROJECT, command, preview.planDigest(),
                        "apply-H7-boundary", CanonicalActor.user(
                                "editor", TENANT, Set.of(), "test")));

        assertEquals(TimelineOperationException.Code.STALE_TARGET_REF, failure.code());
        assertEquals(List.of("current ref changed"), failure.failures());
        verify(writer, never()).saveRevisionForCommand(
                any(TimelineMutationContext.class),
                any(RevisionRef.class), any(), any(), any());
    }

    @Test
    void commandRejectsBlankTransportIdentityBeforeCanonicalResolution() {
        assertThrows(IllegalArgumentException.class, () -> new AddMediaClipCommand(
                BASE_REVISION, "base-hash", "video-1", "clip-S-10-20",
                " ", "stream-S-video", "artifact-S-v1", DIGEST,
                "10/1", "20/1", "0", "10/1", 1, 1,
                AddMediaClipCommand.Direction.FORWARD));
    }

    @Test
    void unauthorizedPreviewFailsBeforeBaseOrSourceDisclosure() {
        TimelineRevisionSaveService writer = mock(TimelineRevisionSaveService.class);
        TimelineSourceReferenceValidator sources = mock(TimelineSourceReferenceValidator.class);
        var service = new TimelineMediaClipOperationService(
                writer, sources, new InternalTimelineValidationService(),
                request -> com.example.platform.shared.authorization.AuthorizationDecision.deny(
                        "RBAC_DENY", "RBAC", "hidden"),
                new OperationPlanApplyService(writer));

        TimelineOperationException failure = assertThrows(TimelineOperationException.class,
                () -> service.preview(TENANT, PROJECT, request("unknown"), actor("viewer", TENANT)));

        assertEquals(TimelineOperationException.Code.AUTHORIZATION_DENIED, failure.code());
        assertEquals(List.of("project operation access denied"), failure.failures());
        verifyNoInteractions(writer, sources);
    }

    @Test
    void explicitAndAuthenticatedTenantMismatchFailsBeforeHydration() {
        TimelineRevisionSaveService writer = mock(TimelineRevisionSaveService.class);
        TimelineSourceReferenceValidator sources = mock(TimelineSourceReferenceValidator.class);
        var service = new TimelineMediaClipOperationService(
                writer, sources, new InternalTimelineValidationService(),
                request -> com.example.platform.shared.authorization.AuthorizationDecision.allow("rbac"),
                new OperationPlanApplyService(writer));

        TimelineOperationException failure = assertThrows(TimelineOperationException.class,
                () -> service.preview(TENANT, PROJECT, request("unknown"),
                        actor("viewer", "tenant-B")));

        assertEquals(TimelineOperationException.Code.TENANT_CONTEXT_MISMATCH, failure.code());
        verifyNoInteractions(writer, sources);
    }

    private static CanonicalActor actor(String actorId, String tenantId) {
        return CanonicalActor.user(actorId, tenantId, Set.of("EDITOR"), "test");
    }

    private static TimelineDocument baseTimeline() {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("video-1", "main", TrackType.VIDEO, List.of())),
                TimelineMetadata.empty());
    }

    private static AddMediaClipCommand request(String baseHash) {
        return new AddMediaClipCommand(
                BASE_REVISION, baseHash, "video-1", "clip-S-10-20",
                "media-S", "stream-S-video", "artifact-S-v1", DIGEST,
                "10/1", "20/1", "0", "10/1", 1, 1, null);
    }

    private static TimelineRevision revision(
            String revisionId, String parent, TimelineDocument document, String actor) {
        String timelineDigest = DIGESTER.digest(document);
        var effectRef = new EffectSemanticSnapshotReference(
                EffectSemanticSnapshotId.of("effect-" + revisionId), "empty-effect-digest",
                EffectSemanticContractVersion.current());
        String fullDigest = TimelineRevisionEffectSemanticCommitment
                .revisionEffectSemanticDigest(timelineDigest, effectRef);
        var context = new TimelineRevisionSemanticContext(
                timelineDigest, effectRef, fullDigest,
                TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1);
        return new TimelineRevision(revisionId, PROJECT, parent,
                TimelineDocument.CURRENT_SCHEMA_VERSION, document, fullDigest,
                Instant.EPOCH, actor, context);
    }
}
