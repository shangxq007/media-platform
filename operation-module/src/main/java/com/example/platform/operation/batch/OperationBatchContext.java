package com.example.platform.operation.batch;

import com.example.platform.operation.plan.AuthorizationDecision;
import com.example.platform.shared.authorization.CanonicalActor;

/**
 * Caller context for one batch invocation.
 *
 * <p>Preview contexts carry no apply command and no authorization decision; a
 * commit context must carry both, and the decision must bind the exact
 * {@code batchDigest} of the plan being committed.
 */
@org.springframework.modulith.NamedInterface("batch")
public record OperationBatchContext(
        String applyCommandId,
        CanonicalActor actor,
        AuthorizationDecision authorization,
        String correlationId,
        String expectedBatchDigest) {
    public OperationBatchContext(String applyCommandId, CanonicalActor actor,
            AuthorizationDecision authorization, String correlationId) {
        this(applyCommandId, actor, authorization, correlationId, null);
    }


    public OperationBatchContext {
        if (actor == null) {
            throw new IllegalArgumentException("actor required");
        }
        if (authorization != null && (applyCommandId == null || applyCommandId.isBlank())) {
            throw new IllegalArgumentException("applyCommandId required for an authorized commit");
        }
    }

    public static OperationBatchContext preview(CanonicalActor actor, String correlationId) {
        return new OperationBatchContext(null, actor, null, correlationId);
    }

    public static OperationBatchContext apply(String applyCommandId, CanonicalActor actor,
                                              AuthorizationDecision authorization,
                                              String correlationId) {
        return new OperationBatchContext(applyCommandId, actor, authorization, correlationId);
    }

    public String principalRef() {
        return actor.actorId();
    }
}
