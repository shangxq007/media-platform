package com.example.platform.render.app.operation;

import com.example.platform.operation.invocation.OperationInvocationContext;
import com.example.platform.operation.invocation.OperationInvocationException;
import com.example.platform.operation.invocation.OperationInvocationFailureCode;
import com.example.platform.operation.invocation.OperationInvocationPort;
import com.example.platform.operation.invocation.OperationInvocationResult;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationDefinitionId;
import com.example.platform.operation.operation.OperationDefinitionVersion;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.operation.plan.ApplyResult;
import com.example.platform.render.app.plan.OperationPlanApplyService;
import com.example.platform.render.testsupport.TestSourceBindings;
import com.example.platform.shared.authorization.AuthorizationDecisionPort;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.app.InternalTimelineValidationService;
import com.example.platform.timeline.app.TimelineMutationContext;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.app.TimelineSourceReferenceValidator;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import com.example.platform.timeline.semantics.clip.MediaClip;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CanonicalOperationInvocationServiceTest {

    private static final String TENANT = "tenant-A";
    private static final String PROJECT = "timeline-T";
    private static final String BASE_REVISION = "revision-R0";
    private static final TimelineContentDigester DIGESTER = new TimelineContentDigester();

    @Test
    void unsupportedDefinitionFailsBeforeAnyOperationMechanics() {
        TimelineMediaClipOperationService mediaClipService =
                mock(TimelineMediaClipOperationService.class);
        var service = new CanonicalOperationInvocationService(mediaClipService);
        OperationRequest request = new OperationRequest(
                OperationDefinition.V1.DELETE.definitionId(),
                OperationDefinition.V1.DELETE.version(),
                new OperationTargetRequest.TimelineTargetRequest("timeline-T"),
                new OperationParameters.NoParameters(),
                "revision-R0", "base-hash", null);

        OperationInvocationException failure = assertThrows(
                OperationInvocationException.class,
                () -> service.invoke(request, context("tenant-A")));

        assertEquals(OperationInvocationFailureCode.UNSUPPORTED_OPERATION, failure.code());
        assertEquals("unsupported-operation", failure.diagnostics().get("failure"));
        verifyNoInteractions(mediaClipService);
    }

    @Test
    void unknownDefinitionAndVersionFailBeforeAnyOperationMechanics() {
        TimelineMediaClipOperationService mediaClipService =
                mock(TimelineMediaClipOperationService.class);
        var service = new CanonicalOperationInvocationService(mediaClipService);
        OperationRequest request = new OperationRequest(
                OperationDefinitionId.of("timeline.unknown"),
                OperationDefinitionVersion.of(99, 7),
                new OperationTargetRequest.TimelineTargetRequest(PROJECT),
                new OperationParameters.NoParameters(),
                BASE_REVISION, "base-hash", null);

        OperationInvocationException failure = assertThrows(
                OperationInvocationException.class,
                () -> service.invoke(request, context(TENANT)));

        assertEquals(OperationInvocationFailureCode.UNSUPPORTED_OPERATION, failure.code());
        verifyNoInteractions(mediaClipService);
    }

    @Test
    void supportedDefinitionWithInvalidTargetFailsBeforeAnyOperationMechanics() {
        TimelineMediaClipOperationService mediaClipService =
                mock(TimelineMediaClipOperationService.class);
        var service = new CanonicalOperationInvocationService(mediaClipService);
        OperationRequest request = new OperationRequest(
                OperationDefinition.V1.ADD_MEDIA_CLIP.definitionId(),
                OperationDefinition.V1.ADD_MEDIA_CLIP.version(),
                new OperationTargetRequest.SyncTargetRequest("sync-1"),
                new OperationParameters.NoParameters(),
                "revision-R0", "base-hash", null);

        OperationInvocationException failure = assertThrows(
                OperationInvocationException.class,
                () -> service.invoke(request, context("tenant-A")));

        assertEquals(OperationInvocationFailureCode.INVALID_SCOPE, failure.code());
        verifyNoInteractions(mediaClipService);
    }

    @Test
    void supportedDefinitionWithInvalidParametersFailsBeforeAnyOperationMechanics() {
        TimelineMediaClipOperationService mediaClipService =
                mock(TimelineMediaClipOperationService.class);
        var service = new CanonicalOperationInvocationService(mediaClipService);
        OperationRequest request = new OperationRequest(
                OperationDefinition.V1.ADD_MEDIA_CLIP.definitionId(),
                OperationDefinition.V1.ADD_MEDIA_CLIP.version(),
                new OperationTargetRequest.TimelineTargetRequest("timeline-T"),
                new OperationParameters.NoParameters(),
                "revision-R0", "base-hash", null);

        OperationInvocationException failure = assertThrows(
                OperationInvocationException.class,
                () -> service.invoke(request, context("tenant-A")));

        assertEquals(OperationInvocationFailureCode.INVALID_PARAMETER, failure.code());
        verifyNoInteractions(mediaClipService);
    }

    @Test
    void validRequestRunsCanonicalH7PipelineAndPreservesExactBindings() {
        TimelineDocument base = baseTimeline();
        String baseHash = DIGESTER.digest(base);
        CanonicalActor actor = actor("editor-1", TENANT);
        TimelineRevisionSaveService writer = mock(TimelineRevisionSaveService.class);
        when(writer.findById(TENANT, BASE_REVISION))
                .thenReturn(revision(BASE_REVISION, null, base, "base-author"));
        when(writer.findPayloadDocument(TENANT, BASE_REVISION)).thenReturn(Optional.of(base));
        when(writer.saveRevisionForCommand(
                any(TimelineMutationContext.class),
                eq(RevisionRef.main(TENANT, PROJECT)), eq(BASE_REVISION),
                any(TimelineDocument.class),
                any(TimelineRevisionSaveService.RevisionWriteCommand.class)))
                .thenAnswer(invocation -> {
                    TimelineDocument candidate = invocation.getArgument(3, TimelineDocument.class);
                    return new TimelineRevisionSaveService.RevisionWriteResult(
                            "revision-R1", BASE_REVISION, DIGESTER.digest(candidate), false);
                });
        TimelineSourceReferenceValidator sources = mock(TimelineSourceReferenceValidator.class);
        when(sources.validate(any(MediaStreamSourceBinding.class), eq(TENANT), eq(PROJECT),
                eq(TrackType.VIDEO)))
                .thenReturn(new TimelineSourceReferenceValidator.ValidationResult(true, List.of()));
        List<com.example.platform.shared.authorization.AuthorizationRequest> decisions =
                new ArrayList<>();
        AuthorizationDecisionPort authorization = authorizationRequest -> {
            decisions.add(authorizationRequest);
            assertEquals(actor, authorizationRequest.actor());
            assertEquals(TENANT, authorizationRequest.resource().tenantId());
            assertEquals(PROJECT, authorizationRequest.resource().projectId());
            if ("READ".equals(authorizationRequest.action().permissionKey())) {
                verify(writer, never()).findById(any(), any());
            }
            return com.example.platform.shared.authorization.AuthorizationDecision.allow("rbac-v1");
        };
        var h7Service = new TimelineMediaClipOperationService(
                writer, sources, new InternalTimelineValidationService(), authorization,
                new OperationPlanApplyService(writer));
        OperationInvocationPort port = new CanonicalOperationInvocationService(h7Service);
        OperationRequest request = validRequest(baseHash);
        OperationInvocationContext context = new OperationInvocationContext(
                actor, "invocation-apply-1",
                new OperationInvocationContext.Provenance("correlation-1", "workflow"));

        OperationInvocationResult.Applied result = assertInstanceOf(
                OperationInvocationResult.Applied.class, port.invoke(request, context));

        assertEquals(request.definitionId(), result.definitionId());
        assertEquals(request.version(), result.definitionVersion());
        assertEquals(BASE_REVISION, result.baseRevisionId());
        assertEquals("revision-R1", result.newRevisionId());
        assertEquals("invocation-apply-1", result.invocationId());
        assertEquals("correlation-1", result.correlationId());
        assertNotEquals(baseHash, result.resultContentHash());
        assertEquals(List.of("READ", "WRITE"), decisions.stream()
                .map(decision -> decision.action().permissionKey()).toList());
        assertEquals(result.planDigest(), decisions.get(1).context()
                .additionalReadOnlySignals().get("operationPlanDigest"));
        ArgumentCaptor<TimelineMutationContext> mutation =
                ArgumentCaptor.forClass(TimelineMutationContext.class);
        ArgumentCaptor<TimelineRevisionSaveService.RevisionWriteCommand> command =
                ArgumentCaptor.forClass(TimelineRevisionSaveService.RevisionWriteCommand.class);
        verify(writer).saveRevisionForCommand(
                mutation.capture(), eq(RevisionRef.main(TENANT, PROJECT)), eq(BASE_REVISION),
                any(TimelineDocument.class), command.capture());
        assertEquals(actor, mutation.getValue().actor());
        assertEquals("invocation-apply-1", command.getValue().commandId());
        assertEquals(result.planDigest(), command.getValue().planDigest());
        verify(writer).findById(TENANT, BASE_REVISION);
        verify(writer).findPayloadDocument(TENANT, BASE_REVISION);
        verify(sources).validate(any(MediaStreamSourceBinding.class), eq(TENANT), eq(PROJECT),
                eq(TrackType.VIDEO));
    }

    @Test
    void noOpApplyOutcomeProjectsWithoutInventingAnotherExecutableOperation() {
        TimelineMediaClipOperationService mediaClipService =
                mock(TimelineMediaClipOperationService.class);
        OperationRequest request = validRequest("base-hash");
        OperationInvocationContext context = context(TENANT);
        ApplyResult noOp = ApplyResult.noOp(
                "plan-digest", context.invocationId(), BASE_REVISION,
                "unchanged-hash", OperationPlanApplyService.CURRENT_REVISION_REF);
        when(mediaClipService.invoke(request, context))
                .thenReturn(new TimelineMediaClipOperationService.InvocationOutcome(noOp));
        OperationInvocationPort port = new CanonicalOperationInvocationService(mediaClipService);

        OperationInvocationResult.NoOp result = assertInstanceOf(
                OperationInvocationResult.NoOp.class, port.invoke(request, context));

        assertEquals(request.definitionId(), result.definitionId());
        assertEquals(request.version(), result.definitionVersion());
        assertEquals("plan-digest", result.planDigest());
        assertEquals(BASE_REVISION, result.baseRevisionId());
        assertEquals("unchanged-hash", result.unchangedContentHash());
        assertEquals(context.invocationId(), result.invocationId());
        assertEquals(context.provenance().correlationId(), result.correlationId());
    }

    @Test
    void provenanceChangesOnlyEchoedCorrelationAndNeverCanonicalInputs() {
        TimelineMediaClipOperationService mediaClipService =
                mock(TimelineMediaClipOperationService.class);
        OperationRequest request = validRequest("base-hash");
        CanonicalActor actor = actor("editor-1", TENANT);
        OperationInvocationContext first = new OperationInvocationContext(
                actor, "same-invocation",
                new OperationInvocationContext.Provenance("correlation-A", "workflow-A"));
        OperationInvocationContext second = new OperationInvocationContext(
                actor, "same-invocation",
                new OperationInvocationContext.Provenance("correlation-B", "workflow-B"));
        ApplyResult applied = ApplyResult.applied(
                "same-plan", "same-invocation", BASE_REVISION, "revision-R1",
                "same-hash", BASE_REVISION, OperationPlanApplyService.CURRENT_REVISION_REF);
        when(mediaClipService.invoke(eq(request), any()))
                .thenReturn(new TimelineMediaClipOperationService.InvocationOutcome(applied));
        OperationInvocationPort port = new CanonicalOperationInvocationService(mediaClipService);

        OperationInvocationResult.Applied firstResult = assertInstanceOf(
                OperationInvocationResult.Applied.class, port.invoke(request, first));
        OperationInvocationResult.Applied secondResult = assertInstanceOf(
                OperationInvocationResult.Applied.class, port.invoke(request, second));

        assertEquals(firstResult.planDigest(), secondResult.planDigest());
        assertEquals(firstResult.newRevisionId(), secondResult.newRevisionId());
        assertEquals(firstResult.resultContentHash(), secondResult.resultContentHash());
        assertEquals("correlation-A", firstResult.correlationId());
        assertEquals("correlation-B", secondResult.correlationId());
        ArgumentCaptor<OperationRequest> requests = ArgumentCaptor.forClass(OperationRequest.class);
        ArgumentCaptor<OperationInvocationContext> contexts =
                ArgumentCaptor.forClass(OperationInvocationContext.class);
        verify(mediaClipService, times(2)).invoke(requests.capture(), contexts.capture());
        assertSame(request, requests.getAllValues().get(0));
        assertSame(request, requests.getAllValues().get(1));
        assertEquals(actor, contexts.getAllValues().get(0).actor());
        assertEquals(actor, contexts.getAllValues().get(1).actor());
        assertEquals("same-invocation", contexts.getAllValues().get(0).invocationId());
        assertEquals("same-invocation", contexts.getAllValues().get(1).invocationId());
    }

    @Test
    void wrongTenantActorDeniedBeforeBaseHydration() {
        TimelineRevisionSaveService writer = mock(TimelineRevisionSaveService.class);
        TimelineSourceReferenceValidator sources = mock(TimelineSourceReferenceValidator.class);
        AuthorizationDecisionPort authorization = authorizationRequest ->
                com.example.platform.shared.authorization.AuthorizationDecision.deny(
                        "WRONG_TENANT", "rbac", "hidden");
        var h7Service = new TimelineMediaClipOperationService(
                writer, sources, new InternalTimelineValidationService(), authorization,
                new OperationPlanApplyService(writer));
        OperationInvocationPort port = new CanonicalOperationInvocationService(h7Service);

        OperationInvocationException failure = assertThrows(
                OperationInvocationException.class,
                () -> port.invoke(validRequest("hidden-base-hash"), context("tenant-B")));

        assertEquals(OperationInvocationFailureCode.AUTHORIZATION_DENIED, failure.code());
        verifyNoInteractions(writer, sources);
    }

    @Test
    void missingInvocationContractFailsWithOneSafeStableDiagnostic() {
        TimelineMediaClipOperationService mediaClipService =
                mock(TimelineMediaClipOperationService.class);
        var service = new CanonicalOperationInvocationService(mediaClipService);

        OperationInvocationException failure = assertThrows(
                OperationInvocationException.class,
                () -> service.invoke(null, context(TENANT)));

        assertEquals(OperationInvocationFailureCode.INVALID_REQUEST, failure.code());
        assertEquals(java.util.Map.of("failure", "invalid-request"), failure.diagnostics());
        verifyNoInteractions(mediaClipService);
    }

    @Test
    void tenantlessActorFailsAsAuthorizationContextMismatchBeforeDelegation() {
        TimelineMediaClipOperationService mediaClipService =
                mock(TimelineMediaClipOperationService.class);
        var service = new CanonicalOperationInvocationService(mediaClipService);
        OperationInvocationContext context = new OperationInvocationContext(
                CanonicalActor.user("editor-1", null, Set.of("EDITOR"), "test"),
                "invocation-1",
                new OperationInvocationContext.Provenance("correlation-1", "test"));

        OperationInvocationException failure = assertThrows(
                OperationInvocationException.class,
                () -> service.invoke(validRequest("base-hash"), context));

        assertEquals(
                OperationInvocationFailureCode.AUTHORIZATION_CONTEXT_MISMATCH,
                failure.code());
        verifyNoInteractions(mediaClipService);
    }

    @Test
    void unexpectedInfrastructureFailureIsSanitizedAsApplyFailure() {
        TimelineMediaClipOperationService mediaClipService =
                mock(TimelineMediaClipOperationService.class);
        OperationRequest request = validRequest("base-hash");
        OperationInvocationContext context = context(TENANT);
        when(mediaClipService.invoke(request, context)).thenThrow(
                new RuntimeException("provider secret", new SQLException("jdbc secret")));
        var service = new CanonicalOperationInvocationService(mediaClipService);

        OperationInvocationException failure = assertThrows(
                OperationInvocationException.class,
                () -> service.invoke(request, context));

        assertEquals(OperationInvocationFailureCode.APPLY_FAILURE, failure.code());
        assertEquals(java.util.Map.of("failure", "apply-failure"), failure.diagnostics());
        assertEquals(OperationInvocationFailureCode.APPLY_FAILURE.name(), failure.getMessage());
        assertEquals(null, failure.getCause());
    }

    @ParameterizedTest
    @MethodSource("failureMappings")
    void mapsH7FailuresToOneSafeInvocationException(
            TimelineOperationException.Code internalCode,
            OperationInvocationFailureCode publicCode,
            String stableLabel) {
        TimelineMediaClipOperationService mediaClipService =
                mock(TimelineMediaClipOperationService.class);
        OperationRequest request = validRequest("base-hash");
        OperationInvocationContext context = context(TENANT);
        when(mediaClipService.invoke(request, context))
                .thenThrow(new TimelineOperationException(
                        internalCode, List.of("secret provider or SQL detail")));
        var service = new CanonicalOperationInvocationService(mediaClipService);

        OperationInvocationException failure = assertThrows(
                OperationInvocationException.class,
                () -> service.invoke(request, context));

        assertEquals(publicCode, failure.code());
        assertEquals(java.util.Map.of("failure", stableLabel), failure.diagnostics());
        assertEquals(publicCode.name(), failure.getMessage());
        assertEquals(0, failure.getStackTrace().length);
    }

    private static Stream<Arguments> failureMappings() {
        return Stream.of(
                Arguments.of(TimelineOperationException.Code.BASE_REVISION_NOT_FOUND,
                        OperationInvocationFailureCode.BASE_REVISION_NOT_FOUND,
                        "base-revision-not-found"),
                Arguments.of(TimelineOperationException.Code.STALE_BASE_REVISION,
                        OperationInvocationFailureCode.STALE_BASE_REVISION,
                        "stale-base-revision"),
                Arguments.of(TimelineOperationException.Code.SOURCE_REFERENCE_INVALID,
                        OperationInvocationFailureCode.SOURCE_REFERENCE_INVALID,
                        "source-reference-invalid"),
                Arguments.of(TimelineOperationException.Code.CANDIDATE_INVALID,
                        OperationInvocationFailureCode.CANDIDATE_INVALID,
                        "candidate-invalid"),
                Arguments.of(TimelineOperationException.Code.PLAN_CHANGED,
                        OperationInvocationFailureCode.PLAN_CHANGED, "plan-changed"),
                Arguments.of(TimelineOperationException.Code.AUTHORIZATION_DENIED,
                        OperationInvocationFailureCode.AUTHORIZATION_DENIED,
                        "authorization-denied"),
                Arguments.of(TimelineOperationException.Code.AUTHORIZATION_CONTEXT_MISMATCH,
                        OperationInvocationFailureCode.AUTHORIZATION_CONTEXT_MISMATCH,
                        "authorization-context-mismatch"),
                Arguments.of(TimelineOperationException.Code.IDEMPOTENCY_KEY_CONFLICT,
                        OperationInvocationFailureCode.IDEMPOTENCY_CONFLICT,
                        "idempotency-conflict"),
                Arguments.of(TimelineOperationException.Code.TARGET_MISSING,
                        OperationInvocationFailureCode.TARGET_MISSING, "target-missing"),
                Arguments.of(TimelineOperationException.Code.PLACEMENT_CONFLICT,
                        OperationInvocationFailureCode.PLACEMENT_CONFLICT,
                        "placement-conflict"),
                Arguments.of(TimelineOperationException.Code.CANONICAL_INVARIANT_VIOLATION,
                        OperationInvocationFailureCode.CANONICAL_INVARIANT_VIOLATION,
                        "canonical-invariant-violation"),
                Arguments.of(TimelineOperationException.Code.PERSISTENCE_FAILURE,
                        OperationInvocationFailureCode.PERSISTENCE_FAILURE,
                        "persistence-failure"),
                Arguments.of(TimelineOperationException.Code.APPLY_UNKNOWN_FAILURE,
                        OperationInvocationFailureCode.APPLY_FAILURE, "apply-failure"));
    }

    private static OperationInvocationContext context(String tenantId) {
        return new OperationInvocationContext(
                actor("editor-1", tenantId),
                "invocation-1",
                new OperationInvocationContext.Provenance("correlation-1", "test"));
    }

    private static CanonicalActor actor(String actorId, String tenantId) {
        return CanonicalActor.user(actorId, tenantId, Set.of("EDITOR"), "test");
    }

    private static OperationRequest validRequest(String baseHash) {
        MediaClip.TimeRange sourceRange = new MediaClip.TimeRange(
                MediaTime.ofRational(10, 1), MediaTime.ofRational(20, 1));
        MediaClip.TimeRange placement = new MediaClip.TimeRange(
                MediaTime.ZERO, MediaTime.ofRational(10, 1));
        var parameters = new OperationParameters.AddMediaClipParameters(
                "video-1", TimelineClipId.of("clip-S-10-20"),
                TestSourceBindings.of(
                        "media-S", "stream-S-video", "artifact-S-v1", sourceRange),
                placement,
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD));
        return new OperationRequest(
                OperationDefinition.V1.ADD_MEDIA_CLIP.definitionId(),
                OperationDefinition.V1.ADD_MEDIA_CLIP.version(),
                new OperationTargetRequest.TimelineTargetRequest(PROJECT),
                parameters, BASE_REVISION, baseHash, null);
    }

    private static TimelineDocument baseTimeline() {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("video-1", "main", TrackType.VIDEO, List.of())),
                TimelineMetadata.empty());
    }

    private static TimelineRevision revision(
            String revisionId, String parent, TimelineDocument document, String author) {
        String timelineDigest = DIGESTER.digest(document);
        var effectRef = new EffectSemanticSnapshotReference(
                EffectSemanticSnapshotId.of("effect-" + revisionId), "empty-effect-digest",
                EffectSemanticContractVersion.current());
        String fullDigest = TimelineRevisionEffectSemanticCommitment
                .revisionEffectSemanticDigest(timelineDigest, effectRef);
        var semanticContext = new TimelineRevisionSemanticContext(
                timelineDigest, effectRef, fullDigest,
                TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1);
        return new TimelineRevision(
                revisionId, PROJECT, parent, TimelineDocument.CURRENT_SCHEMA_VERSION,
                document, fullDigest, Instant.EPOCH, author, semanticContext);
    }
}
