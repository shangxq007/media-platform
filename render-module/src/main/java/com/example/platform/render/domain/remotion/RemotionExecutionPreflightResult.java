package com.example.platform.render.domain.remotion;

import java.util.List;

/**
 * Result of Remotion execution preflight check.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * @param status       preflight status
 * @param violations   policy/sandbox/command violations
 * @param explanation  human-readable explanation
 * @param readyToExecute whether execution could proceed (false in v0)
 */
public record RemotionExecutionPreflightResult(
        RemotionExecutionPreflightStatus status,
        List<String> violations,
        String explanation,
        boolean readyToExecute) {

    /**
     * Returns true if preflight passed (ready for future execution).
     */
    public boolean passed() {
        return status == RemotionExecutionPreflightStatus.READY_BUT_EXECUTION_DISABLED;
    }

    /**
     * Returns true if preflight blocked execution.
     */
    public boolean blocked() {
        return status != RemotionExecutionPreflightStatus.READY_BUT_EXECUTION_DISABLED
                && status != RemotionExecutionPreflightStatus.NOT_IMPLEMENTED;
    }

    /**
     * Returns true if execution is not implemented.
     */
    public boolean notImplemented() {
        return status == RemotionExecutionPreflightStatus.NOT_IMPLEMENTED;
    }
}
