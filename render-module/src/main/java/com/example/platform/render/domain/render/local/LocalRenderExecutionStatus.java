package com.example.platform.render.domain.render.local;

/**
 * Outcome status of a local render execution.
 * Extends LocalRenderSmokeStatus with UNSUPPORTED for plan-level rejection.
 */
public enum LocalRenderExecutionStatus {
    PASS,
    PASS_WITH_WARNINGS,
    FAIL,
    BLOCKED,
    SKIPPED,
    NOT_AVAILABLE,
    UNSUPPORTED
}
