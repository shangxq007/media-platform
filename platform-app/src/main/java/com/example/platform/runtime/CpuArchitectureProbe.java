package com.example.platform.runtime;

import com.example.platform.workerfabric.domain.CpuArchitecture;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * P1-1A.6 (Gate 2): observes the CPU instruction-set architecture of the running worker JVM — the
 * same machine the sandbox executes on.
 *
 * <p>This is a real runtime observation ({@code os.arch} of the live process), never a value mapped
 * back from a provider's declared {@code ProviderHardwareRequirement}. An unrecognized architecture
 * yields {@link Optional#empty()} so the caller fails closed instead of guessing.
 */
public final class CpuArchitectureProbe {

    private final Supplier<String> architectureProperty;

    public CpuArchitectureProbe() {
        this(() -> System.getProperty("os.arch"));
    }

    CpuArchitectureProbe(Supplier<String> architectureProperty) {
        this.architectureProperty = Objects.requireNonNull(
                architectureProperty, "architectureProperty");
    }

    public Optional<CpuArchitecture> probe() {
        return map(architectureProperty.get());
    }

    /** Canonical JVM {@code os.arch} spellings mapped to the frozen architecture vocabulary. */
    static Optional<CpuArchitecture> map(String osArch) {
        if (osArch == null || osArch.isBlank()) {
            return Optional.empty();
        }
        return switch (osArch.trim().toLowerCase(Locale.ROOT)) {
            case "amd64", "x86_64", "x86-64", "x64" -> Optional.of(CpuArchitecture.X86_64);
            case "aarch64", "arm64" -> Optional.of(CpuArchitecture.AARCH64);
            case "riscv64" -> Optional.of(CpuArchitecture.RISCV64);
            default -> Optional.empty();
        };
    }
}
