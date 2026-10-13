package com.example.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.workerfabric.domain.CpuArchitecture;
import org.junit.jupiter.api.Test;

/** P1-1A.6 Gate 2: the CPU architecture observation is real and fail-closed on unknowns. */
class CpuArchitectureProbeTest {

    @Test
    void mapsCanonicalArchitectureSpellings() {
        assertThat(CpuArchitectureProbe.map("amd64")).contains(CpuArchitecture.X86_64);
        assertThat(CpuArchitectureProbe.map("x86_64")).contains(CpuArchitecture.X86_64);
        assertThat(CpuArchitectureProbe.map("AARCH64")).contains(CpuArchitecture.AARCH64);
        assertThat(CpuArchitectureProbe.map("arm64")).contains(CpuArchitecture.AARCH64);
        assertThat(CpuArchitectureProbe.map("riscv64")).contains(CpuArchitecture.RISCV64);
    }

    @Test
    void unknownOrMissingArchitectureIsEmptyNeverGuessed() {
        assertThat(CpuArchitectureProbe.map("sparc")).isEmpty();
        assertThat(CpuArchitectureProbe.map("")).isEmpty();
        assertThat(CpuArchitectureProbe.map(null)).isEmpty();
    }

    @Test
    void observesTheLiveRuntimeArchitecture() {
        var probe = new CpuArchitectureProbe();

        // The live process reports a canonical architecture on every supported worker host.
        assertThat(probe.probe()).isPresent();
    }
}
