package com.example.platform.operation.application;

import java.util.List;

/**
 * Transport shape of one Timeline batch invocation. Semantics live in the
 * generic {@code OperationBatchRequest} and in the Timeline domain adapter; this
 * record only carries the authenticated JSON payload.
 */
public record TimelineBatchCommand(
        String batchId,
        String baseRevisionId,
        String baseContentHash,
        String message,
        List<TimelineOperationStep> operations) {

    /** SHA-256 represented in canonical padded Base64 by TimelineContentDigester. */
    public static final String TIMELINE_DIGEST = "[A-Za-z0-9+/]{42}[AEIMQUYcgkosw048]=";

    /**
     * Definition-specific target coordinates. {@code clipId} also identifies the
     * audio input for {@code audio.*} operations (paired with {@code audioTrackId}).
     */
    public record Target(String clipId, List<String> clipIds, String groupId,
                         String syncIdentityKey, String audioTrackId, String textElementId, String transitionId) {
        public Target(String clipId, List<String> clipIds, String groupId, String syncIdentityKey, String audioTrackId) {
            this(clipId, clipIds, groupId, syncIdentityKey, audioTrackId, null, null);
        }
    }
}
