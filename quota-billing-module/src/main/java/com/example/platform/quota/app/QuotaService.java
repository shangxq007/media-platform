package com.example.platform.quota.app;

import com.example.platform.quota.domain.QuotaBucket;
import com.example.platform.quota.domain.QuotaBucketStatus;
import com.example.platform.quota.domain.QuotaPolicy;
import com.example.platform.quota.domain.ThresholdEvent;
import com.example.platform.shared.Ids;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @deprecated EUMF-V1: semantic authority MERGED into entitlement-module Quota Authority
 *             (UCUO-ADR-009); physical retirement DEFERRED to PMPR-DDHV1. In-memory engine
 *             retained for existing render consumers this task.
 */
@Deprecated
@Service
public class QuotaService {

    private final Map<String, QuotaBucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, QuotaPolicy> policies = new ConcurrentHashMap<>();
    private final List<ThresholdEvent> thresholdEvents = new ArrayList<>();

    public Map<String, Object> overview() {
        return Map.of(
                "module", "quota-billing-module",
                "status", "read-only-compatibility",
                "description", "Deprecated quota configuration projection; usage writes are disabled.",
                "bucketCount", buckets.size(),
                "policyCount", policies.size(),
                "thresholdEventCount", thresholdEvents.size()
        );
    }

    public QuotaBucket createBucket(String tenantId, String featureCode, long limit, String period) {
        String id = Ids.newId("qbk");
        QuotaBucket bucket = new QuotaBucket(id, tenantId, featureCode, limit, period, 0L, Instant.now(), Instant.now());
        buckets.put(id, bucket);
        return bucket;
    }

    public QuotaBucketStatus getBucketStatus(String bucketId) {
        QuotaBucket bucket = buckets.get(bucketId);
        if (bucket == null) {
            throw new IllegalArgumentException("QuotaBucket not found: " + bucketId);
        }
        return QuotaBucketStatus.from(bucket);
    }

    public QuotaPolicy createPolicy(String name, String rules, double thresholdPercentage) {
        String id = Ids.newId("qpol");
        QuotaPolicy policy = new QuotaPolicy(id, name, rules, thresholdPercentage);
        policies.put(id, policy);
        return policy;
    }

    public List<ThresholdEvent> evaluateThresholds() {
        List<ThresholdEvent> newEvents = new ArrayList<>();
        for (QuotaBucket bucket : buckets.values()) {
            for (QuotaPolicy policy : policies.values()) {
                double ratio = bucket.usageRatio() * 100.0;
                if (ratio >= policy.thresholdPercentage()) {
                    String eventId = Ids.newId("tev");
                    ThresholdEvent event = new ThresholdEvent(eventId, bucket.id(), policy.thresholdPercentage(), Instant.now());
                    thresholdEvents.add(event);
                    newEvents.add(event);
                }
            }
        }
        return newEvents;
    }

    public List<ThresholdEvent> getThresholdEvents() {
        return List.copyOf(thresholdEvents);
    }

    public List<QuotaBucket> getBucketsForTenant(String tenantId) {
        return buckets.values().stream()
                .filter(b -> b.tenantId().equals(tenantId))
                .toList();
    }

    public List<QuotaBucketSummary> getBucketSummariesForTenant(String tenantId) {
        return buckets.values().stream()
                .filter(b -> b.tenantId().equals(tenantId))
                .map(b -> new QuotaBucketSummary(
                        b.featureCode(),
                        b.currentUsage(),
                        b.limit(),
                        b.usageRatio(),
                        b.isExceeded()))
                .toList();
    }
}
