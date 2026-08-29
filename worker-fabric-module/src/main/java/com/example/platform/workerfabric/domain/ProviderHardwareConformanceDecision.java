package com.example.platform.workerfabric.domain;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Immutable fail-closed technical conformance decision with no policy or optimization fields. */
public record ProviderHardwareConformanceDecision(
        ProviderHardwareConformanceStatus status,
        List<ProviderHardwareConformanceReason> reasons)
        implements Serializable {

    public ProviderHardwareConformanceDecision {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reasons, "reasons");
        reasons = List.copyOf(reasons);
        for (int index = 1; index < reasons.size(); index++) {
            if (reasons.get(index - 1).compareTo(reasons.get(index)) >= 0) {
                throw new IllegalArgumentException("reasons must be unique and canonically ordered");
            }
        }
        boolean unknown = reasons.stream().anyMatch(reason -> reason.code().unknownEvidence());
        if ((status == ProviderHardwareConformanceStatus.CAN_RUN) != reasons.isEmpty()) {
            throw new IllegalArgumentException("CAN_RUN must have no reasons and failures must have reasons");
        }
        if ((status == ProviderHardwareConformanceStatus.UNKNOWN_FAIL_CLOSED) != unknown) {
            throw new IllegalArgumentException("unknown status must match unknown evidence reasons");
        }
    }

    public boolean canRun() {
        return status == ProviderHardwareConformanceStatus.CAN_RUN;
    }
}
