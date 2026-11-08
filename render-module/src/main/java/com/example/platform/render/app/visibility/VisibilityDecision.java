package com.example.platform.render.app.visibility;

/**
 * Conservative analysis decision.
 *
 * <p>{@link #OUTSIDE_REQUEST} means no direct output contribution only. It is NOT
 * permission to delete the Canonical clip, source Artifact, audio route, shared
 * decode node, or any other consumer.</p>
 */
public enum VisibilityDecision {
    CONTRIBUTES,
    OUTSIDE_REQUEST,
    KEEP_UNKNOWN
}
