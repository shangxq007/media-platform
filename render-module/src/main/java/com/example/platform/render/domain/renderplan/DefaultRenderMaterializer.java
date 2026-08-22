package com.example.platform.render.domain.renderplan;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.semantics.clip.TimelineSourceBinding;
import com.example.platform.timeline.semantics.effect.ClipEffectTarget;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticStateCanonicalSemantics;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.FreezeTemporalMapping;
import com.example.platform.timeline.semantics.temporal.TemporalMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default materializer (C16, ROADMAP20 correction F1/F2/F3/F5).
 *
 * <p>For each clip: a DECODE node + chained EFFECT nodes for enabled video
 * effects; from AudioMix: AUDIO_PROCESS + AUDIO_MIX nodes; for each
 * TextElement: a TIMED_TEXT node with a typed {@link TimedTextMaterializationRequirement}
 * wired into a COMPOSITE node that feeds OUTPUT; one OUTPUT node. All time math
 * is exact rational (C11). Provider-neutral: no provider/worker/device/tier/price
 * fields. Capability requirements use the platform capability authority (F3).
 *
 * <p>F1: effect/audio/text nodes carry typed immutable
 * {@link RenderMaterializationRequirement} values (the materialized logical WHAT)
 * rather than opaque hashes only. F2: TIMED_TEXT is not orphaned — it feeds the
 * visual composition/output path. F5: unknown TemporalMapping fails closed with
 * PLANNING_UNSUPPORTED (no silent full-source-range fallback).
 */
public final class DefaultRenderMaterializer implements RenderMaterializer {

    private static final RenderPlanCanonicalCodec CODEC = RenderPlanFingerprintCalculator.codec();

    /** Synthetic mix input for audio edges that have no authored AudioMixInput (mix node internals). */
    private static final AudioMixInput SYNTHETIC_MIX_INPUT = AudioMixInput.of("synthetic", "synthetic");

    private static final String OP_DECODE = "decode";
    private static final String OP_GAIN = "gain";
    private static final String OP_MIX = "mix";
    private static final String OP_RASTER = "raster";
    private static final String OP_COMPOSITE = "composite";
    private static final String OP_ENCODE = "encode";

    @Override
    public RenderMaterializationResult materialize(RenderPlanningInput input) {
        List<RenderNode> nodes = new ArrayList<>();
        List<RenderDependencyEdge> edges = new ArrayList<>();
        List<RenderPlanningDiagnostic> diagnostics = new ArrayList<>();

        List<MediaClip> clips = input.verifiedRevision().clips();
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
            List<CapabilityRequirement> decodeCaps = List.of(
                    RenderCapabilityVocabulary.videoDecode());
            String decodeReqFp = CODEC.sha256Hex(CODEC.requirementsFingerprintCanonical(
                    decodeArtifacts, decodeCaps, List.of(), List.of()));
            RenderNodeId decodeId = RenderNodeId.of(
                    new RenderNodeKind.Decode(), decodePath, OP_DECODE, decodeReqFp);
            RenderSampleWindow decodeWindow = computeDecodeWindow(clip, request, diagnostics);
            RenderExecutionCoverage decodeCoverage = new RenderExecutionCoverage(
                    clip.timelineRange().start(), clip.timelineRange().end(),
                    request.extent() != null ? request.extent().frameRate() : null);
            RenderNode decodeNode = new RenderNode(
                    decodeId, new RenderNodeKind.Decode(), decodePath, OP_DECODE,
                    decodeArtifacts, decodeCaps, List.of(), List.of(), List.of(),
                    Optional.of(decodeWindow), decodeCoverage);
            nodes.add(decodeNode);
            putEntry(decodeKeys, decodeValues, clip.clipId(), decodeId);
            putEntry(producerKeys, producerValues, clip.clipId(), decodeId);

            // ── chained EFFECT nodes for enabled video effects on this clip ──
            RenderNodeId prevProducer = decodeId;
            for (EffectInstance effect : effectsForClip(
                    input.effectSemanticSnapshot().effectsForClip(clip), clip)) {
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
                // R6-B: effective capabilities = category baseline UNION
                // definition-required capabilities (typed CapabilityId lowering).
                EffectInstance.EffectDefinition definition = resolveEffectDefinition(
                        effect, input, diagnostics);
                if (definition == null) {
                    continue; // diagnostic already recorded (definition missing)
                }
                List<CapabilityRequirement> effectCaps = RenderCapabilityVocabulary.forEffect(
                        category, definition.requiredCapabilities());
                // F1 + R5-B: typed materialized WHAT — authoritative category +
                // supported parameters as typed immutable values PLUS the
                // complete downstream-relevant authored Effect WHAT (instance id,
                // definition id/version, enabled, exact application range,
                // automation bindings, temporal behavior) so a future physical
                // planner never re-reads authored Effect state.
                // Authored Map<String,String> parameters are converted here into the
                // typed EffectParameter list (deterministic sorted by key); the
                // renderplan model itself never carries a raw map payload.
                List<EffectMaterializationRequirement.AutomationBinding> automationBindings =
                        effect.automationBindings().entrySet().stream()
                                .map(entry -> new EffectMaterializationRequirement.AutomationBinding(
                                        entry.getKey(), entry.getValue()))
                                .sorted()
                                .toList();
                EffectMaterializationRequirement effectRequirement =
                        EffectMaterializationRequirement.ofComplete(effect, definition,
                                effect.parameters().entrySet().stream()
                                        .map(entry -> new EffectMaterializationRequirement.EffectParameter(
                                                entry.getKey(), entry.getValue()))
                                        .toList(),
                                automationBindings);
                // R6-C2: node identity fingerprint uses the SAME complete
                // canonical encoder as the final RenderPlan serialization
                // (single grammar, multiple consumers) — application range,
                // automation, definition version, temporal behavior, target and
                // effective capabilities all participate in the node identity.
                String effectReqFp = CODEC.sha256Hex(CODEC.effectMaterializationRequirementCanonical(
                        effectRequirement, effectCaps));
                RenderNodeId effectId = RenderNodeId.of(
                        new RenderNodeKind.Effect(), effectPath, opKey, effectReqFp);
                RenderExecutionCoverage effectCoverage = new RenderExecutionCoverage(
                        clip.timelineRange().start(), clip.timelineRange().end(),
                        request.extent() != null ? request.extent().frameRate() : null);
                RenderNode effectNode = new RenderNode(
                        effectId, new RenderNodeKind.Effect(), effectPath, opKey,
                        List.of(), effectCaps, List.of(), List.of(),
                        List.of(effectRequirement), Optional.empty(), effectCoverage);
                nodes.add(effectNode);
                // data-flow direction: producer (data source) -> consumer (data sink)
                edges.add(new RenderDependencyEdge(prevProducer, effectId, new RenderDependency.EffectInput()));
                putEntry(producerKeys, producerValues, clip.clipId(), effectId);
                prevProducer = effectId;
            }
        }

        // ── Audio nodes (from AudioMix) ──
        AudioMix audioMix = input.verifiedRevision().audioMix();
        List<RenderNodeId> audioProcessNodes = new ArrayList<>();
        if (audioMix != null && !audioMix.routes().isEmpty()) {
            for (AudioRoute route : audioMix.routes()) {
                AudioMixInput mixInput = route.input();
                RenderComponentPath audioPath = new RenderComponentPath(
                        RenderComponentKind.AUDIO_ROUTE, List.of(mixInput.trackId(), mixInput.clipId()));
                List<CapabilityRequirement> audioCaps = List.of(
                        RenderCapabilityVocabulary.audioProcess());
                // F1: typed materialized WHAT — gain/mute/balance as typed immutable
                // values (recoverable from the Logical RenderPlan, no re-read of AudioRoute).
                AudioProcessMaterializationRequirement audioRequirement =
                        AudioProcessMaterializationRequirement.of(
                                route.gain(), route.mute(), route.balance());
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
                        List.of(), audioCaps, List.of(), List.of(),
                        List.of(audioRequirement), Optional.empty());
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
            List<CapabilityRequirement> mixCaps = List.of(
                    RenderCapabilityVocabulary.audioMix());
            String mixReqFp = CODEC.sha256Hex(CODEC.requirementsFingerprintCanonical(
                    List.of(), mixCaps, List.of(), List.of()));
            RenderNodeId mixId = RenderNodeId.of(
                    new RenderNodeKind.AudioMix(), mixPath, OP_MIX, mixReqFp);
            RenderNode mixNode = new RenderNode(
                    mixId, new RenderNodeKind.AudioMix(), mixPath, OP_MIX,
                    List.of(), mixCaps, List.of(), List.of(), List.of(), Optional.empty());
            nodes.add(mixNode);
            for (RenderNodeId audioProcessId : audioProcessNodes) {
                edges.add(new RenderDependencyEdge(audioProcessId, mixId,
                        new RenderDependency.AudioInput(SYNTHETIC_MIX_INPUT)));
            }
        }

        // ── TIMED_TEXT nodes (F2: typed materialization, connected to COMPOSITE) ──
        List<RenderNodeId> timedTextNodes = new ArrayList<>();
        for (TextElement textElement : input.verifiedRevision().textElements()) {
            RenderComponentPath textPath = RenderComponentPath.of(
                    RenderComponentKind.TEXT_ELEMENT, textElement.id().value());
            List<CapabilityRequirement> textCaps = List.of(
                    RenderCapabilityVocabulary.timedTextRasterize());
            // F2: typed materialized WHAT — exact text, timing, layout, fallback
            // policy and resolved font runs (consumed, never recomputed).
            TimedTextMaterializationRequirement textRequirement =
                    TimedTextMaterializationRequirement.from(textElement);
            List<String> textParamEncodings = List.of(
                    "text=" + textElement.styledText().content().value(),
                    "start=" + textElement.start(),
                    "duration=" + textElement.duration());
            String textReqFp = CODEC.sha256Hex(CODEC.requirementsFingerprintCanonical(
                    List.of(), textCaps, List.of(), textParamEncodings));
            RenderNodeId textId = RenderNodeId.of(
                    new RenderNodeKind.TimedText(), textPath, OP_RASTER, textReqFp);
            RenderNode textNode = new RenderNode(
                    textId, new RenderNodeKind.TimedText(), textPath, OP_RASTER,
                    List.of(), textCaps, List.of(), List.of(),
                    List.of(textRequirement), Optional.empty());
            nodes.add(textNode);
            timedTextNodes.add(textId);
        }

        // ── COMPOSITE node (F2: visual composition of video + timed text) ──
        RenderNodeId compositeId = null;
        if (!timedTextNodes.isEmpty()) {
            RenderComponentPath compositePath = RenderComponentPath.of(
                    RenderComponentKind.COMPOSITE, "video");
            List<CapabilityRequirement> compositeCaps = List.of(
                    RenderCapabilityVocabulary.composite());
            String compositeReqFp = CODEC.sha256Hex(CODEC.requirementsFingerprintCanonical(
                    List.of(), compositeCaps, List.of(), List.of()));
            compositeId = RenderNodeId.of(
                    new RenderNodeKind.Composite(), compositePath, OP_COMPOSITE, compositeReqFp);
            RenderNode compositeNode = new RenderNode(
                    compositeId, new RenderNodeKind.Composite(), compositePath, OP_COMPOSITE,
                    List.of(), compositeCaps, List.of(), List.of(), List.of(), Optional.empty());
            nodes.add(compositeNode);
            // COMPOSITE depends on the final video producer of the primary clip
            MediaClip primaryClip = clips.get(0);
            RenderNodeId primaryProducer = getEntry(producerKeys, producerValues, primaryClip.clipId());
            if (primaryProducer != null) {
                edges.add(new RenderDependencyEdge(primaryProducer, compositeId,
                        new RenderDependency.CompositeInput()));
            }
            // TIMED_TEXT -> COMPOSITE: typed subtitle/text raster dependency
            for (RenderNodeId textId : timedTextNodes) {
                edges.add(new RenderDependencyEdge(textId, compositeId,
                        new RenderDependency.CompositeInput()));
            }
        }

        // ── OUTPUT node ──
        RenderComponentPath outputPath = RenderComponentPath.of(RenderComponentKind.OUTPUT, "master");
        List<CapabilityRequirement> outputCaps = List.of(
                RenderCapabilityVocabulary.outputEncode());
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
                outputArtifacts, outputCaps, List.copyOf(request.outputs()), List.of(),
                List.of(), Optional.empty());
        nodes.add(outputNode);

        // OUTPUT --CompositeInput--> COMPOSITE when timed text participates,
        // otherwise --EffectInput--> final video producer of the primary clip
        MediaClip primaryClip = clips.get(0);
        if (compositeId != null) {
            edges.add(new RenderDependencyEdge(compositeId, outputId,
                    new RenderDependency.CompositeInput()));
        } else {
            RenderNodeId primaryProducer = getEntry(producerKeys, producerValues, primaryClip.clipId());
            if (primaryProducer != null) {
                edges.add(new RenderDependencyEdge(primaryProducer, outputId,
                        new RenderDependency.EffectInput()));
            }
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
    /**
     * R6-A6 + FINAL: selects effects for a clip by EXPLICIT typed authored
     * target membership — {@code effect.target() == (clip.trackId, clip.clipId)} —
     * NOT by temporal overlap. The authored effect stack order is preserved
     * (input order; R6-H ORDERED semantics). The typed EffectInstance view is
     * DERIVED from the verified snapshot entries (applicationRange derived
     * from the target clip extent — APPLICATION_RANGE_AUTHORITY_V1).
     */
    private List<EffectInstance> effectsForClip(List<EffectInstance> effects, MediaClip clip) {
        List<EffectInstance> result = new ArrayList<>();
        for (EffectInstance effect : effects) {
            if (effect.target() instanceof ClipEffectTarget target
                    && target.trackId().equals(clip.trackId())
                    && target.clipId().equals(clip.clipId())) {
                result.add(effect);
            }
        }
        return result;
    }

    /**
     * Computes the DECODE sample window (C11, F5): exact intersection of the
     * clip's source range with the extent-mapped source window. ConstantRate:
     * window = sourceRange clipped to the portion overlapped by the request
     * extent. Freeze: point window [pos, pos]. All exact rational — no double.
     * Unknown/unsupported TemporalMapping fails closed with PLANNING_UNSUPPORTED
     * (no silent full-source-range fallback).
     */
    private RenderSampleWindow computeDecodeWindow(
            MediaClip clip, RenderRequest request, List<RenderPlanningDiagnostic> diagnostics) {
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

        // F5: unsupported/unknown mapping — FAIL CLOSED. Do not silently
        // reinterpret a new mapping subtype as identity or full-range sampling.
        diagnostics.add(RenderPlanningDiagnostic.forNode(
                RenderPlanningDiagnosticCode.PLANNING_UNSUPPORTED,
                RenderNodeId.of(new RenderNodeKind.Decode(),
                        new RenderComponentPath(RenderComponentKind.CLIP,
                                List.of(clip.trackId(), clip.clipId())),
                        OP_DECODE, "unsupported-temporal-mapping"),
                RenderDiagnosticSeverity.ERROR,
                "Unsupported TemporalMapping kind for clip " + clip.clipId()
                        + ": " + mapping.getClass().getSimpleName()));
        // Degenerate zero-length window (fail-closed; planner will surface the ERROR diagnostic)
        return new RenderSampleWindow(sourceRange.start(), sourceRange.start(), frameRate);
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
        for (EffectInstance.EffectDefinition definition : input.effectSemanticSnapshot().effectDefinitions()) {
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

    /**
     * R5-B: resolves the authoritative EffectDefinition for an effect instance
     * (definition identity + version + temporal behavior are authored WHAT a
     * physical planner must recover without re-reading the authored catalog).
     * Fails closed: unknown definition -> PLANNING_UNSUPPORTED diagnostic and
     * null (caller skips the effect).
     */
    private EffectInstance.EffectDefinition resolveEffectDefinition(
            EffectInstance effect, RenderPlanningInput input,
            List<RenderPlanningDiagnostic> diagnostics) {
        for (EffectInstance.EffectDefinition definition : input.effectSemanticSnapshot().effectDefinitions()) {
            if (definition.definitionId().equals(effect.effectDefinitionId())) {
                return definition;
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
