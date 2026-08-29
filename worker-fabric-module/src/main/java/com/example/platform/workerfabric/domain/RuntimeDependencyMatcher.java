package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderImplementationId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/** Pure exact matcher for implementation-local requirements and freshness-bound observations. */
public final class RuntimeDependencyMatcher {

    private RuntimeDependencyMatcher() {}

    public static RuntimeDependencyMatchResult match(
            ProviderImplementationId expectedProviderImplementationId,
            WorkerRuntimeId expectedWorkerRuntimeId,
            Optional<DeviceId> expectedDeviceId,
            List<RuntimeDependencyRequirement> requirements,
            Optional<RuntimeDependencyObservation> observation,
            Instant assessedAt) {
        Objects.requireNonNull(expectedProviderImplementationId, "expectedProviderImplementationId");
        Objects.requireNonNull(expectedWorkerRuntimeId, "expectedWorkerRuntimeId");
        Objects.requireNonNull(expectedDeviceId, "expectedDeviceId");
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(observation, "observation");
        Objects.requireNonNull(assessedAt, "assessedAt");

        TreeSet<RuntimeDependencyMatchReason> reasons = new TreeSet<>();
        ArrayList<RuntimeDependencyRequirement> canonicalRequirements = new ArrayList<>();
        HashSet<RuntimeDependencyCoordinate> requirementCoordinates = new HashSet<>();
        for (RuntimeDependencyRequirement requirement : requirements) {
            Objects.requireNonNull(requirement, "requirements entry");
            if (!requirement.providerImplementationId().equals(expectedProviderImplementationId)
                    || !requirementCoordinates.add(requirement.coordinate())) {
                reasons.add(RuntimeDependencyMatchReason.forDependency(
                        RuntimeDependencyMatchReasonCode.INCOMPLETE_CRITICAL_EVIDENCE,
                        requirement.coordinate()));
                continue;
            }
            canonicalRequirements.add(requirement);
        }
        canonicalRequirements.sort((left, right) -> left.coordinate().compareTo(right.coordinate()));

        if (observation.isEmpty()) {
            reasons.add(RuntimeDependencyMatchReason.general(
                    RuntimeDependencyMatchReasonCode.INCOMPLETE_CRITICAL_EVIDENCE));
            return result(reasons);
        }

        RuntimeDependencyObservation exact = observation.orElseThrow();
        if (!exact.providerImplementationId().equals(expectedProviderImplementationId)) {
            reasons.add(RuntimeDependencyMatchReason.general(
                    RuntimeDependencyMatchReasonCode.PROVIDER_IMPLEMENTATION_MISMATCH));
        }
        if (!exact.workerRuntimeId().equals(expectedWorkerRuntimeId)) {
            reasons.add(RuntimeDependencyMatchReason.general(
                    RuntimeDependencyMatchReasonCode.WORKER_RUNTIME_MISMATCH));
        }
        if (!exact.deviceId().equals(expectedDeviceId)) {
            reasons.add(RuntimeDependencyMatchReason.general(
                    RuntimeDependencyMatchReasonCode.DEVICE_BINDING_MISMATCH));
        }
        if (!exact.probeSchemaVersion().equals(RuntimeDependencyProbeSchemaVersion.CURRENT)) {
            reasons.add(RuntimeDependencyMatchReason.general(
                    RuntimeDependencyMatchReasonCode.PROBE_SCHEMA_MISMATCH));
        }
        if (!exact.isFreshAt(assessedAt)) {
            reasons.add(RuntimeDependencyMatchReason.general(
                    RuntimeDependencyMatchReasonCode.STALE_OBSERVATION));
        }

        Map<RuntimeDependencyCoordinate, RuntimeDependencyObservedDependency> observedByCoordinate =
                new HashMap<>();
        exact.dependencies().forEach(dependency ->
                observedByCoordinate.put(dependency.coordinate(), dependency));
        for (RuntimeDependencyRequirement requirement : canonicalRequirements) {
            RuntimeDependencyObservedDependency observed =
                    observedByCoordinate.get(requirement.coordinate());
            if (observed == null) {
                reasons.add(RuntimeDependencyMatchReason.forDependency(
                        RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_MISSING,
                        requirement.coordinate()));
                continue;
            }
            if (!requirement.versionConstraint().matches(observed.version())) {
                reasons.add(RuntimeDependencyMatchReason.forDependency(
                        RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_VERSION_INCOMPATIBLE,
                        requirement.coordinate()));
            }
            if (requirement.abiConstraint().isPresent()
                    && !requirement.abiConstraint().equals(observed.abi())) {
                reasons.add(RuntimeDependencyMatchReason.forDependency(
                        RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_ABI_INCOMPATIBLE,
                        requirement.coordinate()));
            }
            if (!observed.enabledFeatures().containsAll(requirement.requiredFeatures())) {
                reasons.add(RuntimeDependencyMatchReason.forDependency(
                        RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_FEATURE_MISSING,
                        requirement.coordinate()));
            }
            if (!observed.enabledBuildRuntimeFlags()
                    .containsAll(requirement.requiredBuildRuntimeFlags())) {
                reasons.add(RuntimeDependencyMatchReason.forDependency(
                        RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_BUILD_RUNTIME_FLAG_MISSING,
                        requirement.coordinate()));
            }
        }
        return result(reasons);
    }

    private static RuntimeDependencyMatchResult result(TreeSet<RuntimeDependencyMatchReason> reasons) {
        List<RuntimeDependencyMatchReason> ordered = List.copyOf(reasons);
        if (ordered.isEmpty()) {
            return new RuntimeDependencyMatchResult(RuntimeDependencyMatchStatus.CAN_MATCH, ordered);
        }
        RuntimeDependencyMatchStatus status = ordered.stream()
                        .anyMatch(reason -> reason.code().unknownEvidence())
                ? RuntimeDependencyMatchStatus.UNKNOWN_FAIL_CLOSED
                : RuntimeDependencyMatchStatus.CANNOT_MATCH;
        return new RuntimeDependencyMatchResult(status, ordered);
    }
}
