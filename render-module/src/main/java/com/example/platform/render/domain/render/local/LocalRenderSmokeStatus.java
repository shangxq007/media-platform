package com.example.platform.render.domain.render.local;

/**
 * Outcome status of a local render smoke execution.
 */
public enum LocalRenderSmokeStatus {
    PASS,
    PASS_WITH_WARNINGS,
    FAIL,
    BLOCKED,
    SKIPPED,
    NOT_AVAILABLE
}
