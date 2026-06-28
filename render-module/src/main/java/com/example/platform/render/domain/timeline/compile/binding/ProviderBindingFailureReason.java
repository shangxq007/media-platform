package com.example.platform.render.domain.timeline.compile.binding;

/**
 * Explicit reason why provider binding failed for a capability node.
 *
 * <p>Internal only — may appear in tests and internal docs.
 * Must not leak into public render status/result APIs unless
 * explicitly safe and already part of internal admin/debug surface.</p>
 */
public enum ProviderBindingFailureReason {

    /** No provider declares the required capability. */
    REQUIRED_CAPABILITY_MISSING,

    /** Provider is explicitly disabled by configuration. */
    PROVIDER_DISABLED,

    /** Provider is not production-eligible (POC/SPIKE/HOLD). */
    PROVIDER_NOT_PRODUCTION_ELIGIBLE,

    /** Provider tool/binary is not available on this system. */
    TOOL_UNAVAILABLE,

    /** Provider status blocks dispatch (STUB/SKELETON/DEPRECATED/MOCK). */
    PROVIDER_STATUS_BLOCKED,

    /** Provider has autoDispatch=false and job is not manual/experiment. */
    PROVIDER_AUTO_DISPATCH_DISABLED,

    /** Multiple providers match equally; no clear winner. */
    MULTIPLE_PROVIDERS_AMBIGUOUS,

    /** Provider type does not support this artifact type. */
    PROVIDER_TYPE_UNSUPPORTED,

    /** OpenFX capability requires an OFX host (not an executable provider). */
    OPENFX_REQUIRES_HOST,

    /** Execution environment does not support this provider. */
    EXECUTION_ENVIRONMENT_UNSUPPORTED
}
