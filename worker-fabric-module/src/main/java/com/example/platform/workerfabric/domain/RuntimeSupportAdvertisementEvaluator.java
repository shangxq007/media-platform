package com.example.platform.workerfabric.domain;

import java.util.Objects;
import java.util.Optional;

/** Central validator for worker-supplied static support candidate evidence. */
public final class RuntimeSupportAdvertisementEvaluator {

    private RuntimeSupportAdvertisementEvaluator() {}

    public static RuntimeSupportAdvertisementDecision evaluate(
            WorkerRuntimeDescriptor authoritativeRuntime,
            Optional<WorkerRuntimeSupportAdvertisement> advertisement,
            Optional<WorkerRuntimeSupportRequirement> requirement) {
        Objects.requireNonNull(authoritativeRuntime, "authoritativeRuntime");
        Objects.requireNonNull(advertisement, "advertisement");
        Objects.requireNonNull(requirement, "requirement");
        if (requirement.isEmpty()) {
            return rejected(RuntimeSupportAdvertisementReason.REQUIREMENT_MISSING);
        }
        if (advertisement.isEmpty()) {
            return rejected(RuntimeSupportAdvertisementReason.MISSING);
        }
        WorkerRuntimeSupportAdvertisement candidate = advertisement.orElseThrow();
        WorkerRuntimeSupportRequirement exact = requirement.orElseThrow();
        if (!candidate.runtimeId().equals(authoritativeRuntime.id())
                || candidate.runtimeKind() != authoritativeRuntime.lifecycleKind()
                || candidate.runtimeKind() != exact.requiredRuntimeKind()) {
            return rejected(RuntimeSupportAdvertisementReason.RUNTIME_MISMATCH);
        }
        if (!candidate.staticSupportEvidence().containsKey(exact.supportIdentifier())) {
            return rejected(RuntimeSupportAdvertisementReason.UNSUPPORTED);
        }
        return new RuntimeSupportAdvertisementDecision(
                true, RuntimeSupportAdvertisementReason.ACCEPTED_CANDIDATE_EVIDENCE);
    }

    private static RuntimeSupportAdvertisementDecision rejected(
            RuntimeSupportAdvertisementReason reason) {
        return new RuntimeSupportAdvertisementDecision(false, reason);
    }
}
