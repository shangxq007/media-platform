package com.example.platform.workerfabric.domain;

/** Stable ordered reason families for provider hardware/runtime conformance. */
public enum ProviderHardwareConformanceReasonCode {
    INCOMPLETE_CRITICAL_EVIDENCE(true),
    PROVIDER_IMPLEMENTATION_MISMATCH(false),
    WORKER_RUNTIME_MISMATCH(false),
    PHYSICAL_HOST_MISMATCH(false),
    DEVICE_IDENTITY_MISMATCH(false),
    STALE_OBSERVATION(true),
    PROBE_UNKNOWN(true),
    PROBE_FAILED(true),
    RUNTIME_UNAVAILABLE(false),
    CPU_ARCHITECTURE_INCOMPATIBLE(false),
    DEVICE_CLASS_UNAVAILABLE(false),
    DEVICE_UNAVAILABLE(false),
    DRIVER_RUNTIME_INCOMPATIBLE(false),
    PROVIDER_BUILD_FEATURE_MISSING(false),
    CODEC_OR_FILTER_FEATURE_MISSING(false),
    DEVICE_FEATURE_UNAVAILABLE(false),
    DEVICE_NOT_EXPOSED(false),
    SANDBOX_PERMISSION_UNAVAILABLE(false);

    private final boolean unknownEvidence;

    ProviderHardwareConformanceReasonCode(boolean unknownEvidence) {
        this.unknownEvidence = unknownEvidence;
    }

    public boolean unknownEvidence() {
        return unknownEvidence;
    }
}
