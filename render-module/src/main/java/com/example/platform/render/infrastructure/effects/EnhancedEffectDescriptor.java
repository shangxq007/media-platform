package com.example.platform.render.infrastructure.effects;

import com.example.platform.render.infrastructure.EffectParameterSchema;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enhanced effect descriptor with full taxonomy, validation, and entitlement metadata.
 */
public record EnhancedEffectDescriptor(
    String effectId,
    String displayName,
    String description,
    EffectCategory category,
    Set<EffectTargetType> supportedTargets,
    EffectStatus status,
    EffectStage stage,
    EffectBackendKind backendKind,
    Set<String> requiredCapabilities,
    List<EffectParameterSchema> parameterSchema,
    String entitlementKey,
    boolean defaultEnabled,
    String notes
) {
    /**
     * Check if this effect can be applied to the given target type.
     */
    public boolean supportsTarget(EffectTargetType targetType) {
        return supportedTargets.contains(targetType);
    }
    
    /**
     * Check if this effect is available for dispatch (not stub/planned/deprecated).
     */
    public boolean isDispatchEligible() {
        return status == EffectStatus.IMPLEMENTED || status == EffectStatus.PARTIAL;
    }
    
    /**
     * Check if this effect requires premium entitlement.
     */
    public boolean requiresEntitlement() {
        return entitlementKey != null && !entitlementKey.isEmpty();
    }
    
    /**
     * Check if this effect requires specific provider capabilities.
     */
    public boolean requiresCapabilities() {
        return requiredCapabilities != null && !requiredCapabilities.isEmpty();
    }
}
