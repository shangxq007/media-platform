package com.example.platform.quota.app;

import com.example.platform.quota.domain.*;
import com.example.platform.shared.Ids;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuotaService {

    private final Map<String, QuotaBucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, UsageRecord> usageRecords = new ConcurrentHashMap<>();
    private final Map<String, QuotaPolicy> policies = new ConcurrentHashMap<>();
    private final List<ThresholdEvent> thresholdEvents = new ArrayList<>();
    private final Map<String, String> idempotencyIndex = new ConcurrentHashMap<>();

    public Map<String, Object> overview() {
        return Map.of(
                "module", "quota-billing-module",
                "status", "active",
                "description", "配额与计量模块，负责资源额度、用量汇总与阈值检查。",
                "bucketCount", buckets.size(),
                "usageRecordCount", usageRecords.size(),
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

    public UsageRecord recordUsage(String bucketId, long amount, String idempotencyKey) {
        QuotaBucket bucket = buckets.get(bucketId);
        if (bucket == null) {
            throw new IllegalArgumentException("QuotaBucket not found: " + bucketId);
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String existingRecordId = idempotencyIndex.get(idempotencyKey);
            if (existingRecordId != null) {
                return usageRecords.get(existingRecordId);
            }
        }

        String recordId = Ids.newId("usr");
        UsageRecord record = new UsageRecord(recordId, bucketId, amount, Instant.now(), idempotencyKey);
        usageRecords.put(recordId, record);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyIndex.put(idempotencyKey, recordId);
        }

        QuotaBucket updated = bucket.withUsage(bucket.currentUsage() + amount);
        buckets.put(bucketId, updated);

        return record;
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
