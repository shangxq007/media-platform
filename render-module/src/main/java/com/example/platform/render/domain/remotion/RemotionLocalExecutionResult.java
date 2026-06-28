package com.example.platform.render.domain.remotion;

import java.util.List;

/**
 * Result of a Remotion local execution attempt.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * <p>v0: All results have executed=false and readyToExecute=false.
 * No external process is started. No output is produced.</p>
 *
 * @param status           execution status
 * @param preflightStatus  preflight status that led to this result
 * @param failureReason    human-readable failure reason
 * @param readyToExecute   whether execution could proceed (false in v0)
 * @param executed         whether execution actually ran (false in v0)
 * @param outputProductId  output product ID (null in v0)
 * @param outputPathRef    output path reference (null in v0)
 * @param safeMessage      safe message for internal logging
 * @param violations       policy/sandbox violations
 * @param safeMetadata     safe metadata only
 */
public record RemotionLocalExecutionResult(
        RemotionLocalExecutionStatus status,
        RemotionExecutionPreflightStatus preflightStatus,
        String failureReason,
        boolean readyToExecute,
        boolean executed,
        String outputProductId,
        String outputPathRef,
        String safeMessage,
        List<String> violations,
        java.util.Map<String, String> safeMetadata) {

    /**
     * Returns true if execution was blocked (not implemented, not executed).
     */
    public boolean isBlocked() {
        return status != RemotionLocalExecutionStatus.NOT_IMPLEMENTED
                && !executed;
    }

    /**
     * Returns true if execution is not implemented.
     */
    public boolean notImplemented() {
        return status == RemotionLocalExecutionStatus.NOT_IMPLEMENTED;
    }

    // --- Factory methods ---

    public static RemotionLocalExecutionResult notImplemented(String message) {
        return new RemotionLocalExecutionResult(
                RemotionLocalExecutionStatus.NOT_IMPLEMENTED,
                RemotionExecutionPreflightStatus.NOT_IMPLEMENTED,
                message, false, false, null, null, message, List.of(), java.util.Map.of());
    }

    public static RemotionLocalExecutionResult blockedByPreflight(
            RemotionExecutionPreflightStatus preflightStatus, String message, List<String> violations) {
        return new RemotionLocalExecutionResult(
                mapPreflightStatus(preflightStatus), preflightStatus,
                message, false, false, null, null, message, violations, java.util.Map.of());
    }

    public static RemotionLocalExecutionResult blockedByPolicy(String message) {
        return new RemotionLocalExecutionResult(
                RemotionLocalExecutionStatus.BLOCKED_BY_POLICY,
                RemotionExecutionPreflightStatus.BLOCKED_BY_POLICY,
                message, false, false, null, null, message, List.of(), java.util.Map.of());
    }

    public static RemotionLocalExecutionResult blockedByRuntime(String message) {
        return new RemotionLocalExecutionResult(
                RemotionLocalExecutionStatus.BLOCKED_BY_RUNTIME,
                RemotionExecutionPreflightStatus.BLOCKED_BY_RUNTIME,
                message, false, false, null, null, message, List.of(), java.util.Map.of());
    }

    public static RemotionLocalExecutionResult rejectedUnsupported(String message) {
        return new RemotionLocalExecutionResult(
                RemotionLocalExecutionStatus.REJECTED_UNSUPPORTED_DOCUMENT,
                RemotionExecutionPreflightStatus.BLOCKED_BY_UNSUPPORTED_DOCUMENT,
                message, false, false, null, null, message, List.of(), java.util.Map.of());
    }

    public static RemotionLocalExecutionResult failedClosed(String message) {
        return new RemotionLocalExecutionResult(
                RemotionLocalExecutionStatus.FAILED_CLOSED, null,
                message, false, false, null, null, message, List.of(), java.util.Map.of());
    }

    private static RemotionLocalExecutionStatus mapPreflightStatus(RemotionExecutionPreflightStatus preflight) {
        return switch (preflight) {
            case BLOCKED_BY_POLICY -> RemotionLocalExecutionStatus.BLOCKED_BY_POLICY;
            case BLOCKED_BY_RUNTIME -> RemotionLocalExecutionStatus.BLOCKED_BY_RUNTIME;
            case BLOCKED_BY_SANDBOX -> RemotionLocalExecutionStatus.BLOCKED_BY_SANDBOX;
            case BLOCKED_BY_UNSUPPORTED_DOCUMENT -> RemotionLocalExecutionStatus.REJECTED_UNSUPPORTED_DOCUMENT;
            case BLOCKED_BY_UNSAFE_COMMAND -> RemotionLocalExecutionStatus.BLOCKED_BY_UNSAFE_COMMAND;
            case READY_BUT_EXECUTION_DISABLED -> RemotionLocalExecutionStatus.NOT_IMPLEMENTED;
            case NOT_IMPLEMENTED -> RemotionLocalExecutionStatus.NOT_IMPLEMENTED;
        };
    }
}
