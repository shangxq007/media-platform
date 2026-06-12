package com.example.platform.render.infrastructure.effects;

import com.example.platform.render.infrastructure.EffectMappingService;
import com.example.platform.render.infrastructure.EffectDescriptor;
import com.example.platform.render.infrastructure.RenderProviderCapability;
import com.example.platform.render.infrastructure.RenderProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service for enforcing effect policies, entitlements, and provider capability matching.
 */
@Service
public class EffectPolicyService {
    private static final Logger log = LoggerFactory.getLogger(EffectPolicyService.class);

    private final EffectMappingService effectMapping;
    private final RenderProviderRegistry providerRegistry;

    public EffectPolicyService(EffectMappingService effectMapping, RenderProviderRegistry providerRegistry) {
        this.effectMapping = effectMapping;
        this.providerRegistry = providerRegistry;
    }

    /**
     * Validate that an effect can be applied with the given provider.
     * 
     * @param effectKey the effect key
     * @param providerKey the provider key
     * @param tenantTier the tenant's entitlement tier
     * @return validation result with errors if any
     */
    public EffectValidationResult validateEffectForProvider(String effectKey, String providerKey, String tenantTier) {
        List<String> errors = new ArrayList<>();
        
        // Check if effect exists
        Optional<EffectDescriptor> descriptorOpt = effectMapping.getDescriptor(effectKey);
        if (descriptorOpt.isEmpty()) {
            errors.add("Unknown effect: " + effectKey);
            return new EffectValidationResult(false, errors);
        }
        
        EffectDescriptor descriptor = descriptorOpt.get();
        
        // Check if effect is enabled
        if (!descriptor.isEffect()) {
            errors.add("Effect is disabled: " + effectKey);
        }
        
        // Check tier entitlement
        if (descriptor.allowedTiers() != null && !descriptor.allowedTiers().isEmpty()) {
            if (tenantTier == null || !descriptor.allowedTiers().contains(tenantTier)) {
                errors.add("Effect '" + effectKey + "' requires tier: " + descriptor.allowedTiers());
            }
        }
        
        // Check provider capability
        Optional<RenderProviderCapability> providerCap = providerRegistry.getCapability(providerKey);
        if (providerCap.isEmpty()) {
            errors.add("Unknown provider: " + providerKey);
            return new EffectValidationResult(false, errors);
        }
        
        RenderProviderCapability capability = providerCap.get();
        
        // Check if provider supports this effect
        if (!capability.supportsEffect(effectKey)) {
            errors.add("Provider '" + providerKey + "' does not support effect: " + effectKey);
        }
        
        // Check provider status
        if (!capability.isProduction() && !capability.isPoc()) {
            errors.add("Provider '" + providerKey + "' is not available (status: " + capability.status() + ")");
        }
        
        return new EffectValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Validate parameters for an effect.
     */
    public EffectValidationResult validateEffectParameters(String effectKey, Map<String, Object> params) {
        List<String> errors = new ArrayList<>();
        
        Optional<EffectDescriptor> descriptorOpt = effectMapping.getDescriptor(effectKey);
        if (descriptorOpt.isEmpty()) {
            errors.add("Unknown effect: " + effectKey);
            return new EffectValidationResult(false, errors);
        }
        
        EffectDescriptor descriptor = descriptorOpt.get();
        List<String> paramErrors = EffectParameterValidator.validate(params, descriptor.paramSchemas());
        errors.addAll(paramErrors);
        
        return new EffectValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Get all effects available for a given provider and tenant tier.
     * Uses tier hierarchy: FREE < PRO < TEAM < ENTERPRISE
     */
    public List<EffectDescriptor> getAvailableEffects(String providerKey, String tenantTier) {
        List<EffectDescriptor> available = new ArrayList<>();
        int tenantTierLevel = getTierLevel(tenantTier);
        
        for (EffectDescriptor descriptor : effectMapping.getAllDescriptors()) {
            // Check tier hierarchy
            if (descriptor.allowedTiers() != null && !descriptor.allowedTiers().isEmpty()) {
                int minTierLevel = descriptor.allowedTiers().stream()
                        .mapToInt(this::getTierLevel)
                        .min()
                        .orElse(0);
                if (tenantTierLevel < minTierLevel) {
                    continue;
                }
            }
            
            // Check if effect is enabled
            if (descriptor.isEffect() != null && !descriptor.isEffect()) {
                continue;
            }
            
            // Check provider support
            if (descriptor.providerKeys() != null && descriptor.providerKeys().contains(providerKey)) {
                available.add(descriptor);
            }
        }
        
        return available;
    }
    
    private int getTierLevel(String tier) {
        if (tier == null) return 0;
        return switch (tier.toUpperCase()) {
            case "FREE" -> 0;
            case "PRO" -> 1;
            case "TEAM" -> 2;
            case "ENTERPRISE" -> 3;
            default -> 0;
        };
    }

    /**
     * Validation result for effect operations.
     */
    public record EffectValidationResult(boolean valid, List<String> errors) {}
}
