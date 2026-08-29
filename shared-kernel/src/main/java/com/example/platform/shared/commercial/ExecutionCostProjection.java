package com.example.platform.shared.commercial;

import java.time.Instant;
import java.util.Objects;

/**
 * Read-only technical execution-cost input produced outside H5.
 *
 * <p>This projection is evidence H5 may consume. It does not define customer price
 * and does not transfer execution-cost authority to a commercial module.</p>
 */
public record ExecutionCostProjection(
        String executionRef,
        Money technicalCost,
        String costAuthority,
        String authorityVersion,
        Instant observedAt) {

    public ExecutionCostProjection {
        executionRef = CommercialValidation.requireNonBlank(executionRef, "executionRef");
        Objects.requireNonNull(technicalCost, "technicalCost must not be null");
        costAuthority = CommercialValidation.requireNonBlank(costAuthority, "costAuthority");
        authorityVersion = CommercialValidation.requireNonBlank(authorityVersion, "authorityVersion");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
    }
}
