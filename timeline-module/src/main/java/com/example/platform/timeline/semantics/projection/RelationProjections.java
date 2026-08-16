package com.example.platform.timeline.semantics.projection;

import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.transition.TransitionInstance;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only graph projections for the timeline.
 * Each projection is a separate, relation-specific structure.
 * No merging into a single cycle check.
 */
public final class RelationProjections {

    private RelationProjections() {}

    /**
     * Containment projection: ordered forest of tracks containing clips.
     */
    public sealed interface ContainmentNode {
        record TrackNode(String trackId, int layer, List<String> childClipIds) implements ContainmentNode {}
        record ClipNode(String clipId, String parentTrackId) implements ContainmentNode {}
    }

    /**
     * Builds containment projection from a list of tracks.
     */
    public static List<ContainmentNode> buildContainment(
            List<TrackDescriptor> tracks) {
        List<ContainmentNode> result = new ArrayList<>();
        for (TrackDescriptor track : tracks) {
            result.add(new ContainmentNode.TrackNode(
                track.trackId(), track.layer(),
                new ArrayList<>(track.clipIds())));
            for (String clipId : track.clipIds()) {
                result.add(new ContainmentNode.ClipNode(clipId, track.trackId()));
            }
        }
        return result;
    }

    /**
     * Transition relation projection: typed endpoint relation.
     */
    public record TransitionRelation(
        String transitionId,
        String outgoingClipId,
        String incomingClipId,
        TransitionInstance.TransitionMediaType mediaType
    ) {}

    /**
     * Builds transition relation projection.
     */
    public static List<TransitionRelation> buildTransitionRelations(
            List<TransitionInstance> transitions) {
        return transitions.stream()
            .map(t -> new TransitionRelation(
                t.transitionId(), t.outgoingClipId(), t.incomingClipId(), t.mediaType()))
            .toList();
    }

    /**
     * Effect dependency projection: acyclic where dependency semantics require.
     */
    public record EffectDependency(
        String effectInstanceId,
        String targetClipId,
        List<String> dependsOnEffectInstanceIds
    ) {}

    /**
     * Builds effect dependency projection.
     */
    public static List<EffectDependency> buildEffectDependencies(
            List<EffectInstance> effects) {
        return effects.stream()
            .map(e -> new EffectDependency(
                e.effectInstanceId(), "", // clip ID would be resolved from context
                List.of())) // dependencies are order-based, not ID-based in v1
            .toList();
    }

    /**
     * Temporal dependency projection: constraint network, not blanket DAG.
     */
    public record TemporalConstraint(
        String sourceClipId,
        String targetClipId,
        TemporalConstraintType constraintType
    ) {}

    public enum TemporalConstraintType {
        /** Source must end before target starts. */
        END_BEFORE_START,
        /** Source and target overlap by exactly the transition duration. */
        OVERLAP_EXACT,
        /** Source and target must be contiguous (no gap, no overlap). */
        CONTIGUOUS
    }

    /**
     * Builds temporal constraint projection from clips and transitions.
     */
    public static List<TemporalConstraint> buildTemporalConstraints(
            List<MediaClip> clips,
            List<TransitionInstance> transitions) {
        List<TemporalConstraint> constraints = new ArrayList<>();
        Map<String, MediaClip> clipMap = new HashMap<>();
        for (MediaClip c : clips) clipMap.put(c.clipId(), c);

        for (TransitionInstance t : transitions) {
            constraints.add(new TemporalConstraint(
                t.outgoingClipId(), t.incomingClipId(),
                TemporalConstraintType.OVERLAP_EXACT));
        }
        return constraints;
    }

    /**
     * Reference integrity projection: typed and relation-specific.
     */
    public record ReferenceIntegrity(
        String sourceEntityId,
        String sourceEntityType,
        String referencedId,
        String referencedType,
        ReferenceType relationType
    ) {}

    public enum ReferenceType {
        CONTAINS,
        TRANSITION_ENDPOINT,
        EFFECT_TARGET,
        AUTOMATION_TARGET,
        MEDIA_REFERENCE
    }

    /**
     * Builds reference integrity projection from all timeline components.
     */
    public static List<ReferenceIntegrity> buildReferences(
            List<TrackDescriptor> tracks,
            List<MediaClip> clips,
            List<TransitionInstance> transitions,
            List<EffectInstance> effects) {
        List<ReferenceIntegrity> refs = new ArrayList<>();

        // Track contains clips
        for (TrackDescriptor t : tracks) {
            for (String clipId : t.clipIds()) {
                refs.add(new ReferenceIntegrity(
                    t.trackId(), "TRACK", clipId, "CLIP",
                    ReferenceType.CONTAINS));
            }
        }

        // Transition endpoints
        for (TransitionInstance t : transitions) {
            refs.add(new ReferenceIntegrity(
                t.transitionId(), "TRANSITION", t.outgoingClipId(), "CLIP",
                ReferenceType.TRANSITION_ENDPOINT));
            refs.add(new ReferenceIntegrity(
                t.transitionId(), "TRANSITION", t.incomingClipId(), "CLIP",
                ReferenceType.TRANSITION_ENDPOINT));
        }

        // Effect targets
        for (EffectInstance e : effects) {
            refs.add(new ReferenceIntegrity(
                e.effectInstanceId(), "EFFECT", "",
                "CLIP", ReferenceType.EFFECT_TARGET));
        }

        return refs;
    }

    /**
     * Track descriptor for projection building.
     */
    public record TrackDescriptor(String trackId, int layer, List<String> clipIds) {
        public TrackDescriptor {
            Objects.requireNonNull(trackId, "trackId");
            Objects.requireNonNull(clipIds, "clipIds");
            if (clipIds.isEmpty()) {
                clipIds = List.of();
            } else {
                clipIds = List.copyOf(clipIds);
            }
        }
    }
}
