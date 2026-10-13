package com.example.platform.workerfabric.domain;

/**
 * P1-1A.4 / WORKER_READINESS_EVIDENCE_V1: registration-side readiness seam.
 *
 * <p>The registration loop asks this seam for the two <b>independent</b> bounded-V1 readiness
 * signals on every tick (owner ruling, option (i)). Environment readiness and sandbox readiness
 * never endorse one another: an implementation must observe each from its own authoritative probe
 * and must never derive one from the other.
 *
 * <p>The concrete production implementation lives in the worker runtime assembly, where it composes
 * the real bounded-V1 environment probe and the real bubblewrap capability detector. Keeping the
 * seam in the domain module lets the registration loop be exercised without giving the worker-fabric
 * module a sandbox implementation dependency.
 */
public interface WorkerReadinessProbe {

    /**
     * Independent environment readiness observation: real proof that the configured bounded-V1
     * execution roots are writable on this host incarnation.
     */
    RuntimeEnvironmentAvailability observeEnvironmentReadiness();

    /**
     * Independent sandbox readiness observation: the production-shape bubblewrap capability probe.
     * It is never inferred from the environment readiness result.
     */
    SandboxRuntimeAvailability observeSandboxReadiness();
}
