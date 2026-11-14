package com.example.platform.config.render;

/**
 * First Render output format intent (Phase 4).
 *
 * <p>V0 supports exactly one value: {@link #MP4}. It maps to the canonical
 * {@code RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)} produced by the Planning
 * Application Service; the HTTP layer never redefines {@code RenderOutputRequirement} or its role.
 */
public enum OutputFormat {
    MP4
}
