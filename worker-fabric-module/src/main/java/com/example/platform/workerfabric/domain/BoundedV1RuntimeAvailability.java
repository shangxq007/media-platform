package com.example.platform.workerfabric.domain;

/**
 * Bounded V1 runtime availability policy (owner decision Q3).
 *
 * <p>The bounded single-host configuration has no sandbox-runtime or runtime-environment probe, so
 * both signals are reported as {@code UNKNOWN}. That is the fail-closed value: the platform claims no
 * capability it has not observed, and the eligibility evaluator decides what to do with an unknown
 * signal. It is an explicit policy value — not a placeholder and not a synthetic "AVAILABLE".
 */
public final class BoundedV1RuntimeAvailability {

    private BoundedV1RuntimeAvailability() {
    }

    /** No sandbox probe exists in bounded V1: report UNKNOWN rather than assuming availability. */
    public static SandboxRuntimeAvailability sandbox() {
        return SandboxRuntimeAvailability.UNKNOWN;
    }

    /** No runtime-environment probe exists in bounded V1: report UNKNOWN. */
    public static RuntimeEnvironmentAvailability environment() {
        return RuntimeEnvironmentAvailability.UNKNOWN;
    }
}
