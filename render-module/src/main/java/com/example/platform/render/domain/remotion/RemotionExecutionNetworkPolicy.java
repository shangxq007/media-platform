package com.example.platform.render.domain.remotion;

/**
 * Network policy for Remotion execution.
 * Internal only.
 */
public enum RemotionExecutionNetworkPolicy {
    /** Network access denied (default). */
    DENIED,
    /** Network access allowed for specific internal endpoints only. */
    INTERNAL_ONLY,
    /** Network access fully allowed (dangerous, not for production). */
    ALLOWED
}
