package com.example.platform.operation.testsupport;

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
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.TextElementId;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationKeyframe;
import com.example.platform.timeline.canonicalmodel.CanonicalTransition;
import com.example.platform.timeline.semantics.relationship.GroupId;
import com.example.platform.timeline.semantics.relationship.GroupRelationship;
import com.example.platform.timeline.semantics.relationship.SemanticRelationship;
import com.example.platform.timeline.semantics.relationship.SyncRelationship;

import java.util.List;
import java.util.Map;

/**
 * FIELD-PRESERVATION-P0 test fixture: a {@link TimelineDocument} with all 8 canonical
 * fields carrying non-empty values, so a preservation assertion is meaningful (an empty
 * default would make "preserved" trivially true).
 */
public final class RichTimelineDocument {

    private RichTimelineDocument() {
    }

    public static final String BASE_REVISION_ID = "R100";
    public static final String BASE_CONTENT_HASH = "hash-100";

    /** The sync relationship present in {@link #rich()} (identity key for sync targets). */
    public static SyncRelationship baseSync() {
        return SyncRelationship.of(TimelineClipId.of("clip-1"), MediaTime.ZERO,
                TimelineClipId.of("clip-2"), MediaTime.ZERO);
    }

    public static TimelineDocument rich() {
        TimelineTrack track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(
                clip("clip-1", 0, 1000),
                clip("clip-2", 1000, 2000),
                clip("clip-3", 2000, 3000)));

        TimelineMetadata metadata = new TimelineMetadata("Title", "Desc", Map.of("k", "v"));
        AudioMix audioMix = AudioMix.of(AudioMasterBus.master(),
                List.of(AudioRoute.of(AudioMixInput.of("track-1", "clip-1"))));
        List<SemanticRelationship> relationships = List.of(
                baseSync(),
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
                metadata, audioMix, relationships, textElements, transitions, automations);
    }

    public static TextFrame frame() {
        return new TextFrame(FontRational.of(640, 1), null,
                TextFrame.HorizontalAlignment.START, TextFrame.VerticalAlignment.TOP,
                ParagraphStyle.WrapPolicy.WRAP, TextFrame.OverflowBehavior.CLIP);
    }

    /** A distinct frame (different width) so a layout op authors a real change. */
    public static TextFrame altFrame() {
        return new TextFrame(FontRational.of(1280, 1), null,
                TextFrame.HorizontalAlignment.CENTER, TextFrame.VerticalAlignment.TOP,
                ParagraphStyle.WrapPolicy.WRAP, TextFrame.OverflowBehavior.CLIP);
    }

    public static TextElement textElement(String id) {
        StyledText styled = new StyledText(new TextContent("Hello"), List.of(), List.of(),
                new ParagraphStyle(ParagraphStyle.Alignment.START, ParagraphStyle.Justification.NONE,
                        LineHeight.ratio(FontRational.of(12, 10)), ParagraphStyle.WrapPolicy.WRAP,
                        ParagraphBaseDirection.AUTO, ParagraphStyle.LineBreakPolicy.STANDARD));
        return new TextElement(new TextElementId(id), FontRational.whole(0), FontRational.whole(5),
                styled, frame(),
                new FontFallbackPolicy(List.of(new FontFamilyName("Arial")), List.of(), List.of(), List.of()),
                List.of());
    }

    public static TimelineClip clip(String id, long startMillis, long endMillis) {
        return new TimelineClip(id, "asset-1", null, null, null,
                MediaTime.ofMillis(startMillis), MediaTime.ofMillis(endMillis),
                MediaTime.ZERO, MediaTime.ofMillis(endMillis - startMillis), "MEDIA_STREAM");
    }
}
