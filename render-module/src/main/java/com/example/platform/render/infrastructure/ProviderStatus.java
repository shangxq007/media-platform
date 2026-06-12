package com.example.platform.render.infrastructure;

/**
 * Maturity status of a render provider.
 *
 * <p>Status determines whether a provider can be automatically dispatched
 * for production render jobs:
 * <ul>
 *   <li>{@link #PRODUCTION} — fully implemented, safe for production dispatch</li>
 *   <li>{@link #POC} — implemented as proof-of-concept, needs explicit allow for dispatch</li>
 *   <li>{@link #OPTIONAL} — implemented but not default, explicit configuration required</li>
 *   <li>{@link #STUB} — interface exists but no real implementation (e.g., Blender, Remotion without runtime)</li>
 *   <li>{@link #SKELETON} — API client exists but not wired or not production-tested (e.g., Shotstack)</li>
 *   <li>{@link #HOLD} — implementation exists but paused/deferred (e.g., GStreamer, Natron)</li>
 *   <li>{@link #SPIKE} — experimental, not for production use</li>
 *   <li>{@link #DEPRECATED} — was implemented, now superseded</li>
 *   <li>{@link #MOCK} — simulated provider for testing only</li>
 * </ul>
 *
 * @see ProviderEligibility for dispatch rules
 * @see ProviderMetadata for provider metadata
 */
public enum ProviderStatus {

    /**
     * Fully implemented and safe for production dispatch.
     * Example: FFmpeg, RemoteRenderProvider.
     */
    PRODUCTION(true, true),

    /**
     * Implemented as proof-of-concept. Works but may have limitations.
     * Requires explicit configuration (e.g., {@code render.providers.allow-poc=true})
     * or provider-specific {@code @ConditionalOnProperty} to be dispatched.
     * Example: MLT, GPAC, libass overlay, Skia sticker overlay.
     */
    POC(false, true),

    /**
     * Implemented but not a default choice. Requires explicit configuration.
     * Example: Shotstack cloud render.
     */
    OPTIONAL(false, true),

    /**
     * Interface exists but no real implementation. The provider writes stub bytes
     * or throws on invocation. Not dispatchable under any circumstances.
     * Example: Blender (no binary), Remotion (no CLI).
     */
    STUB(false, false),

    /**
     * API client or adapter exists but not wired as a Spring bean or not
     * production-tested. Not dispatchable.
     * Example: Shotstack (real API client, not wired), Natron (FFmpeg fallback).
     */
    SKELETON(false, false),

    /**
     * Implementation exists but is paused or deferred. Not dispatchable
     * unless in experiment/manual mode.
     * Example: GStreamer, VapourSynth.
     */
    HOLD(false, true),

    /**
     * Experimental spike. Not for production use. Manual mode only.
     */
    SPIKE(false, true),

    /**
     * Was implemented, now superseded by another provider. Not dispatchable.
     * Example: JavaCV (superseded by FFmpeg), OFX (Java2D simulation).
     */
    DEPRECATED(false, false),

    /**
     * Simulated provider for development and testing. Never dispatched in production.
     * Example: MockRenderProvider.
     */
    MOCK(false, false);

    private final boolean productionDispatchEligible;
    private final boolean canBeConfiguredForDispatch;

    ProviderStatus(boolean productionDispatchEligible, boolean canBeConfiguredForDispatch) {
        this.productionDispatchEligible = productionDispatchEligible;
        this.canBeConfiguredForDispatch = canBeConfiguredForDispatch;
    }

    /**
     * Returns true if this status allows automatic production dispatch
     * without additional configuration.
     */
    public boolean isProductionDispatchEligible() {
        return productionDispatchEligible;
    }

    /**
     * Returns true if this status can be configured to allow dispatch
     * (e.g., POC with explicit allow, OPTIONAL with explicit enable).
     * Returns false for STUB, SKELETON, DEPRECATED, MOCK — these can never be dispatched.
     */
    public boolean canBeConfiguredForDispatch() {
        return canBeConfiguredForDispatch;
    }

    /**
     * Returns true if this provider has a real implementation (not stub/skeleton).
     */
    public boolean hasRealImplementation() {
        return this == PRODUCTION || this == POC || this == OPTIONAL || this == HOLD;
    }
}
