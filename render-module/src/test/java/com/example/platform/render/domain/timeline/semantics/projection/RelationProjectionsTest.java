package com.example.platform.render.domain.timeline.semantics.projection;

import com.example.platform.render.domain.timeline.semantics.clip.MediaClip;
import com.example.platform.render.domain.timeline.semantics.effect.EffectInstance;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.render.domain.timeline.semantics.transition.TransitionInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RelationProjectionsTest {

    @Test
    @DisplayName("Containment projection builds ordered forest")
    void containmentProjection() {
        List<RelationProjections.TrackDescriptor> tracks = List.of(
            new RelationProjections.TrackDescriptor("track-1", 0, List.of("c1", "c2")),
            new RelationProjections.TrackDescriptor("track-2", 1, List.of("c3"))
        );

        var projection = RelationProjections.buildContainment(tracks);
        assertEquals(5, projection.size()); // 2 track nodes + 3 clip nodes
        assertTrue(projection.stream().anyMatch(n ->
            n instanceof RelationProjections.ContainmentNode.TrackNode t && t.trackId().equals("track-1")));
    }

    @Test
    @DisplayName("Transition relation projection contains correct endpoints")
    void transitionRelationProjection() {
        TransitionInstance tx = new TransitionInstance(
            "tx-1", "def-1", "1.0", "c1", "c2",
            TransitionInstance.TransitionMediaType.VIDEO,
            MediaTime.ofRational(1, 2),
            TransitionInstance.TransitionAlignment.CENTER_ON_CUT,
            TransitionInstance.TransitionTemporalPolicy.USE_SOURCE_HANDLES,
            null
        );

        var relations = RelationProjections.buildTransitionRelations(List.of(tx));
        assertEquals(1, relations.size());
        assertEquals("c1", relations.get(0).outgoingClipId());
        assertEquals("c2", relations.get(0).incomingClipId());
    }

    @Test
    @DisplayName("Temporal constraint projection detects overlap at transitions")
    void temporalConstraints() {
        MediaClip c1 = clip("c1", 0, 5, 0, 5);
        MediaClip c2 = clip("c2", 5, 10, 0, 5);
        TransitionInstance tx = new TransitionInstance(
            "tx-1", "def-1", "1.0", "c1", "c2",
            TransitionInstance.TransitionMediaType.VIDEO,
            MediaTime.ofRational(1, 2),
            TransitionInstance.TransitionAlignment.CENTER_ON_CUT,
            TransitionInstance.TransitionTemporalPolicy.USE_SOURCE_HANDLES,
            null
        );

        var constraints = RelationProjections.buildTemporalConstraints(
            List.of(c1, c2), List.of(tx));
        assertEquals(1, constraints.size());
        assertEquals(RelationProjections.TemporalConstraintType.OVERLAP_EXACT,
            constraints.get(0).constraintType());
    }

    @Test
    @DisplayName("Reference integrity projection contains all relations")
    void referenceIntegrity() {
        List<RelationProjections.TrackDescriptor> tracks = List.of(
            new RelationProjections.TrackDescriptor("track-1", 0, List.of("c1"))
        );
        MediaClip c1 = clip("c1", 0, 5, 0, 5);
        TransitionInstance tx = new TransitionInstance(
            "tx-1", "def-1", "1.0", "c1", "c2",
            TransitionInstance.TransitionMediaType.VIDEO,
            MediaTime.ofRational(1, 2),
            TransitionInstance.TransitionAlignment.CENTER_ON_CUT,
            TransitionInstance.TransitionTemporalPolicy.USE_SOURCE_HANDLES,
            null
        );

        var refs = RelationProjections.buildReferences(tracks, List.of(c1), List.of(tx), List.of());
        assertTrue(refs.stream().anyMatch(r -> r.relationType() == RelationProjections.ReferenceType.CONTAINS));
        assertTrue(refs.stream().anyMatch(r -> r.relationType() == RelationProjections.ReferenceType.TRANSITION_ENDPOINT));
    }

    private MediaClip clip(String id, long tStart, long tEnd, long sStart, long sEnd) {
        return new MediaClip(
            id, "track-1",
            new MediaClip.TimeRange(
                MediaTime.ofRational(tStart, 1),
                MediaTime.ofRational(tEnd, 1)),
            new MediaClip.TimeRange(
                MediaTime.ofRational(sStart, 1),
                MediaTime.ofRational(sEnd, 1)),
            new MediaClip.Rational(1, 1),
            "asset-" + id
        );
    }
}
