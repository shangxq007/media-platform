package com.example.platform.web.effects;

import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.entitlement.domain.EntitlementPolicy;
import com.example.platform.render.api.port.EffectEntitlementPort;
import com.example.platform.render.infrastructure.EffectDescriptor;
import com.example.platform.render.infrastructure.EffectMappingService;
import com.example.platform.render.infrastructure.effects.EffectProviderRouter;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class EffectEntitlementAdapter implements EffectEntitlementPort {

    private final EntitlementPolicyService policyService;
    private final EffectMappingService effectMapping;
    private final EffectProviderRouter effectProviderRouter;

    public EffectEntitlementAdapter(EntitlementPolicyService policyService,
                                    EffectMappingService effectMapping,
                                    EffectProviderRouter effectProviderRouter) {
        this.policyService = policyService;
        this.effectMapping = effectMapping;
        this.effectProviderRouter = effectProviderRouter;
    }

    @Override
    public void validateEffectAccess(String tenantId, String tier, List<String> effectKeys,
                                       List<String> packIds) {
        EntitlementPolicy policy = policyService.getPolicy(tenantId);
        String effectiveTier = tier != null && !tier.isBlank() ? tier.toUpperCase() : policy.tier();

        if (packIds != null) {
            for (String packId : packIds) {
                if (packId != null && !packId.isBlank() && !policy.isEffectPackAllowed(packId)) {
                    throw new IllegalArgumentException("当前等级不可用特效包: " + packId);
                }
            }
        }

        Set<String> allowedProviders = policy.allowedProviders() != null
                ? policy.allowedProviders()
                : Set.of();

        if (effectKeys != null) {
            for (String effectKey : effectKeys) {
                EffectDescriptor descriptor = effectMapping.getDescriptor(effectKey)
                        .orElseThrow(() -> new IllegalArgumentException("未知特效: " + effectKey));
                if (descriptor.allowedTiers() != null
                        && !descriptor.allowedTiers().isEmpty()
                        && !descriptor.allowedTiers().contains(effectiveTier)) {
                    throw new IllegalArgumentException("当前等级不可用特效: " + effectKey);
                }
                effectProviderRouter.resolveProviderForEffect(effectKey, allowedProviders)
                        .ifPresent(providerKey -> {
                            if (!policy.isProviderAllowed(providerKey)) {
                                throw new IllegalArgumentException(
                                        "当前等级不可用渲染引擎: " + providerKey + " (特效: " + effectKey + ")");
                            }
                        });
            }
            if (effectProviderRouter.requiresNatronPipeline(effectKeys, allowedProviders)
                    && !policy.isProviderAllowed("natron")) {
                throw new IllegalArgumentException("当前等级不可用 Natron 合成 Worker");
            }
        }
    }
}
