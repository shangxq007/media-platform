package com.example.platform.web.effects;

import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.render.api.port.EffectEntitlementPort;
import com.example.platform.render.infrastructure.EffectMappingService;
import com.example.platform.render.infrastructure.effects.EffectProviderRouter;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class EffectEntitlementAdapter implements EffectEntitlementPort {

    private final EntitlementService entitlementService;
    private final EffectMappingService effectMapping;
    private final EffectProviderRouter effectProviderRouter;

    public EffectEntitlementAdapter(EntitlementService entitlementService,
                                    EffectMappingService effectMapping,
                                    EffectProviderRouter effectProviderRouter) {
        this.entitlementService = entitlementService;
        this.effectMapping = effectMapping;
        this.effectProviderRouter = effectProviderRouter;
    }

    @Override
    public void validateEffectAccess(String tenantId, String tier, List<String> effectKeys,
                                       List<String> packIds) {
        PrincipalRef principal = PrincipalRef.tenantScoped(
                tenantId, PrincipalType.ORGANIZATION, tenantId);

        if (packIds != null) {
            for (String packId : packIds) {
                if (packId != null && !packId.isBlank()
                        && !entitlementService.checkFeature(principal, "effect.pack." + packId).allowed()) {
                    throw new IllegalArgumentException("未授予特效包权限: " + packId);
                }
            }
        }

        if (effectKeys != null) {
            for (String effectKey : effectKeys) {
                effectMapping.getDescriptor(effectKey)
                        .orElseThrow(() -> new IllegalArgumentException("未知特效: " + effectKey));
                if (!entitlementService.checkFeature(principal, "effect." + effectKey).allowed()) {
                    throw new IllegalArgumentException("未授予特效权限: " + effectKey);
                }
                effectProviderRouter.resolveProviderForEffect(effectKey, Set.of());
            }
        }
    }
}
