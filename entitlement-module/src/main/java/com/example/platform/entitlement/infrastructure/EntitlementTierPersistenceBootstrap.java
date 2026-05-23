package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.app.EntitlementPolicyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnBean({JdbcTemplate.class, TenantTierJdbcRepository.class})
public class EntitlementTierPersistenceBootstrap {

    private final TenantTierJdbcRepository tierRepository;
    private final EntitlementPolicyService policyService;

    public EntitlementTierPersistenceBootstrap(TenantTierJdbcRepository tierRepository,
                                               EntitlementPolicyService policyService) {
        this.tierRepository = tierRepository;
        this.policyService = policyService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void hydrateTiers() {
        for (Map.Entry<String, String> entry : tierRepository.loadAll()) {
            policyService.hydrateTier(entry.getKey(), entry.getValue());
        }
    }
}
