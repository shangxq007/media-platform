package com.example.platform.extension.domain;

import com.example.platform.shared.capability.CapabilityParameterContract;
import java.util.Objects;

/**
 * Capability parameter-contract reference (CAPABILITY_OPERATION_PARAMETER_MODEL / E-2b).
 *
 * <p>Parallel to {@link CapabilityDescriptor} (which is left unchanged so the
 * frozen PLUGIN_CAPABILITY_REGISTRY_V1 contract and its existing callers are not
 * disturbed). This descriptor adds the typed parameter contract to a capability:
 * the {@link CapabilityParameterContract} variant that carries the capability's
 * parameter values, so a resolver can check
 * {@code parameterType.isInstance(value)} exactly as the operation resolver does
 * for {@code OperationDefinition.parameterType}.</p>
 *
 * <p>Reference types remain coarse input/output contract names for
 * matching/availability projection; they are not a parameter schema.</p>
 *
 * @param capabilityId        stable capability identity
 * @param contractVersion     capability contract version (independent of plugin/impl versions)
 * @param parameterType       typed parameter contract variant for this capability
 * @param inputReferenceType  coarse input contract name (e.g. {@code MediaFrameSourceRef})
 * @param outputReferenceType coarse output contract name (e.g. {@code RasterFrameImage})
 */
public record CapabilityContractDescriptor(
        CapabilityId capabilityId,
        ContractVersion contractVersion,
        Class<? extends CapabilityParameterContract> parameterType,
        String inputReferenceType,
        String outputReferenceType) {

    public CapabilityContractDescriptor {
        Objects.requireNonNull(capabilityId, "capabilityId");
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(parameterType, "parameterType");
        Objects.requireNonNull(inputReferenceType, "inputReferenceType");
        Objects.requireNonNull(outputReferenceType, "outputReferenceType");
        if (inputReferenceType.isBlank()) {
            throw new IllegalArgumentException("inputReferenceType must not be blank");
        }
        if (outputReferenceType.isBlank()) {
            throw new IllegalArgumentException("outputReferenceType must not be blank");
        }
    }
}
