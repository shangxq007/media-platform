package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.app.EntitlementService;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.jooq.DSLContext;

@Component

public class EntitlementPersistenceBootstrap {

    private final EntitlementGrantRepository grantRepository;
    private final EntitlementService entitlementService;

    public EntitlementPersistenceBootstrap(EntitlementGrantRepository grantRepository,
                                           EntitlementService entitlementService) {
        this.grantRepository = grantRepository;
        this.entitlementService = entitlementService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void hydrateGrants() {
        for (EntitlementGrantRepository.EntitlementGrantRecord grant : grantRepository.findAllActive()) {
            entitlementService.hydrateGrant(grant.subjectId(), grant.bundleCode(), grant.quotaProfileCode());
        }
    }
}
