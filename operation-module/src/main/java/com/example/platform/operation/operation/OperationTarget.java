package com.example.platform.operation.operation;

import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.timeline.semantics.relationship.GroupId;
import com.example.platform.timeline.semantics.selection.ResolvedScope;

/**
 * OPERATION_MODEL_FOUNDATION_V1 (OIR1): resolved typed Operation target.
 * Clip-scope targets wrap a revision-bound ResolvedScope; Group/Sync/Audio
 * targets are direct typed semantic identities. ResolvedScope is NOT a
 * universal target authority; no raw String / god object / index target.
 */
public sealed interface OperationTarget permits
        OperationTarget.TimelineTarget,
        OperationTarget.ResolvedClipScopeTarget,
        OperationTarget.GroupTarget,
        OperationTarget.SyncTarget,
        OperationTarget.AudioTarget {

    record TimelineTarget(String timelineId) implements OperationTarget {
        public TimelineTarget {
            if (timelineId == null || timelineId.isBlank()) {
                throw new IllegalArgumentException("timelineId required");
            }
        }
    }

    record ResolvedClipScopeTarget(ResolvedScope resolvedScope) implements OperationTarget {
        public ResolvedClipScopeTarget {
            if (resolvedScope == null) {
                throw new IllegalArgumentException("resolvedScope required");
            }
        }
    }

    record GroupTarget(GroupId groupId) implements OperationTarget {
        public GroupTarget {
            if (groupId == null) {
                throw new IllegalArgumentException("groupId required");
            }
        }
    }

    record SyncTarget(String syncIdentityKey) implements OperationTarget {
        public SyncTarget {
            if (syncIdentityKey == null || syncIdentityKey.isBlank()) {
                throw new IllegalArgumentException("syncIdentityKey required");
            }
        }
    }

    record AudioTarget(AudioMixInput audioMixInput) implements OperationTarget {
        public AudioTarget {
            if (audioMixInput == null) {
                throw new IllegalArgumentException("audioMixInput required");
            }
        }
    }
}
