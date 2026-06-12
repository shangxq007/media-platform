package com.example.platform.federation.graphql.dataloader;

import com.example.platform.billing.app.UsageMeteringService;
import com.example.platform.billing.domain.UsageRecord;
import org.dataloader.MappedBatchLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * DataLoader for billing usage records, keyed by tenant ID.
 *
 * <p>This loader does NOT manipulate {@code TenantContext} on async threads.
 * Instead, it passes the tenant ID explicitly to {@code UsageMeteringService.getUsageByTenant(tenantId)},
 * which already accepts tenantId as a parameter. This avoids ThreadLocal leakage on shared
 * thread pools (e.g., ForkJoinPool.commonPool).
 *
 * <p>If downstream code changes to require TenantContext, a dedicated executor with
 * proper context propagation must be used instead.
 */
@Component
public class BillingUsageDataLoader implements MappedBatchLoader<String, List<Map<String, Object>>> {
    private static final Logger log = LoggerFactory.getLogger(BillingUsageDataLoader.class);

    private final UsageMeteringService usageMeteringService;

    public BillingUsageDataLoader(UsageMeteringService usageMeteringService) {
        this.usageMeteringService = usageMeteringService;
    }

    @Override
    public CompletionStage<Map<String, List<Map<String, Object>>>> load(Set<String> keys) {
        log.debug("Batch loading usage records for {} tenant IDs", keys.size());
        return CompletableFuture.supplyAsync(() -> {
            Map<String, List<Map<String, Object>>> result = new HashMap<>();
            for (String tenantId : keys) {
                try {
                    List<Map<String, Object>> records = new ArrayList<>();
                    var usageRecords = usageMeteringService.getUsageByTenant(tenantId);
                    for (var r : usageRecords) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", r.recordId());
                        map.put("meterKey", r.meterKey());
                        map.put("quantity", r.quantity());
                        map.put("unit", r.unit());
                        map.put("recordedAt", r.recordedAt() != null ? r.recordedAt().toString() : "");
                        records.add(map);
                    }
                    result.put(tenantId, records);
                } catch (Exception e) {
                    log.warn("Failed to load usage for tenant {}: {}", tenantId, e.getMessage());
                    result.put(tenantId, new ArrayList<>());
                }
            }
            return result;
        });
    }
}
