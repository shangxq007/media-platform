package com.example.platform.runtime;

import com.example.platform.sandbox.BubblewrapSandboxCapabilityDetector;
import com.example.platform.sandbox.BubblewrapSandboxDetection;
import com.example.platform.workerfabric.domain.EnvironmentReadinessProbe;
import com.example.platform.workerfabric.domain.RuntimeEnvironmentAvailability;
import com.example.platform.workerfabric.domain.SandboxRuntimeAvailability;
import com.example.platform.workerfabric.domain.WorkerReadinessProbe;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P1-1A.4 / WORKER_READINESS_EVIDENCE_V1: the worker's concrete, two-source readiness probe.
 *
 * <p>The two signals are <b>independent</b> (owner ruling, option (i)):
 * <ul>
 *   <li><b>Environment readiness</b> comes from the real {@link EnvironmentReadinessProbe} over the
 *       bounded-V1 execution roots the worker actually uses: the configured {@code work-root}, the
 *       {@code workspace-root} with its {@code .sandbox-tmp}/{@code .sandbox-output} sub-roots and its
 *       working directory, and the {@code staging-root}. Each root is write/read-back/cleanup probed;
 *       existence or static configuration is never sufficient.</li>
 *   <li><b>Sandbox readiness</b> comes from the production-shape
 *       {@link BubblewrapSandboxCapabilityDetector}, which runs the same mount/namespace shape as the
 *       real execution. A successful production-shape probe yields {@code AVAILABLE}; an absent binary
 *       or a failed production-shape probe yields {@code UNAVAILABLE}; only a probe that cannot be
 *       carried out at all yields {@code UNKNOWN}.</li>
 * </ul>
 *
 * <p>The sandbox signal is never derived from the environment signal and vice versa.
 */
public final class FfmpegWorkerReadinessProbe implements WorkerReadinessProbe {

    private static final Logger log = LoggerFactory.getLogger(FfmpegWorkerReadinessProbe.class);

    private final EnvironmentReadinessProbe environmentProbe;
    private final Path configuredBubblewrap;

    public FfmpegWorkerReadinessProbe(
            EnvironmentReadinessProbe environmentProbe, Path configuredBubblewrap) {
        this.environmentProbe = Objects.requireNonNull(environmentProbe, "environmentProbe");
        this.configuredBubblewrap = Objects.requireNonNull(configuredBubblewrap, "configuredBubblewrap");
    }

    /**
     * Builds the production probe from the worker's configured bounded-V1 roots and the configured
     * bubblewrap binary. The roots mirror the exact paths the sandbox execution uses
     * ({@code FfmpegSandboxWorkspace.under(workspaceRoot)}): its output staging root is the working
     * directory; both mutable sub-roots remain under the configured workspace.
     */
    public static FfmpegWorkerReadinessProbe of(
            String workRoot, String workspaceRoot, String stagingRoot, String bubblewrap) {
        Path workspace = Path.of(workspaceRoot).toAbsolutePath().normalize();
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        roots.add(Path.of(workRoot).toAbsolutePath().normalize());
        roots.add(workspace);
        roots.add(workspace.resolve(".sandbox-tmp"));
        roots.add(workspace.resolve(".sandbox-output"));
        roots.add(Path.of(stagingRoot).toAbsolutePath().normalize());
        return new FfmpegWorkerReadinessProbe(
                new EnvironmentReadinessProbe(List.copyOf(roots)),
                Path.of(bubblewrap).toAbsolutePath().normalize());
    }

    @Override
    public RuntimeEnvironmentAvailability observeEnvironmentReadiness() {
        EnvironmentReadinessProbe.Result result = environmentProbe.probe();
        if (result.availability() != RuntimeEnvironmentAvailability.AVAILABLE) {
            log.warn("bounded-V1 environment readiness is {}: {}",
                    result.availability(), result.diagnostics());
        }
        return result.availability();
    }

    @Override
    public SandboxRuntimeAvailability observeSandboxReadiness() {
        BubblewrapSandboxDetection detection;
        try {
            detection = BubblewrapSandboxCapabilityDetector.detect(configuredBubblewrap);
        } catch (RuntimeException indeterminate) {
            // The probe could not be carried out at all: no trustworthy conclusion, so fail closed.
            log.warn("bubblewrap capability probe was inconclusive", indeterminate);
            return SandboxRuntimeAvailability.UNKNOWN;
        }
        SandboxRuntimeAvailability availability = sandboxAvailability(detection);
        if (availability != SandboxRuntimeAvailability.AVAILABLE) {
            log.warn("bounded-V1 sandbox readiness is {}: {}",
                    availability, detection.diagnostic());
        }
        return availability;
    }

    /**
     * Maps the real bubblewrap detection to the readiness verdict. A launcher is present only when the
     * production-shape probe passed, so its presence is the sole {@code AVAILABLE} evidence; an absent
     * binary or a failed production-shape probe is a real, deterministic inability
     * ({@code UNAVAILABLE}), never {@code AVAILABLE}.
     */
    static SandboxRuntimeAvailability sandboxAvailability(BubblewrapSandboxDetection detection) {
        Objects.requireNonNull(detection, "detection");
        return detection.launcher().isPresent()
                ? SandboxRuntimeAvailability.AVAILABLE
                : SandboxRuntimeAvailability.UNAVAILABLE;
    }
}
