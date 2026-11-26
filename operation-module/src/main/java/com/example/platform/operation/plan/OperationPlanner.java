package com.example.platform.operation.plan;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.timeline.semantics.effect.VideoTransform;
import com.example.platform.timeline.semantics.effect.BasicVideoEffect;
import com.example.platform.timeline.canonicalmodel.CanonicalTransition;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.operation.operation.OperationInstance;
import com.example.platform.operation.operation.OperationDefinitionId;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTarget;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.operation.operation.TextOperationPlanner;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.semantics.relationship.AlignmentRelationship;
import com.example.platform.timeline.semantics.relationship.GroupRelationship;
import com.example.platform.timeline.semantics.relationship.SemanticRelationship;
import com.example.platform.timeline.semantics.relationship.SourceBaseline;
import com.example.platform.timeline.semantics.relationship.SyncRelationship;
import com.example.platform.timeline.semantics.selection.ResolvedScope;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.FreezeTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.semantics.temporal.TemporalMapping;
import com.example.platform.shared.time.MediaTime;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OPERATION_PLAN_TRANSACTION_MODEL_V1 (§10/§33-42): planner producing an
 * immutable OperationPlan from an OperationInstance + exact base Timeline.
 * Builds a fully materialized candidate Timeline, computes candidate hash via
 * TimelineContentDigester (single authority), classifies semantic NO_OP when
 * candidate hash == base hash, and computes the deterministic PlanDigest.
 * Never generates/apply patches; never reads mutable latest.
 */
@org.springframework.modulith.NamedInterface("plan")
public final class OperationPlanner {

    private final TimelineContentDigester digester = new TimelineContentDigester();

    public OperationPlan plan(
            OperationInstance instance, String hydratedBaseRevisionId, TimelineDocument base) {
        if (!instance.baseRevisionId().equals(hydratedBaseRevisionId)) {
            throw new PlanException(PlanErrorCode.STALE_BASE_REVISION,
                    "instance base " + instance.baseRevisionId()
                            + " does not match independently hydrated base " + hydratedBaseRevisionId);
        }
        List<PlannedChange> changes = new ArrayList<>();
        TimelineDocument candidate = switch (instance.definitionId().value()) {
            case "timeline.track.add" -> planAddTrack(instance, base, changes);
            case "timeline.clip.slide" -> planSlide(instance, base, changes);
            case "timeline.clip.slip" -> planSlip(instance, base, changes);
            case "timeline.clip.roll" -> planRollCut(instance, base, changes);
            case "timeline.clip.paste" -> planPasteClips(instance, base, changes, false);
            case "timeline.clip.ripple-insert" -> planPasteClips(instance, base, changes, true);
            case "timeline.media-clip.add" -> planAddMediaClip(instance, base, changes);
            case "timeline.move" -> planMove(instance, base, changes);
            case "timeline.clip.relocate" -> planRelocate(instance, base, changes);
            case "timeline.composition.aspect.set" -> planCompositionAspect(instance, base, changes);
            case "timeline.clip.opacity.keyframes.set" -> planOpacity(instance, base, changes);
            case "timeline.clip.transform.set" -> planVideoTransform(instance, base, changes);
            case "timeline.clip.effect.set" -> planVideoEffectSet(instance, base, changes);
            case "timeline.clip.effect.remove" -> planVideoEffectRemove(instance, base, changes);
            case "timeline.transition.cross-dissolve.create" -> planTransitionCreate(instance, base, changes);
            case "timeline.transition.cross-dissolve.update" -> planTransitionUpdate(instance, base, changes);
            case "timeline.transition.cross-dissolve.delete" -> planTransitionDelete(instance, base, changes);
            case "timeline.delete" -> planDelete(instance, base, changes);
            case "timeline.clip.ripple-delete" -> planRippleDelete(instance, base, changes);
            case "timeline.trim" -> planTrim(instance, base, changes);
            case "timeline.clip.split" -> planSplit(instance, base, changes);
            case "timeline.temporal.set-rate" -> planSetRate(instance, base, changes);
            case "timeline.temporal.set-direction" -> planSetDirection(instance, base, changes);
            case "timeline.temporal.freeze" -> planFreeze(instance, base, changes);
            case "audio.gain.set", "audio.mute.set", "audio.balance.set" -> planAudio(instance, base, changes);
            case "timeline.group.create" -> planGroupCreate(instance, base, changes);
            case "timeline.group.update-members" -> planGroupUpdate(instance, base, changes);
            case "timeline.group.remove" -> planGroupRemove(instance, base, changes);
            case "timeline.sync.create" -> planSyncCreate(instance, base, changes);
            case "timeline.sync.update-anchor" -> planSyncUpdateAnchor(instance, base, changes);
            case "timeline.sync.remove" -> planSyncRemove(instance, base, changes);
            case "timeline.alignment.create" -> planCreateAlignment(instance, base, changes);
            default -> throw new PlanException(PlanErrorCode.INVALID_PLAN,
                    "unsupported operation " + instance.definitionId());
        };
        if (!base.getAutomations().isEmpty()) {
            var targets = base.getAutomations().stream().filter(a -> a.parameterPath().equals("opacity"))
                    .map(a -> a.targetEntityId()).collect(java.util.stream.Collectors.toSet());
            for (var track : base.getTracks()) for (var clip : track.clips()) {
                if (clip.getEffects().stream().noneMatch(e -> targets.contains(e.id()))) continue;
                var samePlacement = candidate.getTracks().stream().filter(t -> t.trackId().equals(track.trackId()))
                        .flatMap(t -> t.clips().stream()).anyMatch(c -> c.getClipId().equals(clip.getClipId())
                                && c.getStartTime().equals(clip.getStartTime()) && c.getEndTime().equals(clip.getEndTime()));
                if (!samePlacement) throw new PlanException(PlanErrorCode.INVALID_PLAN,
                        "This edit would move or remove a clip with absolute opacity keys: " + clip.getClipId().value());
            }
        }
        return finish(instance, candidate, changes, List.of(), null);
    }

    /** Plans a restore from the scoped historical state resolved by the batch domain. */
    public OperationPlan planRestore(OperationInstance instance, TimelineDocument historical,
                                     String baseSemanticDigest, String sourceSemanticDigest) {
        if (!instance.definitionId().equals(com.example.platform.operation.operation.OperationDefinition.V3.RESTORE_REVISION.definitionId())
                || !(instance.parameters() instanceof OperationParameters.RestoreRevisionParameters restore)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "A resolved restore operation is required");
        }
        return finish(instance, historical, List.of(new PlannedChange.RevisionRestored(restore.sourceRevisionId())),
                List.of(baseSemanticDigest, sourceSemanticDigest), sourceSemanticDigest.equals(baseSemanticDigest));
    }

    private OperationPlan finish(OperationInstance instance, TimelineDocument candidate, List<PlannedChange> changes,
                                 List<String> semanticIdentities, Boolean semanticNoOp) {
        String candidateHash = digester.digest(candidate);
        boolean noOp = semanticNoOp == null ? candidateHash.equals(instance.baseContentHash()) : semanticNoOp;
        var digestChanges = new ArrayList<>(OperationPlanDigest.changeKeys(changes));
        digestChanges.addAll(semanticIdentities);
        String digest = OperationPlanDigest.compute(
                instance.baseRevisionId(), instance.baseContentHash(),
                instance.definitionId().value(),
                instance.version().major() + "." + instance.version().minor(),
                instance.parameterDigest(),
                targetIdentities(instance.target()),
                digestChanges,
                candidateHash);
        return new OperationPlan(OperationPlan.FORMAT_VERSION, instance.baseRevisionId(),
                instance.baseContentHash(), instance, List.copyOf(changes), candidate,
                candidateHash, true, digest, noOp, true);
    }

    /**
     * P2-5.5 text-op planning entry (Option A overload). Routes the nine
     * {@code timeline.text.*} operations to {@link TextOperationPlanner} and
     * ADOPTS that planner's plan digest (the text planner owns its digest
     * formula; it is never recomputed here). Every non-text operation delegates
     * to {@link #plan(OperationInstance, String, TimelineDocument)} unchanged.
     *
     * <p>No dynamic registry / map schema — dispatch is a static prefix check,
     * consistent with OPERATION_MODEL_FOUNDATION_V1.
     */
    public OperationPlan plan(OperationInstance instance, String hydratedBaseRevisionId,
                              TimelineDocument base, TextOperationPlanner.FontResolutionInput fontInput) {
        if (!isTextOperation(instance.definitionId())) {
            return plan(instance, hydratedBaseRevisionId, base);
        }
        if (!instance.baseRevisionId().equals(hydratedBaseRevisionId)) {
            throw new PlanException(PlanErrorCode.STALE_BASE_REVISION,
                    "instance base " + instance.baseRevisionId()
                            + " does not match independently hydrated base " + hydratedBaseRevisionId);
        }
        OperationPlan textPlan =
                new TextOperationPlanner(digester).plan(toRequest(instance), base, fontInput);
        // TEXT-OP-APPLY-NPE: the text planner emits a plan whose sourceInstance is
        // null (it only receives an OperationRequest). Re-attach the resolved
        // instance here — it is NOT an input to OperationPlanDigest, so the plan
        // digest is preserved verbatim.
        return new OperationPlan(
                textPlan.formatVersion(),
                textPlan.baseRevisionId(),
                textPlan.baseContentHash(),
                instance,
                textPlan.plannedChanges(),
                textPlan.candidateTimeline(),
                textPlan.candidateContentHash(),
                textPlan.validated(),
                textPlan.planDigest(),
                textPlan.noOp(),
                textPlan.replanRequired());
    }

    /** Static text-op predicate — id-prefix based, no registry. */
    private static boolean isTextOperation(OperationDefinitionId definitionId) {
        String value = definitionId.value();
        return value != null && value.startsWith("timeline.text.");
    }

    /** Rebuild the caller-facing request the text planner consumes from a resolved instance. */
    private static OperationRequest toRequest(OperationInstance instance) {
        OperationTargetRequest target = switch (instance.target()) {
            case OperationTarget.TimelineTarget t ->
                    new OperationTargetRequest.TimelineTargetRequest(t.timelineId());
            case OperationTarget.TextElementTarget t -> new OperationTargetRequest.TextElementTargetRequest(t.id());
            default -> throw new PlanException(PlanErrorCode.INVALID_PLAN, "Invalid text target");
        };
        return new OperationRequest(instance.definitionId(), instance.version(), target,
                instance.parameters(), instance.baseRevisionId(), instance.baseContentHash(),
                instance.invocationId());
    }

    private static List<String> targetIdentities(OperationTarget target) {
        return switch (target) {
            case OperationTarget.TimelineTarget t -> List.of("timeline:" + t.timelineId());
            case OperationTarget.ResolvedClipScopeTarget r ->
                    r.resolvedScope().resolvedClipIds().stream().map(TimelineClipId::value).toList();
            case OperationTarget.GroupTarget g -> List.of("group:" + g.groupId().value());
            case OperationTarget.TransitionTarget t -> List.of("transition:" + t.transitionId());
            case OperationTarget.SyncTarget s -> List.of("sync:" + s.syncIdentityKey());
            case OperationTarget.TextElementTarget t -> List.of("text:" + t.id().value());
            case OperationTarget.AudioTarget a -> List.of("audio:" + a.audioMixInput());
        };
    }

    // ---- helpers ----
    private static List<TimelineClipId> scopeOf(OperationInstance instance) {
        if (instance.target() instanceof OperationTarget.ResolvedClipScopeTarget r) {
            return r.resolvedScope().resolvedClipIds();
        }
        throw new PlanException(PlanErrorCode.INVALID_PLAN, "clip-scope target required");
    }

    private static TimelineDocument rebuild(TimelineDocument base, List<TimelineTrack> tracks,
                                            AudioMix audioMix, List<SemanticRelationship> rels) {
        return new TimelineDocument(base.getSchemaVersion(), tracks,
                base.getMetadata(),
                audioMix, rels,
                base.getTextElements(), base.getTransitions(), base.getAutomations());
    }

    private static TimelineDocument replaceClip(TimelineDocument base, TimelineClipId id, TimelineClip newClip) {
        List<TimelineTrack> tracks = new ArrayList<>();
        for (var track : base.getTracks()) {
            List<TimelineClip> clips = new ArrayList<>();
            for (var clip : track.clips()) {
                clips.add(clip.getClipId().equals(id) ? newClip : clip);
            }
            tracks.add(new TimelineTrack(track.trackId(), track.name(), track.type(), clips));
        }
        return rebuild(base, tracks, base.getAudioMix(), base.getSemanticRelationships());
    }

    private static TimelineDocument removeClip(TimelineDocument base, TimelineClipId id) {
        List<TimelineTrack> tracks = new ArrayList<>();
        for (var track : base.getTracks()) {
            List<TimelineClip> clips = new ArrayList<>();
            for (var clip : track.clips()) {
                if (!clip.getClipId().equals(id)) {
                    clips.add(clip);
                }
            }
            tracks.add(new TimelineTrack(track.trackId(), track.name(), track.type(), clips));
        }
        return rebuild(base, tracks, base.getAudioMix(), base.getSemanticRelationships());
    }

    private static void rejectTransitionMutation(TimelineDocument base, TimelineClipId id, String operation) {
        for (CanonicalTransition transition : base.getTransitions()) {
            if (transition.outgoingClipId().equals(id.value())
                    || transition.incomingClipId().equals(id.value())) {
                throw new PlanException(PlanErrorCode.UNSUPPORTED_TEMPORAL_STATE,
                        operation + " of a clip referenced by transition " + transition.transitionId()
                                + " requires deleting the transition first");
            }
        }
    }

    private static TimelineClip clipOf(TimelineDocument base, TimelineClipId id) {
        for (var track : base.getTracks()) {
            for (var clip : track.clips()) {
                if (clip.getClipId().equals(id)) {
                    return clip;
                }
            }
        }
        throw new PlanException(PlanErrorCode.TARGET_MISSING, "clip " + id + " missing in base");
    }

    private static TimelineClip withPlacement(TimelineClip clip, MediaTime newStart, MediaTime newEnd) {
        return withRanges(clip, newStart, newEnd, clip.getTrimStart(), clip.getTrimEnd());
    }

    private static TimelineClip withRanges(TimelineClip clip, MediaTime newStart, MediaTime newEnd,
                                           MediaTime sourceStart, MediaTime sourceEnd) {
        return new TimelineClip(clip.getClipId().value(), clip.getMediaAssetId(), clip.getMediaStreamId(),
                clip.getArtifactId(), clip.getContentDigest(), newStart, newEnd,
                sourceStart, sourceEnd, clip.getSourceKind(), clip.getTemporalMapping(),
                clip.getEffects(), clip.getTransform());
    }

    private static TimelineClip withTemporal(TimelineClip clip, TemporalMapping mapping) {
        return new TimelineClip(clip.getClipId().value(), clip.getMediaAssetId(), clip.getMediaStreamId(),
                clip.getArtifactId(), clip.getContentDigest(), clip.getStartTime(), clip.getEndTime(),
                clip.getTrimStart(), clip.getTrimEnd(), clip.getSourceKind(), mapping,
                clip.getEffects(), clip.getTransform());
    }

    private TimelineDocument planAddMediaClip(
            OperationInstance instance, TimelineDocument base, List<PlannedChange> changes) {
        if (!(instance.target() instanceof OperationTarget.TimelineTarget)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN,
                    "ADD_MEDIA_CLIP requires Timeline target");
        }
        var parameters = (OperationParameters.AddMediaClipParameters) instance.parameters();
        for (TimelineTrack track : base.getTracks()) {
            for (TimelineClip existing : track.clips()) {
                if (existing.getClipId().equals(parameters.clipId())) {
                    throw new PlanException(PlanErrorCode.PLACEMENT_CONFLICT,
                            "clip identity already exists: " + parameters.clipId().value());
                }
            }
        }

        var binding = parameters.sourceBinding();
        // Reconstruct the typed aggregate at planning time so source range,
        // placement and TemporalMapping invariants fail before preview/apply.
        var semanticClip = new com.example.platform.timeline.semantics.clip.MediaClip(
                parameters.clipId().value(), parameters.trackId(), parameters.placement(),
                binding.sourceRange(), parameters.temporalMapping(), binding);
        TimelineClip added = TimelineClip.fromSemanticClip(semanticClip);

        boolean found = false;
        List<TimelineTrack> tracks = new ArrayList<>();
        for (TimelineTrack track : base.getTracks()) {
            if (track.trackId().equals(parameters.trackId())) {
                found = true;
                List<TimelineClip> clips = new ArrayList<>(track.clips());
                clips.add(added);
                tracks.add(new TimelineTrack(track.trackId(), track.name(), track.type(), clips));
            } else {
                tracks.add(track);
            }
        }
        if (!found) {
            throw new PlanException(PlanErrorCode.TARGET_MISSING,
                    "target track missing: " + parameters.trackId());
        }
        changes.add(new PlannedChange.ClipAdded(parameters.trackId(), added));
        AudioMix mix = base.getAudioMix();
        TimelineTrack addedTrack = tracks.stream()
                .filter(track -> track.trackId().equals(parameters.trackId())).findFirst().orElseThrow();
        if (addedTrack.type() == com.example.platform.timeline.canonical.TrackType.AUDIO
                && mix.routes().stream().noneMatch(route ->
                        route.input().equals(AudioMixInput.of(parameters.trackId(), parameters.clipId().value())))) {
            List<AudioRoute> routes = new ArrayList<>(mix.routes());
            routes.add(AudioRoute.of(AudioMixInput.of(parameters.trackId(), parameters.clipId().value())));
            mix = mix.withRoutes(routes);
            changes.add(new PlannedChange.AudioMixReplaced("audio-route-added@" + parameters.clipId().value()));
        }
        return new TimelineDocument(base.getSchemaVersion(), tracks, base.getMetadata(),
                mix, base.getSemanticRelationships(), base.getTextElements(),
                base.getTransitions(), base.getAutomations());
    }

    private TimelineDocument planPasteClips(
            OperationInstance instance, TimelineDocument base, List<PlannedChange> changes, boolean ripple) {
        var parameters = (OperationParameters.PasteClipsParameters) instance.parameters();
        TimelineTrack track = base.getTracks().stream().filter(t -> t.trackId().equals(parameters.trackId()))
                .findFirst().orElseThrow(() -> new PlanException(PlanErrorCode.TARGET_MISSING, "Paste track not found"));
        var indexed = track.clips().stream().collect(Collectors.toMap(TimelineClip::getClipId, clip -> clip));
        if (ripple && parameters.sourceClipIds().size() != 1) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Ripple Insert requires one copied clip on the target track");
        }
        var sources = new ArrayList<TimelineClip>();
        for (TimelineClipId id : parameters.sourceClipIds()) {
            TimelineClip clip = indexed.get(id);
            if (clip == null) {
                throw new PlanException(PlanErrorCode.TARGET_MISSING, "Paste source is not on the copied track: " + id.value());
            }
            if (!"MEDIA_STREAM".equals(clip.getSourceKind())
                    || !(clip.getTemporalMapping() instanceof ConstantRateTemporalMapping)) {
                throw new PlanException(PlanErrorCode.UNSUPPORTED_TEMPORAL_STATE,
                        "Paste requires a pinned media-stream clip with constant playback rate: " + id.value());
            }
            if (referencesRelationship(base, id) || base.getAutomations().stream().anyMatch(curve ->
                    clip.getEffects().stream().anyMatch(effect -> curve.targetEntityId().equals(effect.id())))) {
                throw new PlanException(PlanErrorCode.INVALID_PLAN,
                        "Paste cannot reproduce group, sync, transition or automation bindings: " + id.value());
            }
            sources.add(clip);
        }
        sources.sort(Comparator.comparing(TimelineClip::getStartTime).thenComparing(c -> c.getClipId().value()));
        if (!sources.stream().map(TimelineClip::getClipId).toList().equals(parameters.sourceClipIds())) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Paste sources must be in chronological track order");
        }
        var identities = base.getTracks().stream().flatMap(t -> t.clips().stream())
                .map(TimelineClip::getClipId).collect(Collectors.toSet());
        var clips = ripple ? makeInsertionSpace(base, track, sources.getFirst(), parameters.timelineStart(), changes)
                : new ArrayList<>(track.clips());
        AudioMix mix = base.getAudioMix();
        var routes = new ArrayList<>(mix.routes());
        for (int i = 0; i < sources.size(); i++) {
            TimelineClip source = sources.get(i);
            TimelineClipId newId = parameters.newClipIds().get(i);
            if (!identities.add(newId)) {
                throw new PlanException(PlanErrorCode.PLACEMENT_CONFLICT, "Paste clip identity already exists: " + newId.value());
            }
            MediaTime start;
            MediaTime end;
            try {
                start = parameters.timelineStart().add(source.getStartTime().subtract(sources.getFirst().getStartTime()));
                end = start.add(source.getEndTime().subtract(source.getStartTime()));
            } catch (ArithmeticException invalid) {
                throw new PlanException(PlanErrorCode.INVALID_PLAN, "Paste placement exceeds the exact Timeline range");
            }
            if (clips.stream().anyMatch(clip -> start.isLessThan(clip.getEndTime()) && end.isGreaterThan(clip.getStartTime()))) {
                throw new PlanException(PlanErrorCode.PLACEMENT_CONFLICT,
                        "Pasting clip " + source.getClipId().value() + " would overlap another clip on the track");
            }
            TimelineClip added = new TimelineClip(newId.value(), source.getMediaAssetId(), source.getMediaStreamId(),
                    source.getArtifactId(), source.getContentDigest(), start, end, source.getTrimStart(), source.getTrimEnd(),
                    source.getSourceKind(), source.getTemporalMapping(), pastedEffects(source, newId), source.getTransform());
            clips.add(added);
            changes.add(new PlannedChange.ClipAdded(track.trackId(), added));
            var oldInput = AudioMixInput.of(track.trackId(), source.getClipId().value());
            mix.routes().stream().filter(route -> route.input().equals(oldInput)).findFirst().ifPresent(route -> {
                routes.add(new AudioRoute(AudioMixInput.of(track.trackId(), newId.value()),
                        route.gain(), route.mute(), route.balance(), route.dspChain()));
                changes.add(new PlannedChange.AudioMixReplaced("audio-route-pasted@" + newId.value()));
            });
        }
        List<TimelineTrack> tracks = base.getTracks().stream().map(t -> t.trackId().equals(track.trackId())
                ? new TimelineTrack(t.trackId(), t.name(), t.type(), clips) : t).toList();
        return rebuild(base, tracks, mix.withRoutes(routes), base.getSemanticRelationships());
    }

    private static List<TimelineClip> makeInsertionSpace(TimelineDocument base, TimelineTrack track, TimelineClip source,
            MediaTime position, List<PlannedChange> changes) {
        rejectEditBinding(base, source, "Ripple Insert");
        var layout = new ArrayList<TimelineClip>();
        var sorted = track.clips().stream().sorted(Comparator.comparing(TimelineClip::getStartTime)).toList();
        TimelineClip previous = null;
        try {
            MediaTime shift = source.getEndTime().subtract(source.getStartTime());
            for (var clip : sorted) {
                if (previous != null && previous.getEndTime().isGreaterThan(clip.getStartTime())) {
                    throw new PlanException(PlanErrorCode.PLACEMENT_CONFLICT, "Resolve the overlap between "
                            + previous.getClipId().value() + " and " + clip.getClipId().value() + " before ripple insertion");
                }
                previous = clip;
                if (!clip.getStartTime().isLessThan(position)) {
                    rejectEditBinding(base, clip, "Ripple Insert");
                    var moved = withPlacement(clip, clip.getStartTime().add(shift), clip.getEndTime().add(shift));
                    layout.add(moved);
                    changes.add(new PlannedChange.ClipReplaced(clip.getClipId(), moved));
                } else if (clip.getEndTime().isGreaterThan(position)) {
                    throw new PlanException(PlanErrorCode.PLACEMENT_CONFLICT, "Insertion time is inside clip "
                            + clip.getClipId().value() + ". Choose a clip boundary or gap.");
                } else {
                    layout.add(clip);
                }
            }
        } catch (ArithmeticException invalid) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Ripple Insert exceeds the exact Timeline placement range");
        }
        return layout;
    }

    private static List<TimelineClipEffect> pastedEffects(TimelineClip source, TimelineClipId newId) {
        var result = new ArrayList<TimelineClipEffect>();
        for (TimelineClipEffect effect : source.getEffects()) {
            String id = effect.id();
            if (id != null && !id.isBlank()) {
                String key = "timeline-paste-effect-v1/" + newId.value() + "/" + result.size();
                id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
            }
            result.add(new TimelineClipEffect(id, effect.effectKey(), effect.parameters()));
        }
        return result;
    }

    // ---- 17 non-text operations (split added) ----
    private TimelineDocument planSplit(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        var params = (OperationParameters.SplitParameters) inst.parameters();
        List<TimelineClipId> scope = scopeOf(inst);
        if (scope.size() != 1) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "split requires exactly one target");
        }
        TimelineClipId leftId = scope.get(0);
        rejectTransitionMutation(base, leftId, "split");
        TimelineClipId rightId = params.rightClipId();
        if (rightId.equals(leftId)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "right clip id must differ from the left clip");
        }
        TimelineTrack targetTrack = null;
        TimelineClip clip = null;
        for (TimelineTrack track : base.getTracks()) {
            for (TimelineClip candidate : track.clips()) {
                if (candidate.getClipId().equals(rightId)) {
                    throw new PlanException(PlanErrorCode.PLACEMENT_CONFLICT,
                            "right clip identity already exists: " + rightId.value());
                }
                if (candidate.getClipId().equals(leftId)) {
                    clip = candidate;
                    targetTrack = track;
                }
            }
        }
        if (clip == null || targetTrack == null) {
            throw new PlanException(PlanErrorCode.TARGET_MISSING, "clip " + leftId.value() + " missing in base");
        }
        if (!(clip.getTemporalMapping() instanceof ConstantRateTemporalMapping mapping)) {
            throw new PlanException(PlanErrorCode.UNSUPPORTED_TEMPORAL_STATE,
                    "split requires a constant-rate temporal mapping");
        }
        if (!clip.getEffects().isEmpty()) {
            throw new PlanException(PlanErrorCode.UNSUPPORTED_TEMPORAL_STATE,
                    "split of a clip with effects is not supported");
        }
        MediaTime split = params.splitTimelineTime();
        if (!split.isGreaterThan(clip.getStartTime()) || !split.isLessThan(clip.getEndTime())) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN,
                    "split time must be strictly inside the clip");
        }
        if (referencesClip(base, leftId)) {
            throw new PlanException(PlanErrorCode.UNSUPPORTED_TEMPORAL_STATE,
                    "split of a clip referenced by a relationship, transition, automation or audio route is not supported");
        }
        MediaTime offset = split.subtract(clip.getStartTime());
        MediaTime sourceDelta = offset.multiplyRational(
                mapping.rate().numerator(), mapping.rate().denominator());
        MediaTime sourceSplit = mapping.direction() == PlaybackDirection.FORWARD
                ? clip.getTrimStart().add(sourceDelta)
                : clip.getTrimEnd().subtract(sourceDelta);
        if (!sourceSplit.isGreaterThan(clip.getTrimStart()) || !sourceSplit.isLessThan(clip.getTrimEnd())) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN,
                    "split point maps outside the clip source range");
        }
        boolean forward = mapping.direction() == PlaybackDirection.FORWARD;
        MediaTime leftTrimStart = forward ? clip.getTrimStart() : sourceSplit;
        MediaTime leftTrimEnd = forward ? sourceSplit : clip.getTrimEnd();
        MediaTime rightTrimStart = forward ? sourceSplit : clip.getTrimStart();
        MediaTime rightTrimEnd = forward ? clip.getTrimEnd() : sourceSplit;
        TimelineClip left = new TimelineClip(leftId.value(), clip.getMediaAssetId(),
                clip.getMediaStreamId(), clip.getArtifactId(), clip.getContentDigest(),
                clip.getStartTime(), split, leftTrimStart, leftTrimEnd,
                clip.getSourceKind(), mapping, clip.getEffects(), clip.getTransform());
        TimelineClip right = new TimelineClip(rightId.value(), clip.getMediaAssetId(),
                clip.getMediaStreamId(), clip.getArtifactId(), clip.getContentDigest(),
                split, clip.getEndTime(), rightTrimStart, rightTrimEnd,
                clip.getSourceKind(), mapping, List.of(), clip.getTransform());
        changes.add(new PlannedChange.ClipReplaced(leftId, left));
        changes.add(new PlannedChange.ClipAdded(targetTrack.trackId(), right));
        List<TimelineTrack> tracks = new ArrayList<>();
        for (TimelineTrack track : base.getTracks()) {
            if (!track.trackId().equals(targetTrack.trackId())) {
                tracks.add(track);
                continue;
            }
            List<TimelineClip> clips = new ArrayList<>();
            for (TimelineClip existing : track.clips()) {
                if (existing.getClipId().equals(leftId)) {
                    clips.add(left);
                    clips.add(right);
                } else {
                    clips.add(existing);
                }
            }
            tracks.add(new TimelineTrack(track.trackId(), track.name(), track.type(), clips));
        }
        return rebuild(base, tracks, base.getAudioMix(), base.getSemanticRelationships());
    }

    /** Split is fail-closed when another authored entity still references the clip identity. */
    private static boolean referencesClip(TimelineDocument base, TimelineClipId clipId) {
        return referencesRelationship(base, clipId)
                || base.getAudioMix().routes().stream().anyMatch(route -> clipId.value().equals(route.input().clipId()));
    }

    private static boolean referencesRelationship(TimelineDocument base, TimelineClipId clipId) {
        for (SemanticRelationship relationship : base.getSemanticRelationships()) {
            if (relationship instanceof GroupRelationship group
                    && group.members().stream().anyMatch(member -> member.equals(clipId))) {
                return true;
            }
            if (relationship instanceof SyncRelationship sync
                    && (sync.endpointA().equals(clipId) || sync.endpointB().equals(clipId))) {
                return true;
            }
        }
        return base.referencesClip(clipId);
    }

    // ---- 16 non-text operations ----
    private TimelineDocument planAddTrack(OperationInstance instance, TimelineDocument base,
            List<PlannedChange> changes) {
        var parameters = (OperationParameters.AddTrackParameters) instance.parameters();
        if (base.getTracks().stream().anyMatch(track -> track.trackId().equals(parameters.trackId()))) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "track identity already exists");
        }
        var tracks = new ArrayList<>(base.getTracks());
        long number = tracks.stream().filter(track -> track.type() == parameters.trackType()).count() + 1;
        String name = parameters.trackType() == com.example.platform.timeline.canonical.TrackType.VIDEO
                ? "V" + number : "A" + number;
        tracks.add(new TimelineTrack(parameters.trackId(), name, parameters.trackType(), List.of()));
        changes.add(new PlannedChange.TrackAdded(parameters.trackId()));
        return rebuild(base, tracks, base.getAudioMix(), base.getSemanticRelationships());
    }

    private TimelineDocument planMove(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        var params = (OperationParameters.MoveParameters) inst.parameters();
        var scope = scopeOf(inst);
        if (scope.size() > 1) return planMultiMove(base, scope, params, changes);
        if (params.backward()) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Use an absolute destination for single-clip Move");
        }
        TimelineDocument cur = base;
        for (TimelineClipId id : scopeOf(inst)) {
            rejectTransitionMutation(base, id, "move");
            TimelineClip clip = clipOf(base, id);
            MediaTime newStart = params.absolute() ? params.delta() : clip.getStartTime().add(params.delta());
            MediaTime newEnd = params.absolute() ? params.delta().add(clip.getEndTime().subtract(clip.getStartTime()))
                    : clip.getEndTime().add(params.delta());
            TimelineClip moved = withPlacement(clip, newStart, newEnd);
            changes.add(new PlannedChange.ClipReplaced(id, moved));
            cur = replaceClip(cur, id, moved);
        }
        return cur;
    }

    private TimelineDocument planMultiMove(TimelineDocument base, List<TimelineClipId> scope,
            OperationParameters.MoveParameters params, List<PlannedChange> changes) {
        var selected = Set.copyOf(scope);
        var track = base.getTracks().stream().filter(t ->
                t.clips().stream().filter(c -> selected.contains(c.getClipId())).count() == selected.size())
                .findFirst().orElseThrow(() -> new PlanException(PlanErrorCode.INVALID_PLAN,
                        "All selected clips must be on one track"));
        if (params.absolute()) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "A multi-clip move requires a common relative offset");
        }
        if (params.delta().equals(MediaTime.ZERO)) return base;
        var layout = new ArrayList<TimelineClip>();
        for (var clip : track.clips()) {
            var id = clip.getClipId();
            if (!selected.contains(id)) { layout.add(clip); continue; }
            rejectMultiMoveBindings(base, clip);
            try {
                if (params.backward() && clip.getStartTime().isLessThan(params.delta())) {
                    throw new PlanException(PlanErrorCode.INVALID_PLAN, "Move would produce a negative start: " + id.value());
                }
                var start = params.backward() ? clip.getStartTime().subtract(params.delta()) : clip.getStartTime().add(params.delta());
                var end = params.backward() ? clip.getEndTime().subtract(params.delta()) : clip.getEndTime().add(params.delta());
                var moved = withPlacement(clip, start, end);
                layout.add(moved);
                changes.add(new PlannedChange.ClipReplaced(id, moved));
            } catch (ArithmeticException invalid) {
                throw new PlanException(PlanErrorCode.INVALID_PLAN, "Move exceeds the exact Timeline range: " + id.value());
            }
        }
        // Compare destinations to the final layout, not to the selected clips' old positions.
        try {
            for (var moved : layout) {
                if (!selected.contains(moved.getClipId())) continue;
                for (var other : layout) {
                    if (moved != other && moved.getStartTime().isLessThan(other.getEndTime())
                            && moved.getEndTime().isGreaterThan(other.getStartTime())) {
                        throw new PlanException(PlanErrorCode.PLACEMENT_CONFLICT, "Moving " + moved.getClipId().value()
                                + " would overlap " + other.getClipId().value());
                    }
                }
            }
            var tracks = base.getTracks().stream()
                    .map(t -> t == track ? new TimelineTrack(t.trackId(), t.name(), t.type(), layout) : t).toList();
            return rebuild(base, tracks, base.getAudioMix(), base.getSemanticRelationships());
        } catch (ArithmeticException invalid) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Move exceeds the exact Timeline placement range");
        }
    }

    private static void rejectMultiMoveBindings(TimelineDocument base, TimelineClip clip) {
        var id = clip.getClipId();
        // Group membership is a set, but persisted group order is not currently
        // digest-stable through the canonical reader. Do not write a grouped move.
        for (var relation : base.getSemanticRelationships()) {
            if (relation instanceof GroupRelationship group && group.members().contains(id)) {
                throw new PlanException(PlanErrorCode.INVALID_PLAN, "Multi-clip move cannot preserve group binding "
                        + group.groupId().value() + " on " + id.value());
            }
        }
        // Sync anchors are object-local. The correspondence survives a placement change.
        rejectTransitionMutation(base, id, "multi-clip move");
        if (base.getAutomations().stream().anyMatch(curve -> curve.targetEntityId().equals(id.value())
                || clip.getEffects().stream().anyMatch(effect -> curve.targetEntityId().equals(effect.id())))) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Multi-clip move cannot move automation binding for " + id.value());
        }
    }

    private TimelineDocument planRelocate(OperationInstance instance, TimelineDocument base,
            List<PlannedChange> changes) {
        var params = (OperationParameters.RelocateClipParameters) instance.parameters();
        var scope = scopeOf(instance);
        if (scope.size() != 1) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "cross-track move requires one clip");
        }
        var id = scope.getFirst();
        rejectTransitionMutation(base, id, "relocate");
        var clip = clipOf(base, id);
        var source = base.getTracks().stream()
                .filter(track -> track.clips().contains(clip)).findFirst().orElseThrow();
        MediaTime start = params.absolute() ? params.delta() : clip.getStartTime().add(params.delta());
        var moved = withPlacement(clip, start, start.add(clip.getEndTime().subtract(clip.getStartTime())));
        if (source.trackId().equals(params.destinationTrackId())) {
            changes.add(new PlannedChange.ClipReplaced(id, moved));
            return replaceClip(base, id, moved);
        }
        return moveToTrack(base, source, params.destinationTrackId(), moved, changes);
    }

    private TimelineDocument moveToTrack(TimelineDocument base, TimelineTrack source,
            String destinationId, TimelineClip moved, List<PlannedChange> changes) {
        var destination = base.getTracks().stream().filter(track -> track.trackId().equals(destinationId))
                .findFirst().orElseThrow(() -> new PlanException(PlanErrorCode.TARGET_MISSING, "destination track missing"));
        if (source.type() != destination.type()) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "destination track must have the same media type");
        }
        var id = moved.getClipId();
        var tracks = new ArrayList<>(removeClip(base, id).getTracks());
        var destinationClips = new ArrayList<>(destination.clips());
        destinationClips.add(moved);
        tracks.set(base.getTracks().indexOf(destination),
                new TimelineTrack(destinationId, destination.name(), destination.type(), destinationClips));
        changes.add(new PlannedChange.ClipRemoved(id));
        changes.add(new PlannedChange.ClipAdded(destinationId, moved));
        AudioMix mix = base.getAudioMix();
        if (mix.routes().stream().anyMatch(route -> route.input().clipId().equals(id.value()))) {
            List<AudioRoute> routes = new ArrayList<>();
            for (AudioRoute route : mix.routes()) {
                if (route.input().clipId().equals(id.value())) {
                    routes.add(new AudioRoute(AudioMixInput.of(destinationId, id.value()),
                            route.gain(), route.mute(), route.balance(), route.dspChain()));
                } else {
                    routes.add(route);
                }
            }
            mix = mix.withRoutes(routes);
            changes.add(new PlannedChange.AudioMixReplaced("audio-route-moved@" + id.value()));
        }
        return rebuild(base, tracks, mix, base.getSemanticRelationships());
    }

    private TimelineDocument planRippleDelete(OperationInstance instance, TimelineDocument base,
            List<PlannedChange> changes) {
        var selected = scopeOf(instance);
        if (selected.size() != 1) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Ripple Delete requires exactly one clip");
        }
        var id = selected.getFirst();
        var removed = clipOf(base, id);
        var track = base.getTracks().stream().filter(t -> t.clips().contains(removed)).findFirst().orElseThrow();
        rejectEditBinding(base, removed, "Ripple Delete");
        changes.add(new PlannedChange.ClipRemoved(id));
        try {
            var duration = removed.getEndTime().subtract(removed.getStartTime());
            var layout = new ArrayList<TimelineClip>();
            for (var clip : track.clips()) {
                if (clip == removed) continue;
                if (!clip.getStartTime().isLessThan(removed.getEndTime())) {
                    rejectEditBinding(base, clip, "Ripple Delete");
                    var moved = withPlacement(clip, clip.getStartTime().subtract(duration), clip.getEndTime().subtract(duration));
                    layout.add(moved);
                    changes.add(new PlannedChange.ClipReplaced(clip.getClipId(), moved));
                } else if (clip.getEndTime().isGreaterThan(removed.getStartTime())) {
                    throw rippleOverlap(id, clip.getClipId());
                } else {
                    layout.add(clip);
                }
            }
            // Confirm against the complete resulting track, using half-open intervals.
            var sorted = layout.stream().sorted(Comparator.comparing(TimelineClip::getStartTime)).toList();
            for (int i = 1; i < sorted.size(); i++) {
                var left = sorted.get(i - 1);
                var right = sorted.get(i);
                if (left.getEndTime().isGreaterThan(right.getStartTime())) {
                    throw rippleOverlap(left.getClipId(), right.getClipId());
                }
            }
            var tracks = base.getTracks().stream()
                    .map(t -> t == track ? new TimelineTrack(t.trackId(), t.name(), t.type(), layout) : t).toList();
            var mix = base.getAudioMix();
            var routes = mix.routes().stream().filter(route -> !route.input().clipId().equals(id.value())).toList();
            if (routes.size() != mix.routes().size()) {
                mix = mix.withRoutes(routes);
                changes.add(new PlannedChange.AudioMixReplaced("audio-route-removed"));
            }
            return rebuild(base, tracks, mix, base.getSemanticRelationships());
        } catch (ArithmeticException invalid) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Ripple Delete exceeds the exact Timeline placement range");
        }
    }

    private static PlanException rippleOverlap(TimelineClipId first, TimelineClipId second) {
        return new PlanException(PlanErrorCode.PLACEMENT_CONFLICT, "Ripple Delete cannot resolve overlapping clips "
                + first.value() + " and " + second.value() + ". Remove the overlap before editing.");
    }

    private static void rejectEditBinding(TimelineDocument base, TimelineClip clip, String operation) {
        var id = clip.getClipId();
        for (var relation : base.getSemanticRelationships()) {
            if ((relation instanceof GroupRelationship group && group.members().contains(id))
                    || (relation instanceof SyncRelationship sync && (sync.endpointA().equals(id) || sync.endpointB().equals(id)))
                    || (relation instanceof AlignmentRelationship alignment
                        && (alignment.sourceUnits().stream().anyMatch(unit -> unit.value().equals(id.value()))
                            || alignment.derivedUnits().stream().anyMatch(unit -> unit.value().equals(id.value()))))) {
                String key = switch (relation) {
                    case GroupRelationship group -> "group " + group.groupId().value();
                    case SyncRelationship sync -> sync.identityKey();
                    case AlignmentRelationship alignment -> alignment.identityKey();
                };
                throw new PlanException(PlanErrorCode.INVALID_PLAN, operation + " cannot preserve relationship "
                        + key + " on clip " + id.value() + ". Resolve the relationship before editing.");
            }
        }
        rejectTransitionMutation(base, id, operation);
        if (base.getAutomations().stream().anyMatch(curve -> curve.targetEntityId().equals(id.value())
                || clip.getEffects().stream().anyMatch(effect -> curve.targetEntityId().equals(effect.id())))) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, operation + " cannot move or remove automation for clip "
                    + id.value() + ". Resolve the automation before editing.");
        }
    }

    private TimelineDocument planDelete(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        Set<TimelineClipId> deleted = new HashSet<>(scopeOf(inst));
        for (TimelineClipId id : deleted) rejectTransitionMutation(base, id, "delete");
        TimelineDocument cur = base;
        for (TimelineClipId id : deleted) {
            changes.add(new PlannedChange.ClipRemoved(id));
            cur = removeClip(cur, id);
        }
        // secondary: relationships touching deleted clips
        List<SemanticRelationship> rels = new ArrayList<>(base.getSemanticRelationships());
        List<SemanticRelationship> kept = new ArrayList<>();
        for (SemanticRelationship rel : rels) {
            if (rel instanceof SyncRelationship s) {
                if (deleted.contains(s.endpointA()) || deleted.contains(s.endpointB())) {
                    changes.add(new PlannedChange.RelationshipRemoved(s.identityKey()));
                    continue;
                }
            } else if (rel instanceof GroupRelationship g) {
                Set<TimelineClipId> remaining = new LinkedHashSet<>(g.members());
                remaining.removeAll(deleted);
                if (remaining.size() < 2) {
                    changes.add(new PlannedChange.RelationshipRemoved("G:" + g.groupId().value()));
                    continue;
                }
                if (remaining.size() != g.members().size()) {
                    changes.add(new PlannedChange.GroupMembershipUpdated(g.groupId(), remaining));
                }
                kept.add(GroupRelationship.of(g.groupId(), new ArrayList<>(remaining)));
                continue;
            }
            kept.add(rel);
        }
        AudioMix mix = base.getAudioMix();
        if (mix.routes().stream().anyMatch(route -> deleted.stream()
                .anyMatch(id -> route.input().clipId().equals(id.value())))) {
            List<AudioRoute> keptRoutes = new ArrayList<>();
            for (AudioRoute route : mix.routes()) {
                if (deleted.stream().noneMatch(id -> route.input().clipId().equals(id.value()))) {
                    keptRoutes.add(route);
                }
            }
            mix = mix.withRoutes(keptRoutes);
            changes.add(new PlannedChange.AudioMixReplaced("audio-route-removed"));
        }
        return rebuild(base, cur.getTracks(), mix, kept);
    }

    private static void requireForwardUnitMedia(TimelineClip clip, String operation) {
        if (!"MEDIA_STREAM".equals(clip.getSourceKind()) || clip.getArtifactId() == null || clip.getContentDigest() == null
                || clip.getMediaStreamId() == null || clip.getMediaAssetId() == null
                || !ConstantRateTemporalMapping.identity().equals(clip.getTemporalMapping())
                || !clip.getTrimEnd().subtract(clip.getTrimStart()).equals(clip.getEndTime().subtract(clip.getStartTime()))) {
            throw new PlanException(PlanErrorCode.UNSUPPORTED_TEMPORAL_STATE,
                    operation + " requires pinned forward 1x media with matching source and timeline durations on " + clip.getClipId().value());
        }
    }

    private TimelineDocument planSlide(OperationInstance instance, TimelineDocument base, List<PlannedChange> changes) {
        var slide = (OperationParameters.SlideParameters) instance.parameters();
        var track = base.getTracks().stream().filter(t -> t.trackId().equals(slide.trackId())).findFirst()
                .orElseThrow(() -> new PlanException(PlanErrorCode.TARGET_MISSING, "Slide track not found"));
        var middle = track.clips().stream().filter(c -> c.getClipId().equals(slide.middleClipId())).findFirst()
                .orElseThrow(() -> new PlanException(PlanErrorCode.TARGET_MISSING, "Slide middle clip not found on the selected track"));
        var left = slideNeighbor(track, middle, slide.leftClipId(), true);
        var right = slideNeighbor(track, middle, slide.rightClipId(), false);
        var triple = List.of(left, middle, right);
        var offset = slide.offset();
        try {
            if (track.clips().stream().anyMatch(c -> !triple.contains(c) && c.getStartTime().isLessThan(right.getEndTime())
                    && c.getEndTime().isGreaterThan(left.getStartTime()))) {
                throw new PlanException(PlanErrorCode.PLACEMENT_CONFLICT, "Resolve the overlap on the slide triple before editing");
            }
            for (var clip : triple) {
                requireForwardUnitMedia(clip, "Slide");
                rejectEditBinding(base, clip, "Slide");
            }
            if (offset.magnitude().equals(MediaTime.ZERO)) return base;
            var shrinking = offset.backward() ? left : right;
            if (!offset.magnitude().isLessThan(shrinking.getEndTime().subtract(shrinking.getStartTime()))) {
                throw new PlanException(PlanErrorCode.INVALID_PLAN, "Slide must leave positive duration on " + shrinking.getClipId().value());
            }
            if (offset.backward() && offset.magnitude().isGreaterThan(right.getTrimStart())) {
                throw new PlanException(PlanErrorCode.INVALID_PLAN, "Slide exceeds the source handle before " + right.getClipId().value());
            }
            var firstCut = offset.shift(middle.getStartTime());
            var secondCut = offset.shift(middle.getEndTime());
            var newLeft = withRanges(left, left.getStartTime(), firstCut, left.getTrimStart(), offset.shift(left.getTrimEnd()));
            var newMiddle = withPlacement(middle, firstCut, secondCut);
            var newRight = withRanges(right, secondCut, right.getEndTime(), offset.shift(right.getTrimStart()), right.getTrimEnd());
            var candidate = base;
            for (var updated : List.of(newLeft, newMiddle, newRight)) {
                changes.add(new PlannedChange.ClipReplaced(updated.getClipId(), updated));
                candidate = replaceClip(candidate, updated.getClipId(), updated);
            }
            return candidate;
        } catch (ArithmeticException overflow) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Slide exceeds the exact time range");
        }
    }

    private static TimelineClip slideNeighbor(TimelineTrack track, TimelineClip middle, TimelineClipId expected, boolean left) {
        var neighbors = track.clips().stream().filter(c -> left
                ? c.getEndTime().equals(middle.getStartTime()) : c.getStartTime().equals(middle.getEndTime())).toList();
        if (neighbors.size() != 1 || !neighbors.getFirst().getClipId().equals(expected)) {
            throw new PlanException(PlanErrorCode.PLACEMENT_CONFLICT,
                    "Slide requires exactly one matching touching " + (left ? "left" : "right") + " neighbor on this track");
        }
        return neighbors.getFirst();
    }

    private TimelineDocument planSlip(OperationInstance instance, TimelineDocument base, List<PlannedChange> changes) {
        var scope = scopeOf(instance);
        if (scope.size() != 1) throw new PlanException(PlanErrorCode.INVALID_PLAN, "Slip requires one clip");
        var clip = clipOf(base, scope.getFirst());
        var slip = (OperationParameters.SlipParameters) instance.parameters();
        try {
            requireForwardUnitMedia(clip, "Slip");
            rejectEditBinding(base, clip, "Slip");
            if (slip.magnitude().equals(MediaTime.ZERO)) return base;
            if (slip.backward() && slip.magnitude().isGreaterThan(clip.getTrimStart())) {
                throw new PlanException(PlanErrorCode.INVALID_PLAN, "Slip would start before the pinned source on " + clip.getClipId().value());
            }
            var slipped = withRanges(clip, clip.getStartTime(), clip.getEndTime(),
                    slip.shift(clip.getTrimStart()), slip.shift(clip.getTrimEnd()));
            changes.add(new PlannedChange.ClipReplaced(clip.getClipId(), slipped));
            return replaceClip(base, clip.getClipId(), slipped);
        } catch (ArithmeticException overflow) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Slip exceeds the exact source-time range");
        }
    }

    private TimelineDocument planRollCut(OperationInstance instance, TimelineDocument base, List<PlannedChange> changes) {
        var roll = (OperationParameters.RollCutParameters) instance.parameters();
        var track = base.getTracks().stream().filter(t -> t.trackId().equals(roll.trackId())).findFirst()
                .orElseThrow(() -> new PlanException(PlanErrorCode.TARGET_MISSING, "Roll track not found"));
        var left = clipOf(base, roll.leftClipId());
        var right = clipOf(base, roll.rightClipId());
        if (!track.clips().contains(left) || !track.clips().contains(right) || !left.getEndTime().equals(right.getStartTime())) {
            throw new PlanException(PlanErrorCode.PLACEMENT_CONFLICT, "Select a shared cut between adjacent clips on one track");
        }
        var cut = roll.cutTime();
        if (!left.getStartTime().isLessThan(cut) || !cut.isLessThan(right.getEndTime())) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Roll must leave both clips with positive duration");
        }
        if (track.clips().stream().anyMatch(c -> c != left && c != right && c.getStartTime().isLessThan(right.getEndTime())
                && c.getEndTime().isGreaterThan(left.getStartTime()))) {
            throw new PlanException(PlanErrorCode.PLACEMENT_CONFLICT, "Resolve overlapping clips before rolling this cut");
        }
        try {
            for (var clip : List.of(left, right)) {
                requireForwardUnitMedia(clip, "Roll");
                rejectEditBinding(base, clip, "Roll");
            }
            if (cut.equals(left.getEndTime())) return base;
            MediaTime leftOut = left.getTrimStart().add(cut.subtract(left.getStartTime()));
            MediaTime rightDuration = right.getEndTime().subtract(cut);
            if (rightDuration.isGreaterThan(right.getTrimEnd())) {
                throw new PlanException(PlanErrorCode.INVALID_PLAN, "Roll exceeds the available source handle before " + right.getClipId().value());
            }
            var newLeft = withRanges(left, left.getStartTime(), cut, left.getTrimStart(), leftOut);
            var newRight = withRanges(right, cut, right.getEndTime(), right.getTrimEnd().subtract(rightDuration), right.getTrimEnd());
            changes.add(new PlannedChange.ClipReplaced(left.getClipId(), newLeft));
            changes.add(new PlannedChange.ClipReplaced(right.getClipId(), newRight));
            return replaceClip(replaceClip(base, left.getClipId(), newLeft), right.getClipId(), newRight);
        } catch (ArithmeticException invalid) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Roll exceeds the exact timeline range");
        }
    }

    private TimelineDocument planTrim(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        var params = (OperationParameters.TrimParameters) inst.parameters();
        List<TimelineClipId> scope = scopeOf(inst);
        if (scope.size() != 1) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "trim requires exactly one target");
        }
        rejectTransitionMutation(base, scope.get(0), "trim");
        TimelineClip clip = clipOf(base, scope.get(0));
        if (!(clip.getTemporalMapping() instanceof ConstantRateTemporalMapping mapping)) {
            throw new PlanException(PlanErrorCode.UNSUPPORTED_TEMPORAL_STATE,
                    "trim requires constant-rate media");
        }
        if (!params.delta().isGreaterThan(MediaTime.ZERO)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN,
                    "trim delta must be positive");
        }
        MediaTime sourceDelta = params.delta().multiplyRational(
                mapping.rate().numerator(), mapping.rate().denominator());
        if (!params.delta().isLessThan(clip.getEndTime().subtract(clip.getStartTime()))
                || !sourceDelta.isLessThan(clip.getTrimEnd().subtract(clip.getTrimStart()))) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "trim must leave a positive media range");
        }
        boolean startEdge = params.edge() == OperationParameters.TrimParameters.TrimEdge.START;
        MediaTime newStart = startEdge ? clip.getStartTime().add(params.delta()) : clip.getStartTime();
        MediaTime newEnd = startEdge ? clip.getEndTime() : clip.getEndTime().subtract(params.delta());
        MediaTime newSourceStart = clip.getTrimStart();
        MediaTime newSourceEnd = clip.getTrimEnd();
        if (mapping.direction() == PlaybackDirection.FORWARD) {
            newSourceStart = startEdge ? newSourceStart.add(sourceDelta) : newSourceStart;
            newSourceEnd = startEdge ? newSourceEnd : newSourceEnd.subtract(sourceDelta);
        } else {
            newSourceEnd = startEdge ? newSourceEnd.subtract(sourceDelta) : newSourceEnd;
            newSourceStart = startEdge ? newSourceStart : newSourceStart.add(sourceDelta);
        }
        if (!newStart.isLessThan(newEnd) || !newSourceStart.isLessThan(newSourceEnd)
                || newSourceStart.isLessThan(MediaTime.ZERO)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "trim must leave a positive media range");
        }
        TimelineClip trimmed = withRanges(clip, newStart, newEnd, newSourceStart, newSourceEnd);
        // frozen policy: trim invalidating a Sync object-local anchor => REJECT
        for (SemanticRelationship rel : base.getSemanticRelationships()) {
            if (rel instanceof SyncRelationship s) {
                if (s.endpointA().equals(scope.get(0)) && s.localAnchorA().isGreaterThan(trimmed.getEndTime())) {
                    throw new PlanException(PlanErrorCode.SYNC_ANCHOR_INVALIDATED, "trim invalidates sync anchor");
                }
                if (s.endpointB().equals(scope.get(0)) && s.localAnchorB().isGreaterThan(trimmed.getEndTime())) {
                    throw new PlanException(PlanErrorCode.SYNC_ANCHOR_INVALIDATED, "trim invalidates sync anchor");
                }
            }
        }
        changes.add(new PlannedChange.ClipReplaced(scope.get(0), trimmed));
        return replaceClip(base, scope.get(0), trimmed);
    }

    private TimelineDocument planSetRate(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        var params = (OperationParameters.SetTemporalRateParameters) inst.parameters();
        TimelineDocument cur = base;
        for (TimelineClipId id : scopeOf(inst)) {
            TimelineClip clip = clipOf(base, id);
            if (!(clip.getTemporalMapping() instanceof ConstantRateTemporalMapping current)) {
                throw new PlanException(PlanErrorCode.UNSUPPORTED_TEMPORAL_STATE, "set-rate requires ConstantRate mapping");
            }
            long num = params.rate().numerator();
            long den = params.rate().denominator();
            // preserve sourceRange: newOccupied = oldOccupied x oldRate / newRate (exact rational)
            MediaTime occupied = clip.getEndTime().subtract(clip.getStartTime());
            MediaTime newOccupied = occupied.multiplyRational(
                    current.rate().numerator() * den, current.rate().denominator() * num);
            MediaTime newEnd = clip.getStartTime().add(newOccupied);
            TimelineClip updated = withTemporal(withPlacement(clip, clip.getStartTime(), newEnd),
                    ConstantRateTemporalMapping.of(num, den, current.direction()));
            changes.add(new PlannedChange.ClipReplaced(id, updated));
            cur = replaceClip(cur, id, updated);
        }
        return cur;
    }

    private TimelineDocument planSetDirection(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        var params = (OperationParameters.SetTemporalDirectionParameters) inst.parameters();
        TimelineDocument cur = base;
        for (TimelineClipId id : scopeOf(inst)) {
            TimelineClip clip = clipOf(base, id);
            if (!(clip.getTemporalMapping() instanceof ConstantRateTemporalMapping current)) {
                throw new PlanException(PlanErrorCode.UNSUPPORTED_TEMPORAL_STATE, "set-direction requires ConstantRate mapping");
            }
            TimelineClip updated = withTemporal(clip,
                    ConstantRateTemporalMapping.of(current.rate().numerator(), current.rate().denominator(), params.direction()));
            changes.add(new PlannedChange.ClipReplaced(id, updated));
            cur = replaceClip(cur, id, updated);
        }
        return cur;
    }

    private TimelineDocument planFreeze(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        var params = (OperationParameters.FreezeParameters) inst.parameters();
        List<TimelineClipId> scope = scopeOf(inst);
        if (scope.size() != 1) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "freeze requires exactly one target");
        }
        TimelineClip clip = clipOf(base, scope.get(0));
        TimelineClip frozen = withTemporal(clip, new FreezeTemporalMapping(params.sourcePosition()));
        changes.add(new PlannedChange.ClipReplaced(scope.get(0), frozen));
        return replaceClip(base, scope.get(0), frozen);
    }

    private TimelineDocument planAudio(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        if (!(inst.target() instanceof OperationTarget.AudioTarget audioTarget)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "audio target required");
        }
        AudioMix mix = base.getAudioMix();
        List<AudioRoute> routes = new ArrayList<>();
        boolean found = false;
        for (AudioRoute route : mix.routes()) {
            if (route.input().equals(audioTarget.audioMixInput())) {
                found = true;
                routes.add(switch (inst.parameters()) {
                    case OperationParameters.AudioGainParameters g -> route.withGain(g.gain());
                    case OperationParameters.AudioMuteParameters m -> route.withMute(m.mute());
                    case OperationParameters.StereoBalanceParameters b -> route.withBalance(b.balance());
                    default -> route;
                });
            } else {
                routes.add(route);
            }
        }
        if (!found) {
            TimelineClip target = clipOf(base, TimelineClipId.of(audioTarget.audioMixInput().clipId()));
            TimelineTrack targetTrack = base.getTracks().stream()
                    .filter(track -> track.trackId().equals(audioTarget.audioMixInput().trackId()))
                    .findFirst()
                    .orElseThrow(() -> new PlanException(PlanErrorCode.TARGET_MISSING,
                            "audio target track missing: " + audioTarget.audioMixInput().trackId()));
            if (targetTrack.clips().stream().noneMatch(c -> c.getClipId().equals(target.getClipId()))) {
                throw new PlanException(PlanErrorCode.TARGET_MISSING,
                        "audio target clip is not on the target track: " + audioTarget.audioMixInput());
            }
            if (targetTrack.type() != com.example.platform.timeline.canonical.TrackType.AUDIO
                    && targetTrack.type() != com.example.platform.timeline.canonical.TrackType.VIDEO) {
                throw new PlanException(PlanErrorCode.INVALID_PLAN, "audio target requires an audio or video clip");
            }
            AudioRoute route = AudioRoute.of(audioTarget.audioMixInput());
            route = switch (inst.parameters()) {
                case OperationParameters.AudioGainParameters g -> route.withGain(g.gain());
                case OperationParameters.AudioMuteParameters m -> route.withMute(m.mute());
                case OperationParameters.StereoBalanceParameters b -> route.withBalance(b.balance());
                default -> route;
            };
            routes.add(route);
        }
        AudioMix newMix = mix.withRoutes(routes);
        String summary = inst.definitionId().value() + "@" + audioTarget.audioMixInput();
        changes.add(new PlannedChange.AudioMixReplaced(summary));
        return rebuild(base, base.getTracks(), newMix, base.getSemanticRelationships());
    }

    private TimelineDocument planCompositionAspect(OperationInstance instance, TimelineDocument base,
            List<PlannedChange> changes) {
        if (!(instance.target() instanceof OperationTarget.TimelineTarget)
                || !(instance.parameters() instanceof OperationParameters.CompositionAspectParameters params)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "A Timeline and composition aspect ratio are required");
        }
        var metadata = base.getMetadata();
        changes.add(new PlannedChange.CompositionAspectChanged(params.aspectRatio()));
        return new TimelineDocument(base.getSchemaVersion(), base.getTracks(),
                new com.example.platform.timeline.canonical.TimelineMetadata(
                        metadata.title(), metadata.description(), metadata.properties(), params.aspectRatio()),
                base.getAudioMix(), base.getSemanticRelationships(), base.getTextElements(),
                base.getTransitions(), base.getAutomations());
    }

    private TimelineDocument planOpacity(OperationInstance instance, TimelineDocument base, List<PlannedChange> changes) {
        var id = scopeOf(instance).getFirst();
        var clip = clipOf(base, id);
        if (base.getTracks().stream().noneMatch(t -> t.type() == com.example.platform.timeline.canonical.TrackType.VIDEO
                && t.clips().contains(clip)) || !"MEDIA_STREAM".equals(clip.getSourceKind())) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Opacity requires a video media clip");
        }
        var keys = ((OperationParameters.OpacityParameters) instance.parameters()).keys();
        var basic = clip.getEffects().stream().filter(e -> BasicVideoEffect.EFFECT_KEY.equals(e.effectKey())).findFirst();
        String target = basic.map(TimelineClipEffect::id).orElse("video-basic-" + id.value());
        var existing = base.getAutomations().stream().filter(a -> a.parameterPath().equals("opacity")
                && clip.getEffects().stream().anyMatch(e -> a.targetEntityId().equals(e.id()))).toList();
        try {
            keys.requireInside(clip.getStartTime(), clip.getEndTime());
            if (existing.size() > 1 || !existing.isEmpty() && !target.equals(existing.getFirst().targetEntityId())
                    || basic.isPresent() && basic.get().id() == null) {
                throw new IllegalArgumentException("The existing effect or opacity channel cannot be edited here");
            }
            if (!existing.isEmpty()) {
                com.example.platform.timeline.semantics.effect.OpacityKeys.from(existing.getFirst())
                        .requireInside(clip.getStartTime(), clip.getEndTime());
            }
        } catch (IllegalArgumentException invalid) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, invalid.getMessage());
        }
        var previous = existing.isEmpty() ? null : existing.getFirst();
        String curveId = previous == null ? "opacity-" + id.value() : previous.automationId();
        if (previous == null && base.getAutomations().stream().anyMatch(a -> a.automationId().equals(curveId))) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Opacity channel identity already in use");
        }
        var curve = keys.curve(curveId, target, previous == null ? curveId + "-1" : previous.keyframes().getFirst().keyframeId(),
                previous == null ? curveId + "-2" : previous.keyframes().getLast().keyframeId());
        if (curve.equals(previous)) return base;
        var doc = base;
        if (basic.isEmpty()) {
            var effects = new ArrayList<>(clip.getEffects());
            effects.add(new TimelineClipEffect(target, BasicVideoEffect.EFFECT_KEY, BasicVideoEffect.IDENTITY.toParameters()));
            var updated = new TimelineClip(id.value(), clip.getMediaAssetId(), clip.getMediaStreamId(), clip.getArtifactId(),
                    clip.getContentDigest(), clip.getStartTime(), clip.getEndTime(), clip.getTrimStart(), clip.getTrimEnd(),
                    clip.getSourceKind(), clip.getTemporalMapping(), effects, clip.getTransform());
            doc = replaceClip(base, id, updated);
        }
        var curves = new ArrayList<>(doc.getAutomations());
        curves.remove(previous);
        curves.add(curve);
        changes.add(new PlannedChange.OpacityKeysSet(id, curveId));
        return new TimelineDocument(doc.getSchemaVersion(), doc.getTracks(), doc.getMetadata(), doc.getAudioMix(),
                doc.getSemanticRelationships(), doc.getTextElements(), doc.getTransitions(), curves);
    }

    private TimelineDocument planVideoTransform(OperationInstance inst, TimelineDocument base,
            List<PlannedChange> changes) {
        if (!(inst.parameters() instanceof OperationParameters.VideoTransformParameters params)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "video transform parameters required");
        }
        List<TimelineClipId> scope = scopeOf(inst);
        if (scope.size() != 1) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN,
                    "video transform requires exactly one target");
        }
        TimelineClip clip = clipOf(base, scope.get(0));
        TimelineClip updated = new TimelineClip(clip.getClipId().value(), clip.getMediaAssetId(),
                clip.getMediaStreamId(), clip.getArtifactId(), clip.getContentDigest(),
                clip.getStartTime(), clip.getEndTime(), clip.getTrimStart(), clip.getTrimEnd(),
                clip.getSourceKind(), clip.getTemporalMapping(), clip.getEffects(), params.transform());
        changes.add(new PlannedChange.ClipReplaced(scope.get(0), updated));
        return replaceClip(base, scope.get(0), updated);
    }

    private TimelineDocument planVideoEffectSet(OperationInstance inst, TimelineDocument base,
            List<PlannedChange> changes) {
        if (!(inst.parameters() instanceof OperationParameters.VideoEffectParameters params)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "video effect parameters required");
        }
        List<TimelineClipId> scope = scopeOf(inst);
        if (scope.size() != 1) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "video effect requires exactly one target");
        }
        TimelineClip clip = clipOf(base, scope.get(0));
        List<TimelineClipEffect> effects = new ArrayList<>();
        for (TimelineClipEffect effect : clip.getEffects()) {
            if (!BasicVideoEffect.EFFECT_KEY.equals(effect.effectKey())) effects.add(effect);
        }
        BasicVideoEffect e = params.effect();
        effects.add(new TimelineClipEffect("video-basic-" + clip.getClipId().value(),
                BasicVideoEffect.EFFECT_KEY, e.toParameters()));
        TimelineClip updated = new TimelineClip(clip.getClipId().value(), clip.getMediaAssetId(),
                clip.getMediaStreamId(), clip.getArtifactId(), clip.getContentDigest(),
                clip.getStartTime(), clip.getEndTime(), clip.getTrimStart(), clip.getTrimEnd(),
                clip.getSourceKind(), clip.getTemporalMapping(), effects, clip.getTransform());
        changes.add(new PlannedChange.ClipReplaced(scope.get(0), updated));
        return replaceClip(base, scope.get(0), updated);
    }

    private TimelineDocument planVideoEffectRemove(OperationInstance inst, TimelineDocument base,
            List<PlannedChange> changes) {
        List<TimelineClipId> scope = scopeOf(inst);
        if (scope.size() != 1) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "video effect removal requires exactly one target");
        }
        TimelineClip clip = clipOf(base, scope.get(0));
        List<TimelineClipEffect> effects = clip.getEffects().stream()
                .filter(effect -> !BasicVideoEffect.EFFECT_KEY.equals(effect.effectKey())).toList();
        if (effects.size() == clip.getEffects().size()) return base;
        TimelineClip updated = new TimelineClip(clip.getClipId().value(), clip.getMediaAssetId(),
                clip.getMediaStreamId(), clip.getArtifactId(), clip.getContentDigest(),
                clip.getStartTime(), clip.getEndTime(), clip.getTrimStart(), clip.getTrimEnd(),
                clip.getSourceKind(), clip.getTemporalMapping(), effects, clip.getTransform());
        changes.add(new PlannedChange.ClipReplaced(scope.get(0), updated));
        return replaceClip(base, scope.get(0), updated);
    }

    private TimelineDocument planTransitionCreate(OperationInstance inst, TimelineDocument base,
            List<PlannedChange> changes) {
        if (!(inst.parameters() instanceof OperationParameters.TransitionCreateParameters params)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "transition parameters required");
        }
        List<TimelineClipId> scope = scopeOf(inst);
        if (scope.size() != 1) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "transition requires exactly one outgoing target");
        }
        TimelineClip outgoing = clipOf(base, scope.get(0));
        TimelineClip incoming = clipOf(base, TimelineClipId.of(params.incomingClipId()));
        CanonicalTransition transition = CanonicalTransition.crossDissolve(
                params.transitionId(), outgoing.getClipId().value(), incoming.getClipId().value(), params.duration());
        List<CanonicalTransition> transitions = new ArrayList<>(base.getTransitions());
        transitions.add(transition);
        changes.add(new PlannedChange.TransitionChanged(params.transitionId(), PlannedChange.TransitionChanged.Action.CREATED));
        return withTransitions(base, transitions);
    }

    private TimelineDocument planTransitionUpdate(OperationInstance inst, TimelineDocument base,
            List<PlannedChange> changes) {
        if (!(inst.parameters() instanceof OperationParameters.TransitionUpdateParameters params)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "transition update parameters required");
        }
        if (!(inst.target() instanceof OperationTarget.TransitionTarget target)
                || !target.transitionId().equals(params.transitionId())) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Transition identity mismatch");
        }
        List<CanonicalTransition> transitions = new ArrayList<>();
        boolean found = false;
        for (CanonicalTransition t : base.getTransitions()) {
            if (t.transitionId().equals(params.transitionId())) {
                if (!t.isCrossDissolve()) {
                    throw new PlanException(PlanErrorCode.INVALID_PLAN, "transition definition mismatch");
                }
                transitions.add(t.withDuration(params.duration()));
                found = true;
            } else {
                transitions.add(t);
            }
        }
        if (!found) throw new PlanException(PlanErrorCode.TARGET_MISSING,
                "transition " + params.transitionId() + " missing");
        changes.add(new PlannedChange.TransitionChanged(params.transitionId(), PlannedChange.TransitionChanged.Action.UPDATED));
        return withTransitions(base, transitions);
    }

    private TimelineDocument planTransitionDelete(OperationInstance inst, TimelineDocument base,
            List<PlannedChange> changes) {
        if (!(inst.parameters() instanceof OperationParameters.TransitionDeleteParameters params)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "transition delete parameters required");
        }
        if (!(inst.target() instanceof OperationTarget.TransitionTarget target)
                || !target.transitionId().equals(params.transitionId())) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "Transition identity mismatch");
        }
        List<CanonicalTransition> transitions = new ArrayList<>();
        boolean found = false;
        for (CanonicalTransition t : base.getTransitions()) {
            if (t.transitionId().equals(params.transitionId())) {
                found = true;
            } else {
                transitions.add(t);
            }
        }
        if (!found) throw new PlanException(PlanErrorCode.TARGET_MISSING,
                "transition " + params.transitionId() + " missing");
        changes.add(new PlannedChange.TransitionChanged(params.transitionId(), PlannedChange.TransitionChanged.Action.REMOVED));
        return withTransitions(base, transitions);
    }

    private static TimelineDocument withTransitions(TimelineDocument base,
            List<CanonicalTransition> transitions) {
        return new TimelineDocument(base.getSchemaVersion(), base.getTracks(), base.getMetadata(),
                base.getAudioMix(), base.getSemanticRelationships(), base.getTextElements(),
                transitions, base.getAutomations());
    }

    private TimelineDocument planGroupCreate(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        var params = (OperationParameters.CreateGroupParameters) inst.parameters();
        if (params.members().size() < 2) {
            throw new PlanException(PlanErrorCode.GROUP_CARDINALITY_CONFLICT, "group requires >=2 members");
        }
        GroupRelationship group = GroupRelationship.of(params.groupId(), params.members());
        List<SemanticRelationship> rels = new ArrayList<>(base.getSemanticRelationships());
        rels.add(group);
        changes.add(new PlannedChange.RelationshipAdded(group));
        return rebuild(base, base.getTracks(), base.getAudioMix(), rels);
    }

    private TimelineDocument planGroupUpdate(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        if (!(inst.target() instanceof OperationTarget.GroupTarget groupTarget)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "group target required");
        }
        var params = (OperationParameters.UpdateGroupMembershipParameters) inst.parameters();
        GroupRelationship existing = base.getSemanticRelationships().stream()
                .filter(r -> r instanceof GroupRelationship g && g.groupId().equals(groupTarget.groupId()))
                .map(r -> (GroupRelationship) r)
                .findFirst()
                .orElseThrow(() -> new PlanException(PlanErrorCode.TARGET_MISSING,
                        "group " + groupTarget.groupId() + " missing in base"));
        Set<TimelineClipId> members = new LinkedHashSet<>(existing.members());
        members.addAll(params.membersToAdd());
        members.removeAll(params.membersToRemove());
        if (members.size() < 2) {
            throw new PlanException(PlanErrorCode.GROUP_CARDINALITY_CONFLICT,
                    "update would leave group below minimum cardinality");
        }
        List<SemanticRelationship> rels = new ArrayList<>(base.getSemanticRelationships());
        rels.remove(existing);
        GroupRelationship updated = GroupRelationship.of(groupTarget.groupId(), new ArrayList<>(members));
        rels.add(updated);
        changes.add(new PlannedChange.RelationshipAdded(updated));
        return rebuild(base, base.getTracks(), base.getAudioMix(), rels);
    }

    private TimelineDocument planGroupRemove(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        if (!(inst.target() instanceof OperationTarget.GroupTarget groupTarget)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "group target required");
        }
        boolean removed = false;
        List<SemanticRelationship> rels = new ArrayList<>();
        for (SemanticRelationship rel : base.getSemanticRelationships()) {
            if (rel instanceof GroupRelationship g && g.groupId().equals(groupTarget.groupId())) {
                changes.add(new PlannedChange.RelationshipRemoved("G:" + g.groupId().value()));
                removed = true;
                continue;
            }
            rels.add(rel);
        }
        if (!removed) {
            throw new PlanException(PlanErrorCode.TARGET_MISSING, "group missing in base");
        }
        return rebuild(base, base.getTracks(), base.getAudioMix(), rels);
    }

    private TimelineDocument planSyncCreate(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        var params = (OperationParameters.CreateSyncParameters) inst.parameters();
        SyncRelationship sync = SyncRelationship.of(params.endpointA(), params.localAnchorA(),
                params.endpointB(), params.localAnchorB());
        List<SemanticRelationship> rels = new ArrayList<>(base.getSemanticRelationships());
        rels.add(sync);
        changes.add(new PlannedChange.RelationshipAdded(sync));
        return rebuild(base, base.getTracks(), base.getAudioMix(), rels);
    }

    private TimelineDocument planSyncUpdateAnchor(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        if (!(inst.target() instanceof OperationTarget.SyncTarget syncTarget)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "sync target required");
        }
        var params = (OperationParameters.UpdateSyncAnchorParameters) inst.parameters();
        List<SemanticRelationship> rels = new ArrayList<>();
        boolean found = false;
        for (SemanticRelationship rel : base.getSemanticRelationships()) {
            if (rel instanceof SyncRelationship s && s.identityKey().equals(syncTarget.syncIdentityKey())) {
                found = true;
                SyncRelationship updated = SyncRelationship.of(s.endpointA(), params.localAnchorA(),
                        s.endpointB(), params.localAnchorB());
                rels.add(updated);
                changes.add(new PlannedChange.RelationshipAdded(updated));
                continue;
            }
            rels.add(rel);
        }
        if (!found) {
            throw new PlanException(PlanErrorCode.TARGET_MISSING, "sync missing in base");
        }
        return rebuild(base, base.getTracks(), base.getAudioMix(), rels);
    }

    private TimelineDocument planSyncRemove(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        if (!(inst.target() instanceof OperationTarget.SyncTarget syncTarget)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "sync target required");
        }
        List<SemanticRelationship> rels = new ArrayList<>();
        boolean removed = false;
        for (SemanticRelationship rel : base.getSemanticRelationships()) {
            if (rel instanceof SyncRelationship s && s.identityKey().equals(syncTarget.syncIdentityKey())) {
                changes.add(new PlannedChange.RelationshipRemoved(s.identityKey()));
                removed = true;
                continue;
            }
            rels.add(rel);
        }
        if (!removed) {
            throw new PlanException(PlanErrorCode.TARGET_MISSING, "sync missing in base");
        }
        return rebuild(base, base.getTracks(), base.getAudioMix(), rels);
    }

    /**
     * L5 lineage (CREATE_ALIGNMENT): the planner materialises the caller's typed descriptor into a
     * canonical {@link AlignmentRelationship} inside the candidate Timeline. The source baseline is
     * provenance derived from the exact base revision (V1 simplification: the base document content
     * digest; source-unit-scoped digest is backlog L5-BASELINE-SOURCE-UNIT-SCOPED-DIGEST).
     */
    private TimelineDocument planCreateAlignment(
            OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        var params = (OperationParameters.CreateAlignmentParameters) inst.parameters();
        SourceBaseline baseline = new SourceBaseline(inst.baseRevisionId(), digester.digest(base));
        AlignmentRelationship rel = new AlignmentRelationship(
                params.alignmentId(), params.sourceUnits(), params.derivedUnits(), baseline, params.attributes());
        List<SemanticRelationship> rels = new ArrayList<>(base.getSemanticRelationships());
        rels.add(rel);
        changes.add(new PlannedChange.RelationshipAdded(rel));
        return rebuild(base, base.getTracks(), base.getAudioMix(), rels);
    }
}
