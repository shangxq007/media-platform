package com.example.platform.execution.domain;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Capability requirements for an execution step.
 *
 * <p>Immutable value object describing non-resource capabilities that an
 * execution provider must support to execute this step. Examples include
 * codec support, hardware acceleration, privacy class, and region constraints.
 */
public record ExecutionCapabilityRequirement(
        String capabilityId,
        String minimumVersion,
        Set<String> requiredFeatures,
        String qualityProfile,
        String privacyClass,
        String regionPolicy
) implements Serializable {

    public ExecutionCapabilityRequirement {
        Objects.requireNonNull(capabilityId, "capabilityId");
        if (capabilityId.isBlank()) throw new IllegalArgumentException("capabilityId must not be blank");
        // minimumVersion, qualityProfile, privacyClass, regionPolicy may be null (no constraint)
        requiredFeatures = requiredFeatures != null ? Set.copyOf(requiredFeatures) : Set.of();
    }

    /**
     * Creates a capability requirement with no version or feature constraints.
     */
    public static ExecutionCapabilityRequirement of(String capabilityId) {
        return new ExecutionCapabilityRequirement(capabilityId, null, Set.of(), null, null, null);
    }

    /**
     * Creates a capability requirement with a minimum version.
     */
    public static ExecutionCapabilityRequirement withMinVersion(String capabilityId, String minimumVersion) {
        return new ExecutionCapabilityRequirement(capabilityId, minimumVersion, Set.of(), null, null, null);
    }

    /**
     * Creates a capability requirement with required features.
     */
    public static ExecutionCapabilityRequirement withFeatures(String capabilityId, Set<String> requiredFeatures) {
        return new ExecutionCapabilityRequirement(capabilityId, null, requiredFeatures, null, null, null);
    }

    /**
     * Returns true if this requirement specifies a minimum version constraint.
     */
    public boolean hasVersionConstraint() {
        return minimumVersion != null && !minimumVersion.isBlank();
    }

    /**
     * Returns true if this requirement specifies feature constraints.
     */
    public boolean hasFeatureConstraints() {
        return !requiredFeatures.isEmpty();
    }

    /**
     * Returns a canonical string representation for deterministic hashing.
     */
    public String canonicalForm() {
        return "capability{" +
                "id=" + capabilityId +
                ",minVer=" + (minimumVersion != null ? minimumVersion : "") +
                ",features=" + requiredFeatures.stream().sorted().toList() +
                ",quality=" + (qualityProfile != null ? qualityProfile : "") +
                ",privacy=" + (privacyClass != null ? privacyClass : "") +
                ",region=" + (regionPolicy != null ? regionPolicy : "") +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
