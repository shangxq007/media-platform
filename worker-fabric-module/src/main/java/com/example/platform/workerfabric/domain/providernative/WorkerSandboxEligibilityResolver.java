package com.example.platform.workerfabric.domain.providernative;

import com.example.platform.sandbox.SandboxExecutionRequirement;
import com.example.platform.sandbox.SandboxExecutionResolver;
import com.example.platform.sandbox.SandboxFailure;
import com.example.platform.sandbox.SandboxFailureCode;
import com.example.platform.sandbox.SandboxResolution;
import com.example.platform.sandbox.SandboxRuntimeCapabilities;
import com.example.platform.workerfabric.domain.SandboxRuntimeAvailability;
import com.example.platform.workerfabric.domain.SandboxRuntimeRequirement;
import java.util.Set;

/**
 * Worker-owned eligibility composition over technology-neutral sandbox capability evidence.
 */
public final class WorkerSandboxEligibilityResolver {
    private WorkerSandboxEligibilityResolver() {}

    public static SandboxResolution resolve(
            SandboxRuntimeRequirement coarseRequirement,
            SandboxRuntimeAvailability coarseAvailability,
            SandboxExecutionRequirement requirement,
            SandboxRuntimeCapabilities runtime) {
        if (coarseRequirement != SandboxRuntimeRequirement.REQUIRED) {
            return new SandboxResolution.Rejected(SandboxFailure.of(
                    SandboxFailureCode.SANDBOX_POLICY_UNSATISFIABLE,
                    "effective sandbox execution requires REQUIRED runtime semantics",
                    Set.of()));
        }
        SandboxRuntimeAvailability evidenceAvailability = runtime.available()
                ? SandboxRuntimeAvailability.AVAILABLE
                : SandboxRuntimeAvailability.UNAVAILABLE;
        if (coarseAvailability != evidenceAvailability) {
            return new SandboxResolution.Rejected(SandboxFailure.of(
                    SandboxFailureCode.SANDBOX_UNAVAILABLE,
                    "coarse sandbox availability disagrees with capability evidence",
                    Set.of()));
        }
        return SandboxExecutionResolver.resolve(requirement, runtime);
    }
}
