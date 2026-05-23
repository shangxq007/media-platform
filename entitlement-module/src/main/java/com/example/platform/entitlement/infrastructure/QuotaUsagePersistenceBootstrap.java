package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.app.QuotaUsageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(QuotaUsageJdbcRepository.class)
public class QuotaUsagePersistenceBootstrap {

    private final QuotaUsageJdbcRepository repository;
    private final QuotaUsageService quotaUsageService;

    public QuotaUsagePersistenceBootstrap(QuotaUsageJdbcRepository repository,
                                          QuotaUsageService quotaUsageService) {
        this.repository = repository;
        this.quotaUsageService = quotaUsageService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void hydrate() {
        for (QuotaUsageJdbcRepository.UsageRow row : repository.loadAll()) {
            quotaUsageService.hydrateUsage(row.subjectId(), row.featureCode(), row.usageValue());
        }
    }
}
