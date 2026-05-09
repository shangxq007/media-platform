package com.example.platform.render.app;

import com.example.platform.shared.events.QuotaCheckRequestedEvent;
import com.example.platform.shared.events.QuotaCheckResultEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RenderQuotaService {

    private final ApplicationEventPublisher eventPublisher;
    private final QuotaUsageRepository quotaUsageRepository;
    private static final int DEFAULT_LIMIT = 100;

    public RenderQuotaService(ApplicationEventPublisher eventPublisher,
            QuotaUsageRepository quotaUsageRepository) {
        this.eventPublisher = eventPublisher;
        this.quotaUsageRepository = quotaUsageRepository;
    }

    public boolean checkQuota(String tenantId, String featureCode, int requestedAmount) {
        eventPublisher.publishEvent(new QuotaCheckRequestedEvent(tenantId, featureCode, requestedAmount));
        int currentUsage = quotaUsageRepository.getUsage(tenantId, featureCode);
        boolean allowed = (currentUsage + requestedAmount) <= DEFAULT_LIMIT;
        int remaining = DEFAULT_LIMIT - currentUsage;
        eventPublisher.publishEvent(new QuotaCheckResultEvent(tenantId, featureCode, requestedAmount, allowed, Math.max(0, remaining)));
        return allowed;
    }

    public void consumeQuota(String tenantId, String featureCode, int amount) {
        quotaUsageRepository.incrementUsage(tenantId, featureCode, amount);
    }

    public Map<String, Integer> getUsage(String tenantId) {
        return quotaUsageRepository.getUsageByTenant(tenantId);
    }
}
