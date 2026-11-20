package com.example.platform.operation.plan;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            case "timeline.media-clip.add" -> planAddMediaClip(instance, base, changes);
            case "timeline.move" -> planMove(instance, base, changes);
            case "timeline.clip.relocate" -> planRelocate(instance, base, changes);
            case "timeline.delete" -> planDelete(instance, base, changes);
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
        String candidateHash = digester.digest(candidate);
        boolean noOp = candidateHash.equals(instance.baseContentHash());
        String digest = OperationPlanDigest.compute(
                instance.baseRevisionId(), instance.baseContentHash(),
                instance.definitionId().value(),
                instance.version().major() + "." + instance.version().minor(),
                instance.parameterDigest(),
                targetIdentities(instance.target()),
                OperationPlanDigest.changeKeys(changes),
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
            default -> throw new PlanException(PlanErrorCode.INVALID_PLAN,
                    "text operation requires a Timeline target");
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
            case OperationTarget.SyncTarget s -> List.of("sync:" + s.syncIdentityKey());
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
        return new TimelineClip(clip.getClipId().value(), clip.getMediaAssetId(), clip.getMediaStreamId(),
                clip.getArtifactId(), clip.getContentDigest(), newStart, newEnd,
                clip.getTrimStart(), clip.getTrimEnd(), clip.getSourceKind(), clip.getTemporalMapping(),
                clip.getEffects());
    }

    private static TimelineClip withTemporal(TimelineClip clip, TemporalMapping mapping) {
        return new TimelineClip(clip.getClipId().value(), clip.getMediaAssetId(), clip.getMediaStreamId(),
                clip.getArtifactId(), clip.getContentDigest(), clip.getStartTime(), clip.getEndTime(),
                clip.getTrimStart(), clip.getTrimEnd(), clip.getSourceKind(), mapping,
                clip.getEffects());
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

    // ---- 17 non-text operations (split added) ----
    private TimelineDocument planSplit(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        var params = (OperationParameters.SplitParameters) inst.parameters();
        List<TimelineClipId> scope = scopeOf(inst);
        if (scope.size() != 1) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "split requires exactly one target");
        }
        TimelineClipId leftId = scope.get(0);
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
                clip.getSourceKind(), mapping, clip.getEffects());
        TimelineClip right = new TimelineClip(rightId.value(), clip.getMediaAssetId(),
                clip.getMediaStreamId(), clip.getArtifactId(), clip.getContentDigest(),
                split, clip.getEndTime(), rightTrimStart, rightTrimEnd,
                clip.getSourceKind(), mapping, List.of());
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
        String id = clipId.value();
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
        if (base.referencesClip(clipId)) {
            return true;
        }
        for (AudioRoute route : base.getAudioMix().routes()) {
            if (id.equals(route.input().clipId())) {
                return true;
            }
        }
        return false;
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
        TimelineDocument cur = base;
        for (TimelineClipId id : scopeOf(inst)) {
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

    private TimelineDocument planRelocate(OperationInstance instance, TimelineDocument base,
            List<PlannedChange> changes) {
        var params = (OperationParameters.RelocateClipParameters) instance.parameters();
        var scope = scopeOf(instance);
        if (scope.size() != 1) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "cross-track move requires one clip");
        }
        var id = scope.getFirst();
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

    private TimelineDocument planDelete(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        Set<TimelineClipId> deleted = new HashSet<>(scopeOf(inst));
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

    private TimelineDocument planTrim(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        var params = (OperationParameters.TrimParameters) inst.parameters();
        List<TimelineClipId> scope = scopeOf(inst);
        if (scope.size() != 1) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "trim requires exactly one target");
        }
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
        TimelineClip trimmed = new TimelineClip(
                clip.getClipId().value(), clip.getMediaAssetId(), clip.getMediaStreamId(),
                clip.getArtifactId(), clip.getContentDigest(), newStart, newEnd,
                newSourceStart, newSourceEnd, clip.getSourceKind(), mapping, clip.getEffects());
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
