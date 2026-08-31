package com.example.platform.operation.operation;

import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.timeline.semantics.relationship.GroupId;
import com.example.platform.timeline.semantics.selection.SelectionSpec;

/**
 * OPERATION_MODEL_FOUNDATION_V1 (OIR1/OIR4): requested target semantics —
 * variant-specific, definition-owned. Group/Sync/Audio targets NEVER go
 * through fake SelectionSpec; clip-scope targets use SelectionSpec +
 * ExpansionPolicy (resolved by the existing ScopeResolver).
 */
@org.springframework.modulith.NamedInterface("invocation")
public sealed interface OperationTargetRequest permits
        OperationTargetRequest.TimelineTargetRequest,
        OperationTargetRequest.ClipSelectionTargetRequest,
        OperationTargetRequest.GroupTargetRequest,
        OperationTargetRequest.SyncTargetRequest,
        OperationTargetRequest.AudioTargetRequest,
        OperationTargetRequest.TextElementTargetRequest {

    /** Exact Timeline aggregate target; never a mutable-latest alias. */
    @org.springframework.modulith.NamedInterface("invocation")
    record TimelineTargetRequest(String timelineId) implements OperationTargetRequest {
        public TimelineTargetRequest {
            if (timelineId == null || timelineId.isBlank()) {
                throw new IllegalArgumentException("timelineId required");
            }
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record ClipSelectionTargetRequest(SelectionSpec selectionSpec,
                                      SelectionSpec.ExpansionPolicy expansionPolicy)
            implements OperationTargetRequest {
        public ClipSelectionTargetRequest {
            if (selectionSpec == null) {
                throw new IllegalArgumentException("selectionSpec required");
            }
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record GroupTargetRequest(GroupId groupId) implements OperationTargetRequest {
        public GroupTargetRequest {
            if (groupId == null) {
                throw new IllegalArgumentException("groupId required");
            }
        }
    }

    /** Existing normalized Sync semantic identity (kind + normalized endpoint pair). */
    @org.springframework.modulith.NamedInterface("invocation")
    record SyncTargetRequest(String syncIdentityKey) implements OperationTargetRequest {
        public SyncTargetRequest {
            if (syncIdentityKey == null || syncIdentityKey.isBlank()) {
                throw new IllegalArgumentException("syncIdentityKey required");
            }
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record AudioTargetRequest(AudioMixInput audioMixInput) implements OperationTargetRequest {
        public AudioTargetRequest {
            if (audioMixInput == null) {
                throw new IllegalArgumentException("audioMixInput required");
            }
        }
    }

    /** ROADMAP_19 (C37): Text operation target — exact authored TextElement. */
    @org.springframework.modulith.NamedInterface("invocation")
    record TextElementTargetRequest(com.example.platform.timeline.canonical.TextElementId textElementId)
            implements OperationTargetRequest {
        public TextElementTargetRequest {
            if (textElementId == null) {
                throw new IllegalArgumentException("textElementId required");
            }
        }
    }
}
