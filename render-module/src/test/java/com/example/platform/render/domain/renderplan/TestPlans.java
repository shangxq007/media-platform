package com.example.platform.render.domain.renderplan;

import com.example.platform.audio.domain.mix.AudioGain;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioMute;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.audio.domain.mix.StereoBalance;
import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.resolution.ResolvedFontInstance;
import com.example.platform.fonttext.resolution.ResolvedFontRun;
import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.resource.FontFormat;
import com.example.platform.fonttext.resource.ValidatedFontExecutionReference;
import com.example.platform.fonttext.security.FontSecurityState;
import com.example.platform.fonttext.text.ParagraphBaseDirection;
import com.example.platform.fonttext.text.RangeDirectionOverride;
import com.example.platform.fonttext.text.ScriptTag;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.text.TextRange;
import com.example.platform.fonttext.text.TextSemanticRun;
import com.example.platform.fonttext.typography.FontFamilyName;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.FontSelectionIntent;
import com.example.platform.fonttext.typography.FontSize;
import com.example.platform.fonttext.typography.LineHeight;
import com.example.platform.fonttext.typography.OpenTypeFeatureIntent;
import com.example.platform.fonttext.typography.OpticalSizingIntent;
import com.example.platform.fonttext.typography.ParagraphStyle;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.TextElementId;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Package-private canonical test fixtures (brief §13). Builds one canonical
 * {@link RenderPlanningInput}: one MediaClip (t1/c1), one enabled video
 * GAUSSIAN_BLUR effect, one AudioMix route, one TextElement, a RENDER_MASTER
 * output request, fully-resolved source, and a full capability context.
 */
final class TestPlans {

    static final String REVISION_ID = "rev-1";
    static final String REQUEST_ID = "req-1";
    static final String TRACK_ID = "t1";
    static final String CLIP_ID = "c1";
    static final String ARTIFACT_ID = "art-1";
    static final String EFFECT_INSTANCE_ID = "eff-1";
    static final String TEXT_ELEMENT_ID = "text-1";

    // 64-hex SHA-256 values
    static final String ARTIFACT_DIGEST_HEX =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    static final String REVISION_DIGEST_HEX =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private TestPlans() {
    }

    /** The canonical planning input described in brief §13 (source RESOLVED). */
    static RenderPlanningInput canonicalInput() {
        return new RenderPlanningInput(
                revisionRef(),
                List.of(mediaClip()),
                List.of(gaussianBlurEffect()),
                List.of(effectDefinition()),
                audioMix(),
                List.of(textElement()),
                renderRequest(),
                new SourceResolutionInput(Map.of(artifactId(), RenderSourceResolutionState.RESOLVED)),
                fullCapabilityContext());
    }

    /** Canonical input with the source in the given resolution state. */
    static RenderPlanningInput inputWithSourceState(RenderSourceResolutionState state) {
        RenderPlanningInput base = canonicalInput();
        return new RenderPlanningInput(
                base.revision(), base.clips(), base.effects(), base.effectDefinitions(), base.audioMix(),
                base.textElements(), base.request(),
                new SourceResolutionInput(Map.of(artifactId(), state)),
                base.capabilities());
    }

    /** Canonical input with the given capability context. */
    static RenderPlanningInput inputWithCapabilities(CapabilityContext capabilities) {
        RenderPlanningInput base = canonicalInput();
        return new RenderPlanningInput(
                base.revision(), base.clips(), base.effects(), base.effectDefinitions(), base.audioMix(),
                base.textElements(), base.request(), base.resolution(), capabilities);
    }

    /** Canonical input with a different request id. */
    static RenderPlanningInput inputWithRequestId(String requestId) {
        RenderPlanningInput base = canonicalInput();
        RenderRequest req = base.request();
        return new RenderPlanningInput(
                base.revision(), base.clips(), base.effects(), base.effectDefinitions(), base.audioMix(),
                base.textElements(),
                new RenderRequest(new RenderRequestId(requestId), req.extent(), req.outputs()),
                base.resolution(), base.capabilities());
    }

    static TimelineRevisionReference revisionRef() {
        return new TimelineRevisionReference(REVISION_ID, ContentDigest.sha256(REVISION_DIGEST_HEX));
    }

    static ArtifactId artifactId() {
        return new ArtifactId(ARTIFACT_ID);
    }

    static ContentDigest artifactDigest() {
        return ContentDigest.sha256(ARTIFACT_DIGEST_HEX);
    }

    static MediaClip mediaClip() {
        MediaTime start = MediaTime.ofRational(0, 1);
        MediaTime end = MediaTime.ofRational(2, 1);
        MediaClip.TimeRange timelineRange = new MediaClip.TimeRange(start, end);
        MediaClip.TimeRange sourceRange = new MediaClip.TimeRange(start, end);
        ConstantRateTemporalMapping mapping = ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD);
        MediaStreamSourceBinding binding = new MediaStreamSourceBinding(
                MediaAssetId.of("asset-1"),
                MediaStreamId.of("stream-1"),
                artifactId(),
                artifactDigest(),
                sourceRange);
        return new MediaClip(CLIP_ID, TRACK_ID, timelineRange, sourceRange, mapping, binding);
    }

    static EffectInstance gaussianBlurEffect() {
        return new EffectInstance(
                EFFECT_INSTANCE_ID,
                "def-blur",
                "1",
                EffectInstance.EffectMediaType.VIDEO,
                true,
                new MediaClip.TimeRange(MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1)),
                // authored effect parameters only (C11); the category is NOT a
                // parameter — it is resolved from the EffectDefinition catalog.
                Map.of("radiusPixels", "4"),
                Map.of(),
                new EffectInstance.EffectProvenance("test", "t", 0L));
    }

    /** Canonical GAUSSIAN_BLUR effect definition (C11 category authority). */
    static EffectInstance.EffectDefinition effectDefinition() {
        return new EffectInstance.EffectDefinition(
                "def-blur",
                "1",
                EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                Map.of(),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                List.of(),
                List.of(),
                List.of());
    }

    static AudioMix audioMix() {
        return new AudioMix(
                com.example.platform.audio.domain.mix.AudioMasterBus.master(),
                List.of(new AudioRoute(
                        AudioMixInput.of(TRACK_ID, CLIP_ID),
                        AudioGain.of(0.8),
                        AudioMute.of(false),
                        StereoBalance.of(0.0),
                        List.of())));
    }

    static TextElement textElement() {
        TextContent content = new TextContent("Hello");
        FontContentDigest digest = FontContentDigest.ofText("inter-v1");
        ValidatedFontExecutionReference ref = new ValidatedFontExecutionReference(
                digest, digest, FontSecurityState.VALIDATED_EXECUTION_FONT,
                FontFormat.TRUETYPE, new FaceIndex(0));
        ResolvedFontInstance font = new ResolvedFontInstance(ref, List.of());
        StyledText styled = new StyledText(
                content,
                List.of(new TextSemanticRun(TextRange.of(0, content.scalarCount()),
                        null, ScriptTag.LATIN, RangeDirectionOverride.NONE)),
                List.of(new com.example.platform.fonttext.typography.TextStyleRun(
                        TextRange.of(0, content.scalarCount()), sampleStyle())),
                new ParagraphStyle(ParagraphStyle.Alignment.START, ParagraphStyle.Justification.NONE,
                        LineHeight.ratio(FontRational.of(12, 10)),
                        ParagraphStyle.WrapPolicy.WRAP, ParagraphBaseDirection.AUTO,
                        ParagraphStyle.LineBreakPolicy.STANDARD));
        return new TextElement(
                new TextElementId(TEXT_ELEMENT_ID),
                FontRational.whole(0),
                FontRational.whole(5),
                styled,
                new TextFrame(FontRational.of(640, 1), null,
                        TextFrame.HorizontalAlignment.START, TextFrame.VerticalAlignment.TOP,
                        ParagraphStyle.WrapPolicy.WRAP, TextFrame.OverflowBehavior.CLIP),
                new FontFallbackPolicy(List.of(new FontFamilyName("Arial")), List.of(), List.of(), List.of()),
                List.of(new ResolvedFontRun(TextRange.of(0, content.scalarCount()), font)));
    }

    private static com.example.platform.fonttext.typography.TextStyle sampleStyle() {
        return new com.example.platform.fonttext.typography.TextStyle(
                new FontSelectionIntent(List.of(new FontFamilyName("Inter")),
                        FontSelectionIntent.WeightIntent.NORMAL, FontSelectionIntent.StretchIntent.NORMAL,
                        FontSelectionIntent.SlantIntent.NORMAL, OpticalSizingIntent.disabled(), List.of()),
                new FontSize(FontRational.of(24, 1)),
                FontRational.of(0, 1), OpenTypeFeatureIntent.empty());
    }

    static RenderRequest renderRequest() {
        return new RenderRequest(
                new RenderRequestId(REQUEST_ID),
                new RenderExtent(MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1), FrameRate.of(30, 1)),
                List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)));
    }

    /** Capability context that supports every capability used by the canonical fixture. */
    static CapabilityContext fullCapabilityContext() {
        return new CapabilityContext(Set.of(
                RenderCapabilityId.DECODE,
                RenderCapabilityId.EFFECT_GAUSSIAN_BLUR,
                RenderCapabilityId.AUDIO_PROCESS,
                RenderCapabilityId.MIX_AUDIO,
                RenderCapabilityId.RASTERIZE_TIMED_TEXT,
                RenderCapabilityId.OUTPUT_ENCODE));
    }
}
