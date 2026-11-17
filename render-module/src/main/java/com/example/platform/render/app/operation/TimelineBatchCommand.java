package com.example.platform.render.app.operation;

import java.util.List;
import java.util.Map;

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
        List<Operation> operations) {

    public record Operation(String definitionId, Target target, Map<String, Object> parameters) {
    }

    /**
     * Definition-specific target coordinates. {@code clipId} also identifies the
     * audio input for {@code audio.*} operations (paired with {@code audioTrackId}).
     */
    public record Target(String clipId, String groupId, String syncIdentityKey,
                         String audioTrackId) {
    }
}
