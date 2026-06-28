package com.example.platform.render.domain.remotion;

/**
 * Status of a Remotion runtime tool availability check.
 * Internal only — diagnostic, not execution permission.
 */
public enum RemotionRuntimeAvailabilityStatus {
    AVAILABLE,
    MISSING,
    VERSION_UNKNOWN,
    NOT_CHECKED,
    CHECK_FAILED,
    DISABLED_BY_POLICY
}
