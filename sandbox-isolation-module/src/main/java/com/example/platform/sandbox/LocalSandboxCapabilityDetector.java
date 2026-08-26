package com.example.platform.sandbox;

import java.time.Instant;
import java.util.Set;

/** Truthful capability evidence for the best-effort local process adapter. */
@org.springframework.modulith.NamedInterface("API")
public final class LocalSandboxCapabilityDetector {
    private LocalSandboxCapabilityDetector() {}

    public static SandboxRuntimeCapabilities detect() {
        Set<SandboxCapability> capabilities = Set.of(
                SandboxCapability.BEST_EFFORT_DESCENDANT_CLEANUP,
                SandboxCapability.WALL_CLOCK_TIMEOUT,
                SandboxCapability.FILESYSTEM_PATH_VALIDATION,
                SandboxCapability.ENVIRONMENT_CLEARING,
                SandboxCapability.BOUNDED_CAPTURE);
        return SandboxRuntimeCapabilities.detected(
                capabilities, "linux-local-process-best-effort", Instant.now());
    }
}
