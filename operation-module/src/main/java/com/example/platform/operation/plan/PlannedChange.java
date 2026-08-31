package com.example.platform.operation.plan;

import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.semantics.relationship.GroupId;
import com.example.platform.timeline.semantics.relationship.SemanticRelationship;

import java.util.Set;

/**
 * OPERATION_PLAN_TRANSACTION_MODEL_V1 (PT8/§12): typed planned semantic changes
 * and secondary consequences. Deterministic, explicit affected entities,
 * previewable, primary/secondary distinguishable. No generic field paths, no
 * JsonNode, no Map<String,Object>.
 */
public sealed interface PlannedChange permits
        PlannedChange.ClipAdded,
        PlannedChange.ClipRemoved,
        PlannedChange.ClipReplaced,
        PlannedChange.RelationshipRemoved,
        PlannedChange.RelationshipAdded,
        PlannedChange.GroupMembershipUpdated,
        PlannedChange.AudioMixReplaced,
        PlannedChange.TextElementAdded,
        PlannedChange.TextElementRemoved,
        PlannedChange.TextElementReplaced {

    boolean primary();

    /** ADD_MEDIA_CLIP_V1: add one fully typed canonical clip. */
    record ClipAdded(String trackId, TimelineClip newClip) implements PlannedChange {
        public ClipAdded {
            if (trackId == null || trackId.isBlank() || newClip == null) {
                throw new IllegalArgumentException("trackId and newClip required");
            }
        }

        public boolean primary() {
            return true;
        }
    }

    /** DELETE: remove clip. */
    record ClipRemoved(TimelineClipId clipId) implements PlannedChange {
        public boolean primary() {
            return true;
        }
    }

    /** MOVE/TRIM/SET_RATE/SET_DIRECTION/FREEZE: replace clip with new canonical state. */
    record ClipReplaced(TimelineClipId clipId, TimelineClip newClip) implements PlannedChange {
        public boolean primary() {
            return true;
        }
    }

    /** DELETE secondary consequence: remove Sync/Group relationship. */
    record RelationshipRemoved(String relationshipIdentity) implements PlannedChange {
        public boolean primary() {
            return false;
        }
    }

    /** GROUP/SYNC create: add relationship. */
    record RelationshipAdded(SemanticRelationship relationship) implements PlannedChange {
        public boolean primary() {
            return true;
        }
    }

    /** DELETE secondary: group membership updated (members after deletion). */
    record GroupMembershipUpdated(GroupId groupId, Set<TimelineClipId> remainingMembers) implements PlannedChange {
        public boolean primary() {
            return false;
        }
    }

    /** AUDIO operations: replaced canonical AudioMix. */
    record AudioMixReplaced(String summary) implements PlannedChange {
        public boolean primary() {
            return true;
        }
    }

    /** ROADMAP_19 (C37): TextElement added to Timeline composition. */
    record TextElementAdded(com.example.platform.timeline.canonical.TextElementId textElementId)
            implements PlannedChange {
        @Override public boolean primary() { return true; }
    }

    /** ROADMAP_19 (C37): TextElement removed from Timeline composition. */
    record TextElementRemoved(com.example.platform.timeline.canonical.TextElementId textElementId)
            implements PlannedChange {
        @Override public boolean primary() { return true; }
    }

    /** ROADMAP_19 (C37): existing TextElement semantically replaced. */
    record TextElementReplaced(com.example.platform.timeline.canonical.TextElementId textElementId)
            implements PlannedChange {
        @Override public boolean primary() { return true; }
    }
}
