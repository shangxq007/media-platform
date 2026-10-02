package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** P2-5b-2a-2a-1: bounded V1 availability signals fail closed. */
class BoundedV1RuntimeAvailabilityTest {

    @Test
    void sandboxAvailabilityIsUnknownWithoutAProbe() {
        assertThat(BoundedV1RuntimeAvailability.sandbox())
                .isEqualTo(SandboxRuntimeAvailability.UNKNOWN);
    }

    @Test
    void runtimeEnvironmentAvailabilityIsUnknownWithoutAProbe() {
        assertThat(BoundedV1RuntimeAvailability.environment())
                .isEqualTo(RuntimeEnvironmentAvailability.UNKNOWN);
    }

    @Test
    void availabilityEnumsRetainTheirClosedValueSets() {
        assertThat(SandboxRuntimeAvailability.values())
                .containsExactly(SandboxRuntimeAvailability.AVAILABLE,
                        SandboxRuntimeAvailability.UNAVAILABLE,
                        SandboxRuntimeAvailability.UNKNOWN);
        assertThat(RuntimeEnvironmentAvailability.values())
                .containsExactly(RuntimeEnvironmentAvailability.AVAILABLE,
                        RuntimeEnvironmentAvailability.UNAVAILABLE,
                        RuntimeEnvironmentAvailability.UNKNOWN);
    }
}
