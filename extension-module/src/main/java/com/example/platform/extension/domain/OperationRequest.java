package com.example.platform.extension.domain;

/**
 * Matching request (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>Reduced internal model consumed by the deterministic matcher. The tenant
 * enablement context is a policy-port result; the selection policy context may
 * carry an explicit plugin ID/version override. Matching input and output use
 * stable IDs and value types.</p>
 *
 * @param requiredCapabilityId              required capability ID (e.g. {@code media.render})
 * @param requiredCapabilityContractVersion required capability contract version (e.g. {@code "1"})
 * @param handledObjectTypeId               required handled-object type ID (e.g. {@code RenderExecutionPlan})
 * @param tenantEnablementContext           policy-port result (nullable; default-enabled for trusted internal)
 * @param selectionPolicyContext            explicit plugin ID/version override (nullable)
 */
public record OperationRequest(
        String requiredCapabilityId,
        String requiredCapabilityContractVersion,
        String handledObjectTypeId,
        TenantEnablementContext tenantEnablementContext,
        SelectionPolicyContext selectionPolicyContext) {

    public OperationRequest {
        if (requiredCapabilityId == null) {
            throw new NullPointerException("requiredCapabilityId must not be null");
        }
        if (requiredCapabilityContractVersion == null) {
            throw new NullPointerException("requiredCapabilityContractVersion must not be null");
        }
        if (handledObjectTypeId == null) {
            throw new NullPointerException("handledObjectTypeId must not be null");
        }
    }

    /**
     * Tenant enablement context produced by the {@code PluginTenantEnablementPolicy}
     * port. P1 default: TRUSTED_INTERNAL_ENABLED (trusted internal plugins enabled
     * for all tenants).
     */
    public record TenantEnablementContext(String tenantId, boolean enabled) {
    }

    /** Explicit selection override (plugin ID and optional version). */
    public record SelectionPolicyContext(String explicitPluginId, String explicitPluginVersion) {
    }

    /** Default request with no tenant/selection context (trusted internal default). */
    public static OperationRequest of(
            String requiredCapabilityId,
            String requiredCapabilityContractVersion,
            String handledObjectTypeId) {
        return new OperationRequest(
                requiredCapabilityId,
                requiredCapabilityContractVersion,
                handledObjectTypeId,
                null,
                null);
    }
}
