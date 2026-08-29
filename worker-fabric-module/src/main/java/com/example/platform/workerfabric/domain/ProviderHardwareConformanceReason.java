package com.example.platform.workerfabric.domain;

import java.io.Serializable;
import java.util.Objects;

/** Typed non-secret conformance reason ordered by its stable reason algebra. */
public record ProviderHardwareConformanceReason(ProviderHardwareConformanceReasonCode code)
        implements Comparable<ProviderHardwareConformanceReason>, Serializable {

    public ProviderHardwareConformanceReason {
        Objects.requireNonNull(code, "code");
    }

    @Override
    public int compareTo(ProviderHardwareConformanceReason other) {
        return Integer.compare(code.ordinal(), other.code.ordinal());
    }
}
