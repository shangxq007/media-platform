package com.example.platform.workerfabric.domain;

/** Stable ordered reason families for runtime dependency conformance. */
public enum RuntimeDependencyMatchReasonCode {
    INCOMPLETE_CRITICAL_EVIDENCE(true),
    PROBE_SCHEMA_MISMATCH(true),
    PROVIDER_IMPLEMENTATION_MISMATCH(false),
    WORKER_RUNTIME_MISMATCH(false),
    DEVICE_BINDING_MISMATCH(false),
    STALE_OBSERVATION(true),
    RUNTIME_DEPENDENCY_MISSING(false),
    RUNTIME_DEPENDENCY_VERSION_INCOMPATIBLE(false),
    RUNTIME_DEPENDENCY_ABI_INCOMPATIBLE(false),
    RUNTIME_DEPENDENCY_FEATURE_MISSING(false),
    RUNTIME_DEPENDENCY_BUILD_RUNTIME_FLAG_MISSING(false);

    private final boolean unknownEvidence;

    RuntimeDependencyMatchReasonCode(boolean unknownEvidence) {
        this.unknownEvidence = unknownEvidence;
    }

    public boolean unknownEvidence() {
        return unknownEvidence;
    }
}
