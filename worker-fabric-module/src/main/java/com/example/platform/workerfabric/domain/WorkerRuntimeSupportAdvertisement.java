package com.example.platform.workerfabric.domain;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable candidate evidence for statically installed runtime support.
 *
 * <p>This advertisement contains no liveness, capacity, reservation, usage, placement, or
 * execution authorization. Central validation must combine it with an exact provider requirement
 * and all authoritative mutable runtime evidence before assignment.
 */
public record WorkerRuntimeSupportAdvertisement(
        WorkerRuntimeId runtimeId,
        RuntimeLifecycleKind runtimeKind,
        Map<RuntimeSupportIdentifier, RuntimeSupportEvidence> staticSupportEvidence) {

    public WorkerRuntimeSupportAdvertisement {
        Objects.requireNonNull(runtimeId, "runtimeId");
        Objects.requireNonNull(runtimeKind, "runtimeKind");
        Objects.requireNonNull(staticSupportEvidence, "staticSupportEvidence");
        TreeMap<RuntimeSupportIdentifier, RuntimeSupportEvidence> canonical = new TreeMap<>();
        staticSupportEvidence.forEach((identifier, evidence) -> canonical.put(
                Objects.requireNonNull(identifier, "staticSupportEvidence key"),
                Objects.requireNonNull(evidence, "staticSupportEvidence value")));
        staticSupportEvidence = Collections.unmodifiableMap(canonical);
    }
}
