package com.example.platform.extension.domain;

import java.util.Set;

/**
 * Guarantee declaration (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>False-by-default guarantee vocabulary. {@code producesCanonicalTimeline}
 * and {@code producesReadyProduct} MUST be {@code false} for the FFmpeg
 * self-description; {@code writesRevision} has NO enum constant and therefore
 * cannot be declared (a plugin declaration cannot grant platform authority).
 * Optional guarantees (deterministic, idempotent, cancelable, streaming,
 * duration/audio/video preservation) default to NOT_DECLARED unless the exact
 * contract froze them as supported by current evidence.</p>
 *
 * @param producesCanonicalTimeline false (mandatory prohibition)
 * @param producesReadyProduct      false (mandatory prohibition — ProductRuntimeService stays sole READY authority)
 * @param declaredGuarantees        set of positively declared optional guarantees (empty = NOT_DECLARED)
 */
public record PluginGuarantee(
        boolean producesCanonicalTimeline,
        boolean producesReadyProduct,
        Set<Guarantee> declaredGuarantees) {

    /** Optional guarantee vocabulary. {@code writesRevision} is deliberately absent. */
    public enum Guarantee {
        DETERMINISTIC,
        IDEMPOTENT,
        CANCELABLE,
        STREAMING,
        PRESERVES_DURATION,
        PRESERVES_AUDIO,
        PRESERVES_VIDEO
    }

    /** Frozen FFmpeg declaration: no timeline/READY authority, nothing else declared. */
    public static PluginGuarantee ffmpegDefaults() {
        return new PluginGuarantee(false, false, Set.of());
    }

    /** Legal-value check: producesCanonicalTimeline/producesReadyProduct must be false. */
    public boolean legal() {
        return !producesCanonicalTimeline && !producesReadyProduct;
    }

    public PluginGuarantee {
        if (declaredGuarantees == null) {
            throw new NullPointerException("declaredGuarantees must not be null");
        }
        declaredGuarantees = Set.copyOf(declaredGuarantees);
    }
}
