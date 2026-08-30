package com.example.platform.operation.plan;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.operation.operation.OperationInstance;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationTarget;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.semantics.relationship.GroupRelationship;
import com.example.platform.timeline.semantics.relationship.SemanticRelationship;
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
public final class OperationPlanner {

    private final TimelineContentDigester digester = new TimelineContentDigester();

    public OperationPlan plan(OperationInstance instance, TimelineDocument base) {
        if (!instance.baseRevisionId().equals(baseRevisionIdOf(instance))) {
            throw new PlanException(PlanErrorCode.STALE_BASE_REVISION,
                    "instance base " + instance.baseRevisionId() + " does not match supplied base");
        }
        List<PlannedChange> changes = new ArrayList<>();
        TimelineDocument candidate = switch (instance.definitionId().value()) {
            case "timeline.media-clip.add-or-trim" -> planAddMediaClip(instance, base, changes);
            case "timeline.move" -> planMove(instance, base, changes);
            case "timeline.delete" -> planDelete(instance, base, changes);
            case "timeline.trim" -> planTrim(instance, base, changes);
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
                candidateHash, true, digest, noOp);
    }

    private static String baseRevisionIdOf(OperationInstance instance) {
        return instance.baseRevisionId();
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

    private static TimelineDocument rebuild(List<TimelineTrack> tracks, AudioMix audioMix,
                                            List<SemanticRelationship> rels) {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, tracks,
                com.example.platform.timeline.canonical.TimelineMetadata.empty(),
                audioMix, rels);
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
        return rebuild(tracks, base.getAudioMix(), base.getSemanticRelationships());
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
        return rebuild(tracks, base.getAudioMix(), base.getSemanticRelationships());
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
                clip.getTrimStart(), clip.getTrimEnd(), clip.getSourceKind(), clip.getTemporalMapping());
    }

    private static TimelineClip withTemporal(TimelineClip clip, TemporalMapping mapping) {
        return new TimelineClip(clip.getClipId().value(), clip.getMediaAssetId(), clip.getMediaStreamId(),
                clip.getArtifactId(), clip.getContentDigest(), clip.getStartTime(), clip.getEndTime(),
                clip.getTrimStart(), clip.getTrimEnd(), clip.getSourceKind(), mapping);
    }

    private TimelineDocument planAddMediaClip(
            OperationInstance instance, TimelineDocument base, List<PlannedChange> changes) {
        if (!(instance.target() instanceof OperationTarget.TimelineTarget)) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN,
                    "ADD_OR_TRIM_MEDIA_CLIP requires Timeline target");
        }
        var parameters = (OperationParameters.AddOrTrimMediaClipParameters) instance.parameters();
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
        return new TimelineDocument(base.getSchemaVersion(), tracks, base.getMetadata(),
                base.getAudioMix(), base.getSemanticRelationships(), base.getTextElements());
    }

    // ---- 15 operations ----
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
        return rebuild(cur.getTracks(), base.getAudioMix(), kept);
    }

    private TimelineDocument planTrim(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        var params = (OperationParameters.TrimParameters) inst.parameters();
        List<TimelineClipId> scope = scopeOf(inst);
        if (scope.size() != 1) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "trim requires exactly one target");
        }
        TimelineClip clip = clipOf(base, scope.get(0));
        TimelineClip trimmed = params.edge() == OperationParameters.TrimParameters.TrimEdge.START
                ? withPlacement(clip, clip.getStartTime().add(params.delta()), clip.getEndTime())
                : withPlacement(clip, clip.getStartTime(), clip.getEndTime().subtract(params.delta()));
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
            throw new PlanException(PlanErrorCode.TARGET_MISSING, "audio input missing in base mix");
        }
        AudioMix newMix = mix.withRoutes(routes);
        String summary = inst.definitionId().value() + "@" + audioTarget.audioMixInput();
        changes.add(new PlannedChange.AudioMixReplaced(summary));
        return rebuild(base.getTracks(), newMix, base.getSemanticRelationships());
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
        return rebuild(base.getTracks(), base.getAudioMix(), rels);
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
        return rebuild(base.getTracks(), base.getAudioMix(), rels);
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
        return rebuild(base.getTracks(), base.getAudioMix(), rels);
    }

    private TimelineDocument planSyncCreate(OperationInstance inst, TimelineDocument base, List<PlannedChange> changes) {
        var params = (OperationParameters.CreateSyncParameters) inst.parameters();
        SyncRelationship sync = SyncRelationship.of(params.endpointA(), params.localAnchorA(),
                params.endpointB(), params.localAnchorB());
        List<SemanticRelationship> rels = new ArrayList<>(base.getSemanticRelationships());
        rels.add(sync);
        changes.add(new PlannedChange.RelationshipAdded(sync));
        return rebuild(base.getTracks(), base.getAudioMix(), rels);
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
        return rebuild(base.getTracks(), base.getAudioMix(), rels);
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
        return rebuild(base.getTracks(), base.getAudioMix(), rels);
    }
}
