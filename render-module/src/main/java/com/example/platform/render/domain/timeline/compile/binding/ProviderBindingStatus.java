package com.example.platform.render.domain.timeline.compile.binding;

/**
 * Status of a provider binding decision for a capability node.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 */
public enum ProviderBindingStatus {

    /** Provider successfully bound to capability node. */
    BOUND,

    /** No provider bound yet (pending binding). */
    UNBOUND,

    /** Capability is unsupported by any available provider. */
    UNSUPPORTED,

    /** Provider tool/binary is not available on this system. */
    TOOL_UNAVAILABLE,

    /** Provider exists but is not production-eligible for auto-dispatch. */
    NOT_PRODUCTION_ELIGIBLE,

    /** Provider is only available in manual/experiment mode. */
    MANUAL_ONLY,

    /** Multiple providers match; ambiguity not resolved. */
    AMBIGUOUS,

    /** Provider is explicitly disabled by configuration. */
    DISABLED,

    /** Binding failed closed due to safety or validation constraint. */
    FAILED_CLOSED
}
