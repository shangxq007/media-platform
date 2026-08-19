package com.example.platform.render.domain.renderplan;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.semantics.clip.TimelineSourceBinding;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.FreezeTemporalMapping;
import com.example.platform.timeline.semantics.temporal.TemporalMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default materializer (C16). For each clip: a DECODE node + chained EFFECT nodes
 * for enabled video effects; from AudioMix: AUDIO_PROCESS + AUDIO_MIX nodes; for
 * each TextElement: a TIMED_TEXT node; one OUTPUT node. All time math is exact
 * rational (C11). Provider-neutral: no provider/worker/device/tier/price fields.
 */
public final class DefaultRenderMaterializer implements RenderMaterializer {

    private static final RenderPlanCanonicalCodec CODEC = RenderPlanFingerprintCalculator.codec();

    /** Synthetic mix input for audio edges that have no authored AudioMixInput (mix node internals). */
    private static final AudioMixInput SYNTHETIC_MIX_INPUT = AudioMixInput.of("synthetic", "synthetic");

    private static final String OP_DECODE = "decode";
    private static final String OP_GAIN = "gain";
    private static final String OP_MIX = "mix";
    private static final String OP_RASTER = "raster";
    private static final String OP_ENCODE = "encode";

    @Override
    public RenderMaterializationResult materialize(RenderPlanningInput input) {
        List<RenderNode> nodes = new ArrayList<>();
        List<RenderDependencyEdge> edges = new ArrayList<>();
        List<RenderPlanningDiagnostic> diagnostics = new ArrayList<>();

        List<MediaClip> clips = input.clips();
        RenderRequest request = input.request();

        if (clips.isEmpty() || request.outputs().isEmpty()) {
            diagnostics.add(RenderPlanningDiagnostic.diagnostic(
                    RenderPlanningDiagnosticCode.MATERIALIZATION_FAILED,
                    RenderDiagnosticSeverity.ERROR,
                    "Materialization requires at least one clip and one output requirement"));
            return new RenderMaterializationResult(nodes, edges, diagnostics);
        }

        // clipId -> DECODE node, for wiring audio/effect edges (parallel lists; avoids Map<String)
        ArrayList<String> decodeKeys = new ArrayList<>();
        ArrayList<RenderNodeId> decodeValues = new ArrayList<>();
        // clipId -> last video producer node (last effect or decode), for OUTPUT wiring
        ArrayList<String> producerKeys = new ArrayList<>();
        ArrayList<RenderNodeId> producerValues = new ArrayList<>();

        for (MediaClip clip : clips) {
            TimelineSourceBinding binding = clip.sourceBinding();
            if (!(binding instanceof MediaStreamSourceBinding mediaBinding)) {
                diagnostics.add(RenderPlanningDiagnostic.diagnostic(
                        RenderPlanningDiagnosticCode.PLANNING_UNSUPPORTED,
                        RenderDiagnosticSeverity.ERROR,
                        "Unsupported source kind (non-MediaStream) for clip " + clip.clipId()));
                continue;
            }

            // ── DECODE node ──
            RenderComponentPath decodePath = new RenderComponentPath(
                    RenderComponentKind.CLIP, List.of(clip.trackId(), clip.clipId()));
            List<RenderArtifactReference> decodeArtifacts = List.of(
                    new RenderArtifactReference.SourceArtifact(
                            mediaBinding.artifactId(), mediaBinding.contentDigest()));
            List<RenderCapabilityRequirement> decodeCaps = List.of(
                    new RenderCapabilityRequirement(RenderCapabilityId.DECODE));
            String decodeReqFp = CODEC.sha256Hex(CODEC.requirementsFingerprintCanonical(
                    decodeArtifacts, decodeCaps, List.of(), List.of()));
            RenderNodeId decodeId = RenderNodeId.of(
                    new RenderNodeKind.Decode(), decodePath, OP_DECODE, decodeReqFp);
            RenderSampleWindow decodeWindow = computeDecodeWindow(clip, request);
            RenderNode decodeNode = new RenderNode(
                    decodeId, new RenderNodeKind.Decode(), decodePath, OP_DECODE,
                    decodeArtifacts, decodeCaps, List.of(), List.of(),
                    Optional.of(decodeWindow));
            nodes.add(decodeNode);
            putEntry(decodeKeys, decodeValues, clip.clipId(), decodeId);
            putEntry(producerKeys, producerValues, clip.clipId(), decodeId);

            // ── chained EFFECT nodes for enabled video effects on this clip ──
            RenderNodeId prevProducer = decodeId;
            for (EffectInstance effect : effectsForClip(input.effects(), clip)) {
                if (!effect.enabled() || !effect.isVideoEffect()) {
                    continue;
                }
                RenderComponentPath effectPath = new RenderComponentPath(
                        RenderComponentKind.EFFECT, List.of(clip.clipId(), effect.effectInstanceId()));
                // operation key + capability = effect category canonical name (C6/C11).
                // Canonical category authority = EffectDefinition.category, resolved
                // via the definition catalog; fails closed when the definition is
                // missing (no silent default).
                EffectInstance.EffectCategory category = resolveEffectCategory(effect, input, diagnostics);
                if (category == null) {
                    continue; // diagnostic already recorded
                }
                String opKey = category.name().toLowerCase();
                RenderCapabilityId cap = effectCategoryToCapability(category);
                List<RenderCapabilityRequirement> effectCaps = List.of(
                        new RenderCapabilityRequirement(cap));
                // encode effect parameters deterministically (sorted "key=value"); uses
                // Map.Entry (allowed) — never the forbidden Map<String token (C8).
                List<String> paramEncodings = effect.parameters().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .toList();
                String effectReqFp = CODEC.sha256Hex(CODEC.requirementsFingerprintCanonical(
                        List.of(), effectCaps, List.of(), paramEncodings));
                RenderNodeId effectId = RenderNodeId.of(
                        new RenderNodeKind.Effect(), effectPath, opKey, effectReqFp);
                RenderNode effectNode = new RenderNode(
                        effectId, new RenderNodeKind.Effect(), effectPath, opKey,
                        List.of(), effectCaps, List.of(), List.of(), Optional.empty());
                nodes.add(effectNode);
                // data-flow direction: producer (data source) -> consumer (data sink)
                edges.add(new RenderDependencyEdge(prevProducer, effectId, new RenderDependency.EffectInput()));
                putEntry(producerKeys, producerValues, clip.clipId(), effectId);
                prevProducer = effectId;
            }
        }

        // ── Audio nodes (from AudioMix) ──
        AudioMix audioMix = input.audioMix();
        List<RenderNodeId> audioProcessNodes = new ArrayList<>();
        if (audioMix != null && !audioMix.routes().isEmpty()) {
            for (AudioRoute route : audioMix.routes()) {
                AudioMixInput mixInput = route.input();
                RenderComponentPath audioPath = new RenderComponentPath(
                        RenderComponentKind.AUDIO_ROUTE, List.of(mixInput.trackId(), mixInput.clipId()));
                List<RenderCapabilityRequirement> audioCaps = List.of(
                        new RenderCapabilityRequirement(RenderCapabilityId.AUDIO_PROCESS));
                // typed gain/mute/balance participate in the node's requirement
                // fingerprint (C12): a gain/mute/balance change changes node
                // identity and plan fingerprint (C6/C24). Sorted key=value, exact.
                List<String> audioParamEncodings = List.of(
                        "gain=" + route.gain().linear(),
                        "mute=" + route.mute().muted(),
                        "balance=" + route.balance().value());
                String audioReqFp = CODEC.sha256Hex(CODEC.requirementsFingerprintCanonical(
                        List.of(), audioCaps, List.of(), audioParamEncodings));
                RenderNodeId audioId = RenderNodeId.of(
                        new RenderNodeKind.AudioProcess(), audioPath, OP_GAIN, audioReqFp);
                RenderNode audioNode = new RenderNode(
                        audioId, new RenderNodeKind.AudioProcess(), audioPath, OP_GAIN,
                        List.of(), audioCaps, List.of(), List.of(), Optional.empty());
                nodes.add(audioNode);
                audioProcessNodes.add(audioId);

                // AUDIO_PROCESS --AudioInput--> DECODE node of the matching clip (by clipId)
                RenderNodeId targetDecode = getEntry(decodeKeys, decodeValues, mixInput.clipId());
                if (targetDecode != null) {
                    edges.add(new RenderDependencyEdge(targetDecode, audioId,
                            new RenderDependency.AudioInput(mixInput)));
                } else {
                    diagnostics.add(RenderPlanningDiagnostic.forNode(
                            RenderPlanningDiagnosticCode.DEPENDENCY_MISSING,
                            audioId, RenderDiagnosticSeverity.ERROR,
                            "Audio route references unknown clip: " + mixInput.clipId()));
                }
            }

            // AUDIO_MIX node
            RenderComponentPath mixPath = RenderComponentPath.of(
                    RenderComponentKind.AUDIO_MIX, "master");
            List<RenderCapabilityRequirement> mixCaps = List.of(
                    new RenderCapabilityRequirement(RenderCapabilityId.MIX_AUDIO));
            String mixReqFp = CODEC.sha256Hex(CODEC.requirementsFingerprintCanonical(
                    List.of(), mixCaps, List.of(), List.of()));
            RenderNodeId mixId = RenderNodeId.of(
                    new RenderNodeKind.AudioMix(), mixPath, OP_MIX, mixReqFp);
            RenderNode mixNode = new RenderNode(
                    mixId, new RenderNodeKind.AudioMix(), mixPath, OP_MIX,
                    List.of(), mixCaps, List.of(), List.of(), Optional.empty());
            nodes.add(mixNode);
            for (RenderNodeId audioProcessId : audioProcessNodes) {
                edges.add(new RenderDependencyEdge(audioProcessId, mixId,
                        new RenderDependency.AudioInput(SYNTHETIC_MIX_INPUT)));
            }
        }

        // ── TIMED_TEXT nodes ──
        for (TextElement textElement : input.textElements()) {
            RenderComponentPath textPath = RenderComponentPath.of(
                    RenderComponentKind.TEXT_ELEMENT, textElement.id().value());
            List<RenderCapabilityRequirement> textCaps = List.of(
                    new RenderCapabilityRequirement(RenderCapabilityId.RASTERIZE_TIMED_TEXT));
            String textReqFp = CODEC.sha256Hex(CODEC.requirementsFingerprintCanonical(
                    List.of(), textCaps, List.of(), List.of()));
            RenderNodeId textId = RenderNodeId.of(
                    new RenderNodeKind.TimedText(), textPath, OP_RASTER, textReqFp);
            RenderNode textNode = new RenderNode(
                    textId, new RenderNodeKind.TimedText(), textPath, OP_RASTER,
                    List.of(), textCaps, List.of(), List.of(), Optional.empty());
            nodes.add(textNode);
            // No incoming edge in this slice (SUBTITLE_RASTER reserved)
        }

        // ── OUTPUT node ──
        RenderComponentPath outputPath = RenderComponentPath.of(RenderComponentKind.OUTPUT, "master");
        List<RenderCapabilityRequirement> outputCaps = List.of(
                new RenderCapabilityRequirement(RenderCapabilityId.OUTPUT_ENCODE));
        List<RenderArtifactReference> outputArtifacts = new ArrayList<>();
        for (RenderOutputRequirement output : request.outputs()) {
            outputArtifacts.add(new RenderArtifactReference.FinalArtifactExpectation(output.role()));
        }
        String outputReqFp = CODEC.sha256Hex(CODEC.requirementsFingerprintCanonical(
                outputArtifacts, outputCaps, List.copyOf(request.outputs()), List.of()));
        RenderNodeId outputId = RenderNodeId.of(
                new RenderNodeKind.Output(), outputPath, OP_ENCODE, outputReqFp);
        RenderNode outputNode = new RenderNode(
                outputId, new RenderNodeKind.Output(), outputPath, OP_ENCODE,
                outputArtifacts, outputCaps, List.copyOf(request.outputs()), List.of(), Optional.empty());
        nodes.add(outputNode);

        // OUTPUT --EffectInput--> final video producer of the primary (first) clip
        MediaClip primaryClip = clips.get(0);
        RenderNodeId primaryProducer = getEntry(producerKeys, producerValues, primaryClip.clipId());
        if (primaryProducer != null) {
            edges.add(new RenderDependencyEdge(primaryProducer, outputId, new RenderDependency.EffectInput()));
        }
        // OUTPUT --AudioInput--> AUDIO_MIX (if audio nodes exist)
        if (audioMix != null && !audioMix.routes().isEmpty()) {
            RenderNodeId mixNodeId = findAudioMixNode(nodes);
            if (mixNodeId != null) {
                edges.add(new RenderDependencyEdge(mixNodeId, outputId,
                        new RenderDependency.AudioInput(SYNTHETIC_MIX_INPUT)));
            }
        }

        return new RenderMaterializationResult(nodes, edges, diagnostics);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Effects bound to a clip. EffectInstance carries no clipId, so we associate
     * by application-range overlap with the clip's timeline range (authored
     * order preserved — it is input, never re-derived).
     */
    private List<EffectInstance> effectsForClip(List<EffectInstance> effects, MediaClip clip) {
        List<EffectInstance> result = new ArrayList<>();
        for (EffectInstance effect : effects) {
            if (effect.applicationRange().overlaps(clip.timelineRange())) {
                result.add(effect);
            }
        }
        return result;
    }

    /**
     * Computes the DECODE sample window (C11): exact intersection of the clip's
     * source range with the extent-mapped source window. ConstantRate: window =
     * sourceRange clipped to the portion overlapped by the request extent. Freeze:
     * point window [pos, pos]. All exact rational — no double.
     */
    private RenderSampleWindow computeDecodeWindow(MediaClip clip, RenderRequest request) {
        FrameRate frameRate = request.extent().frameRate();
        MediaClip.TimeRange sourceRange = clip.sourceRange();
        TemporalMapping mapping = clip.temporalMapping();

        if (mapping instanceof FreezeTemporalMapping freeze) {
            MediaTime pos = freeze.sourcePosition();
            return new RenderSampleWindow(pos, pos, frameRate);
        }

        if (mapping instanceof ConstantRateTemporalMapping constantRate) {
            // timeline portion overlapped by the request extent (half-open)
            MediaTime overlapStart = maxMediaTime(clip.timelineRange().start(), request.extent().start());
            MediaTime overlapEnd = minMediaTime(clip.timelineRange().end(), request.extent().end());
            if (overlapStart.isGreaterThanOrEqualTo(overlapEnd)) {
                // no overlap: degenerate window at the boundary
                MediaTime mapped = mapTimelineToSource(constantRate, clip, overlapStart);
                return new RenderSampleWindow(mapped, mapped, frameRate);
            }
            MediaTime srcStart = mapTimelineToSource(constantRate, clip, overlapStart);
            MediaTime srcEnd = mapTimelineToSource(constantRate, clip, overlapEnd);
            // direction does not change window magnitude (C11): order by value
            MediaTime winStart = minMediaTime(srcStart, srcEnd);
            MediaTime winEnd = maxMediaTime(srcStart, srcEnd);
            // clip to source range authority
            winStart = maxMediaTime(winStart, sourceRange.start());
            winEnd = minMediaTime(winEnd, sourceRange.end());
            return new RenderSampleWindow(winStart, winEnd, frameRate);
        }

        // unknown mapping kind: fall back to full source range
        return new RenderSampleWindow(sourceRange.start(), sourceRange.end(), frameRate);
    }

    /**
     * Maps a timeline position to source position via a constant-rate mapping
     * (C10/C11). Consumes TemporalMapping semantics without redefining them.
     */
    private MediaTime mapTimelineToSource(ConstantRateTemporalMapping mapping, MediaClip clip, MediaTime timelineTime) {
        MediaTime offset = timelineTime.subtract(clip.timelineRange().start());
        MediaClip.Rational rate = mapping.rate();
        MediaTime sourceOffset = offset.multiplyRational(rate.numerator(), rate.denominator());
        if (mapping.direction() == com.example.platform.timeline.semantics.temporal.PlaybackDirection.REVERSE) {
            return clip.sourceRange().end().subtract(sourceOffset);
        }
        return clip.sourceRange().start().add(sourceOffset);
    }

    /**
     * Resolves the authored effect category from the definition catalog (C11).
     * The canonical category authority is EffectDefinition.category; the
     * instance itself only carries definitionId. Fails closed: unknown
     * definition -> PLANNING_UNSUPPORTED diagnostic and null.
     */
    private EffectInstance.EffectCategory resolveEffectCategory(
            EffectInstance effect, RenderPlanningInput input,
            List<RenderPlanningDiagnostic> diagnostics) {
        for (EffectInstance.EffectDefinition definition : input.effectDefinitions()) {
            if (definition.definitionId().equals(effect.effectDefinitionId())) {
                return definition.category();
            }
        }
        diagnostics.add(RenderPlanningDiagnostic.forNode(
                RenderPlanningDiagnosticCode.PLANNING_UNSUPPORTED,
                RenderNodeId.of(new RenderNodeKind.Effect(),
                        new RenderComponentPath(RenderComponentKind.EFFECT,
                                List.of(effect.effectInstanceId())),
                        "effect", "missing-definition"),
                RenderDiagnosticSeverity.ERROR,
                "Effect definition not found in catalog: " + effect.effectDefinitionId()));
        return null;
    }

    private RenderCapabilityId effectCategoryToCapability(EffectInstance.EffectCategory category) {
        return switch (category) {
            case TRANSFORM -> RenderCapabilityId.EFFECT_TRANSFORM;
            case CROP -> RenderCapabilityId.EFFECT_CROP;
            case OPACITY -> RenderCapabilityId.EFFECT_OPACITY;
            case BLEND_MODE -> RenderCapabilityId.EFFECT_BLEND_MODE;
            case COLOR_ADJUSTMENT -> RenderCapabilityId.EFFECT_COLOR_ADJUSTMENT;
            case GAUSSIAN_BLUR -> RenderCapabilityId.EFFECT_GAUSSIAN_BLUR;
            case FADE -> RenderCapabilityId.EFFECT_FADE;
            // audio DSP categories are not effect nodes in this slice (C12): they
            // belong to audio processing, which the slice does not materialize.
            case GAIN, PAN, EQUALIZER, COMPRESSOR, LIMITER -> throw new IllegalArgumentException(
                    "audio DSP category is not a video effect capability: " + category);
        };
    }

    private RenderNodeId findAudioMixNode(List<RenderNode> nodes) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            if (nodes.get(i).kind() instanceof RenderNodeKind.AudioMix) {
                return nodes.get(i).id();
            }
        }
        return null;
    }

    private MediaTime maxMediaTime(MediaTime a, MediaTime b) {
        return a.isGreaterThanOrEqualTo(b) ? a : b;
    }

    private MediaTime minMediaTime(MediaTime a, MediaTime b) {
        return a.isLessThanOrEqualTo(b) ? a : b;
    }

    /** Put into a parallel-key/value list map (avoids Map<String,…> token). */
    private static void putEntry(ArrayList<String> keys, ArrayList<RenderNodeId> values, String key, RenderNodeId value) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).equals(key)) {
                values.set(i, value);
                return;
            }
        }
        keys.add(key);
        values.add(value);
    }

    /** Get from a parallel-key/value list map; null if absent. */
    private static RenderNodeId getEntry(ArrayList<String> keys, ArrayList<RenderNodeId> values, String key) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).equals(key)) {
                return values.get(i);
            }
        }
        return null;
    }
}
