package com.example.platform.operation.application;

import com.example.platform.audio.domain.mix.AudioMasterBus;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.text.ParagraphBaseDirection;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.typography.FontFamilyName;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.LineHeight;
import com.example.platform.fonttext.typography.ParagraphStyle;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationRequestResolver;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.operation.plan.OperationPlanner;
import com.example.platform.render.testsupport.CanonicalFieldsAssert;
import com.example.platform.render.testsupport.CanonicalFieldsAssert.Field;
import com.example.platform.render.testsupport.TestSourceBindings;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.TextElementId;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationKeyframe;
import com.example.platform.timeline.canonicalmodel.CanonicalTransition;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.relationship.GroupId;
import com.example.platform.timeline.semantics.relationship.GroupRelationship;
import com.example.platform.timeline.semantics.relationship.SemanticRelationship;
import com.example.platform.timeline.semantics.relationship.SyncRelationship;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * FIELD-PRESERVATION-P0 (Sink 3): {@code planAddMediaClip} previously dropped
 * {@code transitions}/{@code automations}. This drives the production
 * {@link OperationPlanner} for {@code timeline.media-clip.add} on a base whose 8 canonical
 * fields are all non-empty, and asserts the untouched 7 are preserved.
 */
class MediaClipPlanFieldPreservationTest {

    @Test
    void addMediaClipPreservesUntouchedCanonicalFields() throws Exception {
        TimelineDocument base = richDocument();
        String baseHash = new TimelineContentDigester().digest(base);
        var sourceRange = new MediaClip.TimeRange(MediaTime.ofRational(10, 1), MediaTime.ofRational(20, 1));
        var parameters = new OperationParameters.AddMediaClipParameters(
                "track-1", TimelineClipId.of("clip-S"),
                TestSourceBindings.of("asset-S", "stream-S", "artifact-S-v1", sourceRange),
                new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(10, 1)),
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD));
        OperationRequest request = new OperationRequest(
                OperationDefinition.V1.ADD_MEDIA_CLIP.definitionId(),
                OperationDefinition.V1.ADD_MEDIA_CLIP.version(),
                new OperationTargetRequest.TimelineTargetRequest("timeline-T"),
                parameters, "revision-R0", baseHash, null);

        var instance = OperationRequestResolver.resolve(request,
                new OperationRequestResolver.OperationBaseContext("revision-R0", baseHash, base, "timeline-T"));
        TimelineDocument candidate =
                new OperationPlanner().plan(instance, instance.baseRevisionId(), base).candidateTimeline();

        CanonicalFieldsAssert.assertPreserved(base, candidate, Field.TRACKS);
        assertNotEquals(base.getTracks(), candidate.getTracks(), "add-media-clip authors tracks");
    }

    private static TimelineDocument richDocument() {
        TimelineTrack track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(
                clip("clip-1", 0, 1000), clip("clip-2", 1000, 2000), clip("clip-3", 2000, 3000)));
        AudioMix audioMix = AudioMix.of(AudioMasterBus.master(),
                List.of(AudioRoute.of(AudioMixInput.of("track-1", "clip-1"))));
        List<SemanticRelationship> relationships = List.of(
                SyncRelationship.of(TimelineClipId.of("clip-1"), MediaTime.ZERO,
                        TimelineClipId.of("clip-2"), MediaTime.ZERO),
                GroupRelationship.of(GroupId.of("g-1"),
                        List.of(TimelineClipId.of("clip-1"), TimelineClipId.of("clip-2"))));
        List<TextElement> textElements = List.of(textElement("elem-1"));
        List<CanonicalTransition> transitions = List.of(new CanonicalTransition(
                "transition-1", "crossfade", "1", "clip-1", "clip-2", "VIDEO",
                MediaTime.ofMillis(250), "CENTER", "OVERLAP", Map.of("curve", "linear")));
        List<CanonicalAutomationCurve> automations = List.of(new CanonicalAutomationCurve(
                "automation-1", "clip-1", "parameters.gain", "NUMBER", "HOLD",
                List.of(new CanonicalAutomationKeyframe("kf-1", MediaTime.ZERO, 0.5, "LINEAR"))));

        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track),
                new TimelineMetadata("Title", "Desc", Map.of("k", "v")),
                audioMix, relationships, textElements, transitions, automations);
    }

    private static TextElement textElement(String id) {
        TextFrame frame = new TextFrame(FontRational.of(640, 1), null,
                TextFrame.HorizontalAlignment.START, TextFrame.VerticalAlignment.TOP,
                ParagraphStyle.WrapPolicy.WRAP, TextFrame.OverflowBehavior.CLIP);
        StyledText styled = new StyledText(new TextContent("Hello"), List.of(), List.of(),
                new ParagraphStyle(ParagraphStyle.Alignment.START, ParagraphStyle.Justification.NONE,
                        LineHeight.ratio(FontRational.of(12, 10)), ParagraphStyle.WrapPolicy.WRAP,
                        ParagraphBaseDirection.AUTO, ParagraphStyle.LineBreakPolicy.STANDARD));
        return new TextElement(new TextElementId(id), FontRational.whole(0), FontRational.whole(5),
                styled, frame,
                new FontFallbackPolicy(List.of(new FontFamilyName("Arial")), List.of(), List.of(), List.of()),
                List.of());
    }

    private static TimelineClip clip(String id, long startMillis, long endMillis) {
        return new TimelineClip(id, "asset-1", null, null, null,
                MediaTime.ofMillis(startMillis), MediaTime.ofMillis(endMillis),
                MediaTime.ZERO, MediaTime.ofMillis(endMillis - startMillis), "MEDIA_STREAM");
    }
}
