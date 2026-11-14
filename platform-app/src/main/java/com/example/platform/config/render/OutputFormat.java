package com.example.platform.config.render;

/**
 * Formal RenderJob output format intent.
 *
 * <p>V0 supports exactly one value: {@link #MP4}. It maps to the canonical
 * {@code RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)} produced by the Planning
 * Application Service; the HTTP layer never redefines {@code RenderOutputRequirement} or its role.
 */
public enum OutputFormat {
    MP4
}
