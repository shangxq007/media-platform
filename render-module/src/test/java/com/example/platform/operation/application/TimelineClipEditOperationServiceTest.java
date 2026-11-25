package com.example.platform.operation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.operation.invocation.OperationInvocationContext;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.operation.plan.ApplyResult;
import com.example.platform.operation.application.OperationPlanApplyService;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.api.composition.TimelineSourceValidation;
import com.example.platform.timeline.api.revision.TimelineMutationContext;
import com.example.platform.timeline.app.InternalTimelineValidationService;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import com.example.platform.timeline.semantics.effect.EffectSemanticContractVersion;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import com.example.platform.timeline.semantics.effect.TimelineRevisionEffectSemanticCommitment;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.timeline.version.TimelineRevisionSemanticContext;
import com.example.platform.timeline.otio.CanonicalTimelineOtioExporter;
import com.example.platform.timeline.otio.OtioExportIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TimelineClipEditOperationServiceTest {

    private static final String TENANT = "tenant-edit";
    private static final String PROJECT = "timeline-edit";
    private static final String BASE_REVISION = "rev-base";
    private static final TimelineContentDigester DIGESTER = new TimelineContentDigester();

    @Test
    void moveChangesPlacementButPreservesClipIdentityAndSourceRange() {
        Fixture fixture = fixture();
        ApplyResult result = fixture.invoke(OperationDefinition.V1.MOVE,
                new OperationParameters.MoveParameters(MediaTime.ofRational(1, 2), false));

        assertThat(result.status()).isEqualTo(ApplyResult.APPLIED);
        assertThat(result.newRevisionId()).isEqualTo("rev-next");
        TimelineClip clip = fixture.savedClip();
        assertThat(clip.getClipId().value()).isEqualTo("clip-blue");
        assertThat(clip.getStartTime()).isEqualTo(MediaTime.ofRational(3, 2));
        assertThat(clip.getEndTime()).isEqualTo(MediaTime.ofRational(5, 2));
        assertThat(clip.getTrimStart()).isEqualTo(MediaTime.ZERO);
        assertThat(clip.getTrimEnd()).isEqualTo(MediaTime.ofRational(1, 1));
        assertThat(clip.getMediaAssetId()).isEqualTo("media-blue");
        assertThat(clip.getArtifactId()).isEqualTo("art-blue");
    }

    @Test
    void trimStartAdvancesTimelineAndSourceWindows() {
        Fixture fixture = fixture();
        fixture.invoke(OperationDefinition.V1.TRIM,
                new OperationParameters.TrimParameters(
                        OperationParameters.TrimParameters.TrimEdge.START, MediaTime.ofRational(1, 2)));

        TimelineClip clip = fixture.savedClip();
        assertThat(clip.getStartTime()).isEqualTo(MediaTime.ofRational(3, 2));
        assertThat(clip.getEndTime()).isEqualTo(MediaTime.ofRational(2, 1));
        assertThat(clip.getTrimStart()).isEqualTo(MediaTime.ofRational(1, 2));
        assertThat(clip.getTrimEnd()).isEqualTo(MediaTime.ofRational(1, 1));
    }

    @Test
    void trimEndShortensTimelineAndSourceWindows() {
        Fixture fixture = fixture();
        fixture.invoke(OperationDefinition.V1.TRIM,
                new OperationParameters.TrimParameters(
                        OperationParameters.TrimParameters.TrimEdge.END, MediaTime.ofRational(1, 2)));

        TimelineClip clip = fixture.savedClip();
        assertThat(clip.getStartTime()).isEqualTo(MediaTime.ofRational(1, 1));
        assertThat(clip.getEndTime()).isEqualTo(MediaTime.ofRational(3, 2));
        assertThat(clip.getTrimStart()).isEqualTo(MediaTime.ZERO);
        assertThat(clip.getTrimEnd()).isEqualTo(MediaTime.ofRational(1, 2));
    }

    @Test
    void trimThatWouldConsumeTheWholeClipIsRejectedAtomically() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> fixture.invoke(OperationDefinition.V1.TRIM,
                new OperationParameters.TrimParameters(
                        OperationParameters.TrimParameters.TrimEdge.END, MediaTime.ofRational(1, 1))))
                .isInstanceOf(TimelineOperationException.class);
        verify(fixture.writer, never()).saveRevisionForCommand(
                any(TimelineMutationContext.class), any(RevisionRef.class), any(), any(), any());
        assertThat(fixture.saved.get()).isNull();
    }

    @Test
    void otioProjectionReflectsMoveTrimAndDelete() throws Exception {
        Fixture move = fixture();
        move.invoke(OperationDefinition.V1.MOVE,
                new OperationParameters.MoveParameters(MediaTime.ofRational(1, 2), false));
        JsonNode moveChildren = otioChildren(move);
        assertThat(moveChildren).hasSize(2);
        assertThat(moveChildren.get(0).get("OTIO_SCHEMA").asText()).isEqualTo("Gap.1");
        assertThat(seconds(moveChildren.get(0).get("source_range").get("duration"))).isEqualTo(1.5);
        assertThat(moveChildren.get(1).get("OTIO_SCHEMA").asText()).isEqualTo("Clip.2");
        assertThat(seconds(moveChildren.get(1).get("source_range").get("start_time"))).isEqualTo(0.0);
        assertThat(seconds(moveChildren.get(1).get("source_range").get("duration"))).isEqualTo(1.0);

        Fixture trim = fixture();
        trim.invoke(OperationDefinition.V1.TRIM,
                new OperationParameters.TrimParameters(
                        OperationParameters.TrimParameters.TrimEdge.END, MediaTime.ofRational(1, 2)));
        JsonNode trimChildren = otioChildren(trim);
        assertThat(trimChildren).hasSize(2);
        assertThat(seconds(trimChildren.get(0).get("source_range").get("duration"))).isEqualTo(1.0);
        assertThat(seconds(trimChildren.get(1).get("source_range").get("duration"))).isEqualTo(0.5);

        Fixture delete = fixture();
        delete.invoke(OperationDefinition.V1.DELETE, new OperationParameters.NoParameters());
        assertThat(otioChildren(delete)).isEmpty();
    }

    private static JsonNode otioChildren(Fixture fixture) throws Exception {
        String json = new CanonicalTimelineOtioExporter().export(fixture.saved.get(),
                new OtioExportIdentity(TENANT, PROJECT, "rev-next", TimelineDocument.CURRENT_SCHEMA_VERSION));
        JsonNode root = new ObjectMapper().readTree(json);
        return root.get("tracks").get("children").get(0).get("children");
    }

    private static double seconds(JsonNode rational) {
        return rational.get("value").asDouble() / rational.get("rate").asDouble();
    }

    @Test
    void deleteRemovesOnlyTheTimelineReference() {
        Fixture fixture = fixture();
        fixture.invoke(OperationDefinition.V1.DELETE, new OperationParameters.NoParameters());

        assertThat(fixture.saved.get()).isNotNull();
        assertThat(fixture.saved.get().getTracks().getFirst().clips()).isEmpty();
        assertThat(fixture.saved.get().getTracks().getFirst().trackId()).isEqualTo("v1");
        // No artifact/source service is called by delete; the source identity remains in the base document.
        assertThat(fixture.base.getTracks().getFirst().clips().getFirst().getArtifactId()).isEqualTo("art-blue");
    }

    @Test
    void deleteOfUnknownClipFailsBeforePersistence() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> fixture.invoke(OperationDefinition.V1.DELETE,
                new OperationParameters.NoParameters(), TimelineClipId.of("missing")))
                .isInstanceOf(TimelineOperationException.class);
        verify(fixture.writer, never()).saveRevisionForCommand(
                any(TimelineMutationContext.class), any(RevisionRef.class), any(), any(), any());
    }

    private static Fixture fixture() {
        TimelineClip clip = new TimelineClip("clip-blue", "media-blue", "stream-blue", "art-blue",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", MediaTime.ofRational(1, 1), MediaTime.ofRational(2, 1),
                MediaTime.ZERO, MediaTime.ofRational(1, 1), "MEDIA_STREAM",
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD), List.of());
        TimelineDocument base = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("v1", "main", TrackType.VIDEO, List.of(clip))),
                TimelineMetadata.empty());
        String baseHash = DIGESTER.digest(base);
        TimelineRevision baseRevision = revision(BASE_REVISION, null, base);
        AtomicReference<TimelineDocument> saved = new AtomicReference<>();
        TimelineRevisionSaveService writer = mock(TimelineRevisionSaveService.class);
        when(writer.findById(TENANT, BASE_REVISION)).thenReturn(baseRevision);
        when(writer.findPayloadDocument(TENANT, BASE_REVISION)).thenReturn(Optional.of(base));
        when(writer.saveRevisionForCommand(
                any(TimelineMutationContext.class), eq(RevisionRef.main(TENANT, PROJECT)),
                eq(BASE_REVISION), any(TimelineDocument.class),
                any(com.example.platform.timeline.api.revision.TimelineRevisionCommands.RevisionWriteCommand.class)))
                .thenAnswer(invocation -> {
                    TimelineDocument document = invocation.getArgument(3);
                    saved.set(document);
                    return new com.example.platform.timeline.api.revision.TimelineRevisionCommands
                            .RevisionWriteResult("rev-next", BASE_REVISION, DIGESTER.digest(document), false);
                });
        TimelineSourceValidation sources = mock(TimelineSourceValidation.class);
        when(sources.validate(any(), anyString(), anyString(), any()))
                .thenReturn(new TimelineSourceValidation.ValidationResult(true, List.of()));
        AuthorizationDecisionPort authorization = request ->
                com.example.platform.shared.authorization.AuthorizationDecision.allow("test");
        var service = new TimelineMediaClipOperationService(writer, sources,
                new InternalTimelineValidationService(), authorization,
                new OperationPlanApplyService(writer));
        CanonicalActor actor = CanonicalActor.user("editor", TENANT, Set.of("EDITOR"), "test");
        return new Fixture(base, baseHash, saved, writer, service, actor);
    }

    private static TimelineRevision revision(String id, String parent, TimelineDocument document) {
        String timelineDigest = DIGESTER.digest(document);
        var effectRef = new EffectSemanticSnapshotReference(
                EffectSemanticSnapshotId.of("effect-" + id), "empty", EffectSemanticContractVersion.current());
        String fullDigest = TimelineRevisionEffectSemanticCommitment
                .revisionEffectSemanticDigest(timelineDigest, effectRef);
        return new TimelineRevision(id, PROJECT, parent, TimelineDocument.CURRENT_SCHEMA_VERSION,
                document, fullDigest, Instant.EPOCH, "base",
                new TimelineRevisionSemanticContext(timelineDigest, effectRef, fullDigest,
                        TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1));
    }

    private record Fixture(TimelineDocument base, String baseHash,
                           AtomicReference<TimelineDocument> saved,
                           TimelineRevisionSaveService writer,
                           TimelineMediaClipOperationService service, CanonicalActor actor) {
        ApplyResult invoke(OperationDefinition definition, OperationParameters parameters) {
            return invoke(definition, parameters, TimelineClipId.of("clip-blue"));
        }

        ApplyResult invoke(OperationDefinition definition, OperationParameters parameters,
                          TimelineClipId clipId) {
            OperationRequest request = new OperationRequest(definition.definitionId(), definition.version(),
                    new OperationTargetRequest.ClipSelectionTargetRequest(PROJECT,
                            new SelectionSpec.ExplicitObjectSelection(List.of(clipId)),
                            SelectionSpec.ExpansionPolicy.EXACT),
                    parameters, BASE_REVISION, baseHash, null);
            return service.invoke(request,
                    new OperationInvocationContext(actor, "cmd-" + definition.definitionId().value(),
                            null, "test")).result();
        }

        TimelineClip savedClip() {
            return saved.get().getTracks().getFirst().clips().getFirst();
        }
    }

}
