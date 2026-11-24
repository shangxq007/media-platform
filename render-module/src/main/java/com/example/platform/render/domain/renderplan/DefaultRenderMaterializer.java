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
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
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

        if (clips.size() > 1 && request.extent() != null) {
            clips = clips.stream()
                    .filter(clip -> clip.timelineRange().start().isLessThan(request.extent().end())
                            && clip.timelineRange().end().isGreaterThan(request.extent().start()))
                    .sorted(java.util.Comparator.comparing((MediaClip clip) -> clip.timelineRange().start())
                            .thenComparing(MediaClip::clipId)).toList();
        }

        if (clips.isEmpty() || request.outputs().isEmpty()) {
            diagnostics.add(RenderPlanningDiagnostic.diagnostic(
                    RenderPlanningDiagnosticCode.MATERIALIZATION_FAILED,
                    RenderDiagnosticSeverity.ERROR,
                    "Materialization requires at least one clip and one output requirement"));
            return new RenderMaterializationResult(nodes, edges, diagnostics);
        }

        var revision = input.verifiedRevision();
        var raster = request.extent().raster();
        if (!revision.aspectRatio().matches(raster.width(), raster.height())) {
            diagnostics.add(RenderPlanningDiagnostic.diagnostic(
                    RenderPlanningDiagnosticCode.PLANNING_UNSUPPORTED, RenderDiagnosticSeverity.ERROR,
                    "Output raster must match the authored composition aspect ratio"));
            return new RenderMaterializationResult(nodes, edges, diagnostics);
        }
        AudioMix audioMix = revision.audioMix();
        boolean hasAudioTracks = clips.stream().anyMatch(clip ->
                revision.track(clip.trackId()).type() == com.example.platform.timeline.canonical.TrackType.AUDIO);
        boolean hasAudioRoutes = audioMix != null && !audioMix.routes().isEmpty();
        boolean hasAudio = hasAudioTracks || hasAudioRoutes;
        var dissolvesByIncoming = revision.transitions().stream().collect(
                java.util.stream.Collectors.toMap(
                        com.example.platform.timeline.canonicalmodel.CanonicalTransition::incomingClipId,
                        t -> new RenderDependency.Dissolve(t.transitionId(), t.outgoingClipId(),
                                t.incomingClipId(), t.duration())));
        boolean hasBasicEffects = clips.stream().anyMatch(clip -> clip.effect().enabled() && !clip.effect().identity());
        boolean hasTransform = clips.stream().anyMatch(clip -> !clip.transform().equals(
                com.example.platform.timeline.semantics.effect.VideoTransform.IDENTITY));
        boolean timedTracks = hasAudioTracks || hasAudioRoutes || hasTransform || hasBasicEffects || !dissolvesByIncoming.isEmpty()
                || clips.stream().map(MediaClip::trackId).distinct().count() > 1;
        boolean multiClip = clips.size() > 1 || timedTracks;
        if ((hasTransform || hasBasicEffects || !dissolvesByIncoming.isEmpty())
                && !hasExactEditorialFrameGrid(clips, request.extent())) {
            diagnostics.add(RenderPlanningDiagnostic.diagnostic(
                    RenderPlanningDiagnosticCode.PLANNING_UNSUPPORTED, RenderDiagnosticSeverity.ERROR,
                    "Editorial composition requires clip and output boundaries aligned to the output frame rate"));
            return new RenderMaterializationResult(nodes, edges, diagnostics);
        }
        if (multiClip) {
            boolean unsupported = false;
            for (MediaClip clip : clips) {
                unsupported |= !(clip.temporalMapping() instanceof ConstantRateTemporalMapping mapping
                        && mapping.direction() == PlaybackDirection.FORWARD
                        && mapping.rate().numerator() == 1 && mapping.rate().denominator() == 1);
            }
            if (unsupported) {
                diagnostics.add(RenderPlanningDiagnostic.diagnostic(
                        RenderPlanningDiagnosticCode.PLANNING_UNSUPPORTED,
                        RenderDiagnosticSeverity.ERROR,
                        "Composed media clips require forward unit rate"));
                return new RenderMaterializationResult(nodes, edges, diagnostics);
            }
        }

        // clipId -> DECODE node, for wiring audio/effect edges (parallel lists; avoids Map<String)
        ArrayList<String> decodeKeys = new ArrayList<>();
        // clipId -> typed timeline coverage range (for AUDIO_PROCESS coverage;
        // same clip timeline coordinates as the DECODE node of that clip)
        ArrayList<String> coverageKeys = new ArrayList<>();
        ArrayList<com.example.platform.shared.time.MediaTime> coverageStartValues = new ArrayList<>();
        ArrayList<com.example.platform.shared.time.MediaTime> coverageEndValues = new ArrayList<>();
        ArrayList<RenderNodeId> decodeValues = new ArrayList<>();
        // clipId -> last video producer node (last effect or decode), for OUTPUT wiring
        ArrayList<String> producerKeys = new ArrayList<>();
        ArrayList<RenderNodeId> producerValues = new ArrayList<>();
        // clipId -> separate audio-decode producer for an explicitly routed embedded video audio
        ArrayList<String> embeddedAudioKeys = new ArrayList<>();
        ArrayList<RenderNodeId> embeddedAudioValues = new ArrayList<>();

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
            List<RenderArtifactReference> decodeArtifacts = new ArrayList<>();
            decodeArtifacts.add(new RenderArtifactReference.SourceArtifact(
                    mediaBinding.artifactId(), mediaBinding.contentDigest()));
            if (multiClip) decodeArtifacts.add(new RenderArtifactReference.IntermediateArtifactExpectation(
                    new LogicalArtifactId("clip:" + clip.trackId() + ":" + clip.clipId()),
                    RenderOutputRole.DERIVED_MEDIA));
            boolean audio = revision.track(clip.trackId()).type()
                    == com.example.platform.timeline.canonical.TrackType.AUDIO;
            String decodeOperation = audio ? "audio-decode" : OP_DECODE;
            List<CapabilityRequirement> decodeCaps = List.of(audio
                    ? RenderCapabilityVocabulary.audioDecode() : RenderCapabilityVocabulary.videoDecode());
            String decodeReqFp = CODEC.sha256Hex(CODEC.requirementsFingerprintCanonical(
                    decodeArtifacts, decodeCaps, List.of(), List.of()));
            RenderNodeId decodeId = RenderNodeId.of(
                    new RenderNodeKind.Decode(), decodePath, decodeOperation, decodeReqFp);
            RenderSampleWindow decodeWindow = computeDecodeWindow(clip, request, diagnostics);
            RenderExecutionCoverage decodeCoverage = new RenderExecutionCoverage(
                    clip.timelineRange().start(), clip.timelineRange().end(),
                    request.extent() != null ? request.extent().frameRate() : null);
            RenderNode decodeNode = new RenderNode(
                    decodeId, new RenderNodeKind.Decode(), decodePath, decodeOperation,
                    decodeArtifacts, decodeCaps, List.of(), List.of(), List.of(),
                    Optional.of(decodeWindow), decodeCoverage);
            nodes.add(decodeNode);
            putEntry(decodeKeys, decodeValues, clip.clipId(), decodeId);
            coverageKeys.add(clip.clipId());
            coverageStartValues.add(clip.timelineRange().start());
            coverageEndValues.add(clip.timelineRange().end());
            putEntry(producerKeys, producerValues, clip.clipId(), decodeId);

            // Explicit embedded-audio route on a video clip: decode the same source
            // artifact through a distinct audio-decode unit so the composite has a
            // unique audio input identity. No implicit per-video-track mixing exists.
            if (!audio && routeFor(audioMix, clip.trackId(), clip.clipId()) != null) {
                RenderComponentPath embeddedPath = new RenderComponentPath(
                        RenderComponentKind.CLIP, List.of(clip.trackId(), clip.clipId(), "embedded-audio"));
                List<RenderArtifactReference> embeddedArtifacts = new ArrayList<>();
                embeddedArtifacts.add(new RenderArtifactReference.SourceArtifact(
                        mediaBinding.artifactId(), mediaBinding.contentDigest()));
                if (multiClip) embeddedArtifacts.add(new RenderArtifactReference.IntermediateArtifactExpectation(
                        new LogicalArtifactId("clip:" + clip.trackId() + ":" + clip.clipId() + ":embedded-audio"),
                        RenderOutputRole.DERIVED_MEDIA));
                List<CapabilityRequirement> embeddedCaps = List.of(RenderCapabilityVocabulary.audioDecode());
                String embeddedFp = CODEC.sha256Hex(CODEC.requirementsFingerprintCanonical(
                        embeddedArtifacts, embeddedCaps, List.of(), List.of()));
                RenderNodeId embeddedId = RenderNodeId.of(
                        new RenderNodeKind.Decode(), embeddedPath, "audio-decode", embeddedFp);
                RenderSampleWindow embeddedWindow = computeDecodeWindow(clip, request, diagnostics);
                RenderExecutionCoverage embeddedCoverage = new RenderExecutionCoverage(
                        clip.timelineRange().start(), clip.timelineRange().end(),
                        request.extent() != null ? request.extent().frameRate() : null);
                RenderNode embeddedNode = new RenderNode(
                        embeddedId, new RenderNodeKind.Decode(), embeddedPath, "audio-decode",
                        embeddedArtifacts, embeddedCaps, List.of(), List.of(), List.of(),
                        Optional.of(embeddedWindow), embeddedCoverage);
                nodes.add(embeddedNode);
                putEntry(embeddedAudioKeys, embeddedAudioValues, clip.clipId(), embeddedId);
            }

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
                List<RenderArtifactReference> effectArtifacts = multiClip
                        ? List.of(new RenderArtifactReference.IntermediateArtifactExpectation(
                                new LogicalArtifactId("effect:" + effect.effectInstanceId()),
                                RenderOutputRole.DERIVED_MEDIA)) : List.of();
                String effectCanonical = CODEC.effectMaterializationRequirementCanonical(effectRequirement, effectCaps);
                String effectReqFp = CODEC.sha256Hex(multiClip ? effectCanonical
                        + CODEC.requirementsFingerprintCanonical(effectArtifacts, effectCaps, List.of(), List.of())
                        : effectCanonical);
                RenderNodeId effectId = RenderNodeId.of(
                        new RenderNodeKind.Effect(), effectPath, opKey, effectReqFp);
                RenderExecutionCoverage effectCoverage;
                if (effectRequirement != null && effectRequirement.applicationRange() != null) {
                    // typed authored effect contribution range (may be narrower
                    // than the full clip range) — exact timeline coordinates
                    effectCoverage = new RenderExecutionCoverage(
                            effectRequirement.applicationRange().start(),
                            effectRequirement.applicationRange().end(),
                            request.extent() != null ? request.extent().frameRate() : null);
                } else {
                    effectCoverage = new RenderExecutionCoverage(
                            clip.timelineRange().start(), clip.timelineRange().end(),
                            request.extent() != null ? request.extent().frameRate() : null);
                }
                RenderNode effectNode = new RenderNode(
                        effectId, new RenderNodeKind.Effect(), effectPath, opKey,
                        effectArtifacts, effectCaps, List.of(), List.of(),
                        List.of(effectRequirement), Optional.empty(), effectCoverage);
                nodes.add(effectNode);
                // data-flow direction: producer (data source) -> consumer (data sink)
                edges.add(new RenderDependencyEdge(prevProducer, effectId, new RenderDependency.EffectInput()));
                putEntry(producerKeys, producerValues, clip.clipId(), effectId);
                prevProducer = effectId;
            }
        }

        // Audio gain/mute/balance is carried inline on TimedTrackInput for the
        // BMF multitrack composite. No parallel AUDIO_PROCESS/AUDIO_MIX execution
        // authority is materialized for a timed multitrack plan.

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
            List<RenderArtifactReference> textArtifacts = multiClip
                    ? List.of(new RenderArtifactReference.IntermediateArtifactExpectation(
                            new LogicalArtifactId("text:" + textElement.id().value()),
                            RenderOutputRole.DERIVED_MEDIA)) : List.of();
            String textReqFp = CODEC.sha256Hex(CODEC.requirementsFingerprintCanonical(
                    textArtifacts, textCaps, List.of(), textParamEncodings));
            RenderNodeId textId = RenderNodeId.of(
                    new RenderNodeKind.TimedText(), textPath, OP_RASTER, textReqFp);
            // TIMED_TEXT temporal bridge (Phase A T2): exact #20-owned projection
            // of authored FontRational timing into RenderExecutionCoverage
            // (timeline coordinates). coverage = [exact(start), exact(start+duration)].
            // Unrepresentable values FAIL CLOSED (no node, typed diagnostic).
            RenderExecutionCoverage textCoverage = null;
            var textStart = ExactTextTimelineTimeProjection.project(textElement.start());
            var textEnd = ExactTextTimelineTimeProjection.projectEnd(
                    textElement.start(), textElement.duration());
            if (!textStart.exact() || !textEnd.exact()) {
                diagnostics.add(new RenderPlanningDiagnostic(
                        RenderPlanningDiagnosticCode.PLANNING_UNSUPPORTED,
                        Optional.of(textId),
                        RenderDiagnosticSeverity.ERROR,
                        "TIMED_TEXT " + textElement.id().value()
                                + " timing not exactly representable in timeline coordinates: "
                                + (textStart.exact() ? "" : ((ExactTextTimelineTimeProjection.Overflow) textStart).reason())
                                + " " + (textEnd.exact() ? "" : ((ExactTextTimelineTimeProjection.Overflow) textEnd).reason())));
            } else {
                textCoverage = new RenderExecutionCoverage(
                        ((ExactTextTimelineTimeProjection.Projected) textStart).mediaTime(),
                        ((ExactTextTimelineTimeProjection.Projected) textEnd).mediaTime(),
                        request.extent() != null ? request.extent().frameRate() : null);
            }
            RenderNode textNode = new RenderNode(
                    textId, new RenderNodeKind.TimedText(), textPath, OP_RASTER,
                    textArtifacts, textCaps, List.of(), List.of(),
                    List.of(textRequirement), Optional.empty(), textCoverage);
            nodes.add(textNode);
            timedTextNodes.add(textId);
        }

        // ── COMPOSITE node (F2: visual composition of video + timed text) ──
        RenderNodeId compositeId = null;
        if (!timedTextNodes.isEmpty() || !clips.isEmpty()) {
            RenderComponentPath compositePath = RenderComponentPath.of(
                    RenderComponentKind.COMPOSITE, "video");
            List<CapabilityRequirement> compositeCaps = timedTracks
                    ? List.of(RenderCapabilityVocabulary.composite(), RenderCapabilityVocabulary.multitrackComposite())
                    : List.of(RenderCapabilityVocabulary.composite());
            List<RenderArtifactReference> compositeArtifacts = multiClip
                    ? List.of(new RenderArtifactReference.IntermediateArtifactExpectation(
                            new LogicalArtifactId("composite:video"), RenderOutputRole.DERIVED_MEDIA))
                    : List.of();
            String compositeReqFp = CODEC.sha256Hex(CODEC.requirementsFingerprintCanonical(
                    compositeArtifacts, compositeCaps, List.of(), List.of()));
            String compositeOperation = timedTracks ? "multitrack-composite" : OP_COMPOSITE;
            compositeId = RenderNodeId.of(
                    new RenderNodeKind.Composite(), compositePath, compositeOperation, compositeReqFp);
            RenderNode compositeNode = new RenderNode(
                    compositeId, new RenderNodeKind.Composite(), compositePath, compositeOperation,
                    compositeArtifacts, compositeCaps, List.of(), List.of(), List.of(), Optional.empty(), null); // aggregate
            nodes.add(compositeNode);
            if (multiClip) {
                for (MediaClip clip : clips) {
                    var start = maxMediaTime(clip.timelineRange().start(), request.extent().start());
                    var end = minMediaTime(clip.timelineRange().end(), request.extent().end());
                    RenderNodeId producer = getEntry(producerKeys, producerValues, clip.clipId());
                    if (producer != null && start.isLessThan(end)) {
                        boolean audio = revision.track(clip.trackId()).type()
                                == com.example.platform.timeline.canonical.TrackType.AUDIO;
                        AudioRoute route = routeFor(audioMix, clip.trackId(), clip.clipId());
                        if (timedTracks) {
                            if (audio) {
                                edges.add(new RenderDependencyEdge(producer, compositeId,
                                        new RenderDependency.TimedTrackInput(start, end,
                                                revision.layer(clip.trackId()), true,
                                                route != null ? route.gain() : null,
                                                route != null ? route.mute() : null,
                                                route != null ? route.balance() : null)));
                            } else {
                                edges.add(new RenderDependencyEdge(producer, compositeId,
                                        RenderDependency.TimedTrackInput.video(start, end,
                                                revision.layer(clip.trackId()),
                                                clip.transform(),
                                                VideoEffectSpec.from(clip.effect()),
                                                clip.timelineRange().start(), clip.timelineRange().end(),
                                                Optional.ofNullable(dissolvesByIncoming.get(clip.clipId())))));
                                RenderNodeId embedded = getEntry(embeddedAudioKeys, embeddedAudioValues, clip.clipId());
                                if (embedded != null) {
                                    edges.add(new RenderDependencyEdge(embedded, compositeId,
                                            new RenderDependency.TimedTrackInput(start, end,
                                                    revision.layer(clip.trackId()) + 1000, true,
                                                    route != null ? route.gain() : null,
                                                    route != null ? route.mute() : null,
                                                    route != null ? route.balance() : null)));
                                }
                            }
                        } else {
                            edges.add(new RenderDependencyEdge(producer, compositeId,
                                    new RenderDependency.TimedCompositeInput(start, end)));
                        }
                    }
                }
            } else {
                MediaClip primaryClip = clips.get(0);
                RenderNodeId primaryProducer = getEntry(producerKeys, producerValues, primaryClip.clipId());
                var start = maxMediaTime(primaryClip.timelineRange().start(), request.extent().start());
                var end = minMediaTime(primaryClip.timelineRange().end(), request.extent().end());
                if (primaryProducer != null && start.isLessThan(end)) {
                    edges.add(new RenderDependencyEdge(primaryProducer, compositeId,
                            new RenderDependency.TimedCompositeInput(start, end)));
                }
            }
            // TIMED_TEXT -> COMPOSITE: typed subtitle/text raster dependency
            for (RenderNodeId textId : timedTextNodes) {
                edges.add(new RenderDependencyEdge(textId, compositeId,
                        new RenderDependency.CompositeInput()));
            }
        }

        // ── OUTPUT node ──
        RenderComponentPath outputPath = RenderComponentPath.of(RenderComponentKind.OUTPUT, "master");
        String encodeOperation = hasAudio ? "encode-multitrack" : OP_ENCODE;
        List<CapabilityRequirement> outputCaps = hasAudio
                ? List.of(RenderCapabilityVocabulary.outputEncode(), RenderCapabilityVocabulary.multitrackComposite())
                : List.of(RenderCapabilityVocabulary.outputEncode());
        List<RenderArtifactReference> outputArtifacts = new ArrayList<>();
        for (RenderOutputRequirement output : request.outputs()) {
            outputArtifacts.add(new RenderArtifactReference.FinalArtifactExpectation(output.role()));
        }
        String outputReqFp = CODEC.sha256Hex(CODEC.requirementsFingerprintCanonical(
                outputArtifacts, outputCaps, List.copyOf(request.outputs()), List.of()));
        RenderNodeId outputId = RenderNodeId.of(
                new RenderNodeKind.Output(), outputPath, encodeOperation, outputReqFp);
        RenderNode outputNode = new RenderNode(
                outputId, new RenderNodeKind.Output(), outputPath, encodeOperation,
                outputArtifacts, outputCaps, List.copyOf(request.outputs()), List.of(),
                List.of(), Optional.empty(), null); // full-extent sink: never pruned by coverage
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
        // The multitrack composite owns both the video and audio mix; OUTPUT
        // consumes the composite artifact. No separate AUDIO_MIX execution node
        // may bypass the accepted BMF composite path.

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

    private static boolean hasExactEditorialFrameGrid(List<MediaClip> clips, RenderExtent extent) {
        try {
            extent.start().toFrameExact(extent.frameRate());
            extent.end().toFrameExact(extent.frameRate());
            for (MediaClip clip : clips) {
                if (clip.timelineRange().end().isGreaterThan(extent.start())
                        && clip.timelineRange().start().isLessThan(extent.end())) {
                    clip.timelineRange().start().toFrameExact(extent.frameRate());
                    clip.timelineRange().end().toFrameExact(extent.frameRate());
                }
            }
            return true;
        } catch (ArithmeticException nonIntegral) {
            return false;
        }
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
    private static AudioRoute routeFor(AudioMix mix, String trackId, String clipId) {
        if (mix == null) return null;
        for (AudioRoute route : mix.routes()) {
            if (route.input().trackId().equals(trackId) && route.input().clipId().equals(clipId)) {
                return route;
            }
        }
        return null;
    }

}
