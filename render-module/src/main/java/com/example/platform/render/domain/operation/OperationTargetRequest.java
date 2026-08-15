package com.example.platform.render.domain.operation;

import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.render.domain.timeline.semantics.relationship.GroupId;
import com.example.platform.render.domain.timeline.semantics.selection.SelectionSpec;

/**
 * OPERATION_MODEL_FOUNDATION_V1 (OIR1/OIR4): requested target semantics —
 * variant-specific, definition-owned. Group/Sync/Audio targets NEVER go
 * through fake SelectionSpec; clip-scope targets use SelectionSpec +
 * ExpansionPolicy (resolved by the existing ScopeResolver).
 */
public sealed interface OperationTargetRequest permits
        OperationTargetRequest.ClipSelectionTargetRequest,
        OperationTargetRequest.GroupTargetRequest,
        OperationTargetRequest.SyncTargetRequest,
        OperationTargetRequest.AudioTargetRequest {

    record ClipSelectionTargetRequest(SelectionSpec selectionSpec,
                                      SelectionSpec.ExpansionPolicy expansionPolicy)
            implements OperationTargetRequest {
        public ClipSelectionTargetRequest {
            if (selectionSpec == null) {
                throw new IllegalArgumentException("selectionSpec required");
            }
        }
    }

    record GroupTargetRequest(GroupId groupId) implements OperationTargetRequest {
        public GroupTargetRequest {
            if (groupId == null) {
                throw new IllegalArgumentException("groupId required");
            }
        }
    }

    /** Existing normalized Sync semantic identity (kind + normalized endpoint pair). */
    record SyncTargetRequest(String syncIdentityKey) implements OperationTargetRequest {
        public SyncTargetRequest {
            if (syncIdentityKey == null || syncIdentityKey.isBlank()) {
                throw new IllegalArgumentException("syncIdentityKey required");
            }
        }
    }

    record AudioTargetRequest(AudioMixInput audioMixInput) implements OperationTargetRequest {
        public AudioTargetRequest {
            if (audioMixInput == null) {
                throw new IllegalArgumentException("audioMixInput required");
            }
        }
    }
}
