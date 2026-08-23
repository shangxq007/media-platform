package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Capacity deliberately withheld from scheduling after reservation accounting. */
public record SafetyHeadroom(ReservedResources resources) {

    public SafetyHeadroom {
        Objects.requireNonNull(resources, "resources");
    }

    public static SafetyHeadroom none() {
        return new SafetyHeadroom(ReservedResources.none());
    }
}
