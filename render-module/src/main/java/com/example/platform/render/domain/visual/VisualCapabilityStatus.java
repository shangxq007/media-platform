package com.example.platform.render.domain.visual;

/**
 * Capability lifecycle status.
 * Immutable enum. Internal domain model.
 *
 * <ul>
 *   <li>PRODUCTION — safe for product path</li>
 *   <li>BASELINE_CANDIDATE — intended for FFmpeg/libass baseline but not yet fully wired</li>
 *   <li>POC — internal validation only</li>
 *   <li>SPIKE — research only</li>
 *   <li>FUTURE — vocabulary only</li>
 *   <li>RESTRICTED — not available without dedicated ADR/security review</li>
 *   <li>FORBIDDEN — must not be used</li>
 *   <li>DEPRECATED — scheduled for removal</li>
 * </ul>
 */
public enum VisualCapabilityStatus {
    PRODUCTION,
    BASELINE_CANDIDATE,
    POC,
    SPIKE,
    FUTURE,
    RESTRICTED,
    FORBIDDEN,
    DEPRECATED;

    /**
     * Returns true if this status allows production dispatch.
     */
    public boolean isProductionAllowed() {
        return this == PRODUCTION;
    }

    /**
     * Returns true if this status allows auto-dispatch.
     */
    public boolean isAutoDispatchAllowed() {
        return this == PRODUCTION || this == BASELINE_CANDIDATE;
    }
}
