package com.example.platform.extension.domain;

/**
 * Plugin capability descriptor (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>Stable plugin-facing capability identity. The capability ID is a stable
 * string (e.g. {@code media.render}) — never a Java class name, Spring bean
 * name or implementation key. The capability contract version is independent
 * of the plugin version and platform API version.</p>
 *
 * <p>One plugin may declare multiple capabilities (e.g. {@code media.render}
 * and {@code subtitle.burn-in} for the same render provider). Capability IDs
 * are unique within one plugin.</p>
 *
 * @param capabilityId             stable capability ID (reverse-dns style)
 * @param capabilityContractVersion capability contract version, separate from plugin version
 * @param operationFamily          grouping family (e.g. {@code "render"})
 * @param inputReferenceType       handled-object type ID the capability consumes
 * @param outputReferenceType      output reference type (e.g. {@code "ArtifactReference"})
 * @param invocationMode           invocation style; SYNC_ONLY for P1
 */
public record CapabilityDescriptor(
        String capabilityId,
        String capabilityContractVersion,
        String operationFamily,
        String inputReferenceType,
        String outputReferenceType,
        InvocationMode invocationMode) {

    /** Invocation styles supported by P1. */
    public enum InvocationMode {
        SYNC_ONLY
    }

    public CapabilityDescriptor {
        if (capabilityId == null) {
            throw new NullPointerException("capabilityId must not be null");
        }
        if (capabilityContractVersion == null) {
            throw new NullPointerException("capabilityContractVersion must not be null");
        }
        if (operationFamily == null) {
            throw new NullPointerException("operationFamily must not be null");
        }
        if (inputReferenceType == null) {
            throw new NullPointerException("inputReferenceType must not be null");
        }
        if (outputReferenceType == null) {
            throw new NullPointerException("outputReferenceType must not be null");
        }
        if (invocationMode == null) {
            throw new NullPointerException("invocationMode must not be null");
        }
        capabilityId = capabilityId.trim();
    }
}
