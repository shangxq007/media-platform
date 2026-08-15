package com.example.platform.render.domain.plan;

/**
 * OPERATION_PLAN_TRANSACTION_MODEL_V1 (§44): typed ApplyResult. APPLIED =
 * new revision + head advanced; NO_OP = semantic no-op, no revision, head
 * unchanged; durable idempotency replays the same result for the same
 * ApplyCommandId.
 */
public record ApplyResult(
        String status,
        String planDigest,
        String applyCommandId,
        String baseRevisionId,
        String newRevisionId,
        String newContentHash,
        String parentRevisionId,
        String targetRefId,
        String targetRefUpdateResult) {

    public static final String APPLIED = "APPLIED";
    public static final String NO_OP = "NO_OP";

    public static ApplyResult applied(String planDigest, String applyCommandId, String baseRevisionId,
                                      String newRevisionId, String newContentHash, String parentRevisionId,
                                      String targetRefId) {
        return new ApplyResult(APPLIED, planDigest, applyCommandId, baseRevisionId,
                newRevisionId, newContentHash, parentRevisionId, targetRefId, "ADVANCED");
    }

    public static ApplyResult noOp(String planDigest, String applyCommandId, String baseRevisionId,
                                   String baseContentHash, String targetRefId) {
        return new ApplyResult(NO_OP, planDigest, applyCommandId, baseRevisionId,
                null, baseContentHash, null, targetRefId, "UNCHANGED");
    }
}
