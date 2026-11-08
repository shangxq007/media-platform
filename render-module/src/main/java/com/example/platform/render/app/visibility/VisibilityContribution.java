package com.example.platform.render.app.visibility;

/** Contribution of a clip to the requested output extent (not a deletion instruction). */
public enum VisibilityContribution {
    NONE,
    PARTIAL,
    FULL,
    UNKNOWN
}
