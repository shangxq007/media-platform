package com.example.platform.quota.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.quota.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class QuotaServiceTest {

    private QuotaService service;

    @BeforeEach
    void setUp() {
        service = new QuotaService();
    }

    @Test
    void overviewReturnsModuleInfo() {
        Map<String, Object> overview = service.overview();
        assertEquals("quota-billing-module", overview.get("module"));
        assertEquals("active", overview.get("status"));
    }

    @Test
    void createBucketReturnsBucketWithZeroUsage() {
        QuotaBucket bucket = service.createBucket("tenant-1", "render.job.create", 1000L, "monthly");

        assertNotNull(bucket);
        assertTrue(bucket.id().startsWith("qbk_"));
        assertEquals("tenant-1", bucket.tenantId());
        assertEquals("render.job.create", bucket.featureCode());
        assertEquals(1000L, bucket.limit());
        assertEquals(0L, bucket.currentUsage());
        assertEquals("monthly", bucket.period());
        assertFalse(bucket.isExceeded());
        assertEquals(0.0, bucket.usageRatio());
    }

    @Test
    void recordUsageIncrementsBucketUsage() {
        QuotaBucket bucket = service.createBucket("tenant-1", "render.job.create", 1000L, "monthly");

        UsageRecord record = service.recordUsage(bucket.id(), 100L, "idem-1");

        assertNotNull(record);
        assertTrue(record.id().startsWith("usr_"));
        assertEquals(bucket.id(), record.quotaBucketId());
        assertEquals(100L, record.amount());
        assertEquals("idem-1", record.idempotencyKey());
        assertNotNull(record.recordedAt());

        QuotaBucketStatus status = service.getBucketStatus(bucket.id());
        assertEquals(100L, status.currentUsage());
        assertEquals(0.1, status.usageRatio(), 0.001);
        assertFalse(status.exceeded());
    }

    @Test
    void recordUsageMultipleTimesAccumulates() {
        QuotaBucket bucket = service.createBucket("tenant-1", "render.job.create", 1000L, "monthly");

        service.recordUsage(bucket.id(), 100L, "idem-1");
        service.recordUsage(bucket.id(), 200L, "idem-2");
        service.recordUsage(bucket.id(), 300L, "idem-3");

        QuotaBucketStatus status = service.getBucketStatus(bucket.id());
        assertEquals(600L, status.currentUsage());
        assertEquals(0.6, status.usageRatio(), 0.001);
    }

    @Test
    void recordUsageWithDuplicateIdempotencyKeyReturnsExisting() {
        QuotaBucket bucket = service.createBucket("tenant-1", "render.job.create", 1000L, "monthly");

        UsageRecord record1 = service.recordUsage(bucket.id(), 100L, "idem-same");
        UsageRecord record2 = service.recordUsage(bucket.id(), 100L, "idem-same");

        assertEquals(record1.id(), record2.id());
        assertEquals(record1.amount(), record2.amount());

        QuotaBucketStatus status = service.getBucketStatus(bucket.id());
        assertEquals(100L, status.currentUsage());
    }

    @Test
    void recordUsageWithNullIdempotencyKeyCreatesNewRecord() {
        QuotaBucket bucket = service.createBucket("tenant-1", "render.job.create", 1000L, "monthly");

        UsageRecord record1 = service.recordUsage(bucket.id(), 100L, null);
        UsageRecord record2 = service.recordUsage(bucket.id(), 100L, null);

        assertNotEquals(record1.id(), record2.id());

        QuotaBucketStatus status = service.getBucketStatus(bucket.id());
        assertEquals(200L, status.currentUsage());
    }

    @Test
    void recordUsageForNonExistentBucketThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.recordUsage("nonexistent", 100L, "idem-1"));
    }

    @Test
    void getBucketStatusForNonExistentBucketThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.getBucketStatus("nonexistent"));
    }

    @Test
    void bucketIsExceededWhenUsageReachesLimit() {
        QuotaBucket bucket = service.createBucket("tenant-1", "render.job.create", 100L, "monthly");

        service.recordUsage(bucket.id(), 100L, "idem-1");

        QuotaBucketStatus status = service.getBucketStatus(bucket.id());
        assertTrue(status.exceeded());
        assertEquals(1.0, status.usageRatio(), 0.001);
    }

    @Test
    void bucketIsExceededWhenUsageExceedsLimit() {
        QuotaBucket bucket = service.createBucket("tenant-1", "render.job.create", 100L, "monthly");

        service.recordUsage(bucket.id(), 150L, "idem-1");

        QuotaBucketStatus status = service.getBucketStatus(bucket.id());
        assertTrue(status.exceeded());
    }

    @Test
    void createPolicyReturnsPolicy() {
        QuotaPolicy policy = service.createPolicy("warning-at-80", "{\"action\":\"notify\"}", 80.0);

        assertNotNull(policy);
        assertTrue(policy.id().startsWith("qpol_"));
        assertEquals("warning-at-80", policy.name());
        assertEquals("{\"action\":\"notify\"}", policy.rules());
        assertEquals(80.0, policy.thresholdPercentage());
    }

    @Test
    void evaluateThresholdsTriggersEventWhenUsageExceedsThreshold() {
        QuotaBucket bucket = service.createBucket("tenant-1", "render.job.create", 100L, "monthly");
        service.createPolicy("warning-at-80", "{\"action\":\"notify\"}", 80.0);

        service.recordUsage(bucket.id(), 85L, "idem-1");

        List<ThresholdEvent> events = service.evaluateThresholds();
        assertFalse(events.isEmpty());

        ThresholdEvent event = events.get(0);
        assertEquals(bucket.id(), event.quotaBucketId());
        assertEquals(80.0, event.thresholdPercentage());
        assertNotNull(event.triggeredAt());
    }

    @Test
    void evaluateThresholdsDoesNotTriggerWhenUsageBelowThreshold() {
        QuotaBucket bucket = service.createBucket("tenant-1", "render.job.create", 100L, "monthly");
        service.createPolicy("warning-at-80", "{\"action\":\"notify\"}", 80.0);

        service.recordUsage(bucket.id(), 50L, "idem-1");

        List<ThresholdEvent> events = service.evaluateThresholds();
        assertTrue(events.isEmpty());
    }

    @Test
    void evaluateThresholdsTriggersMultipleEventsForMultiplePolicies() {
        QuotaBucket bucket = service.createBucket("tenant-1", "render.job.create", 100L, "monthly");
        service.createPolicy("warning-at-50", "{\"action\":\"notify\"}", 50.0);
        service.createPolicy("warning-at-80", "{\"action\":\"notify\"}", 80.0);

        service.recordUsage(bucket.id(), 85L, "idem-1");

        List<ThresholdEvent> events = service.evaluateThresholds();
        assertEquals(2, events.size());
    }

    @Test
    void getThresholdEventsReturnsAllTriggeredEvents() {
        QuotaBucket bucket = service.createBucket("tenant-1", "render.job.create", 100L, "monthly");
        service.createPolicy("warning-at-50", "{\"action\":\"notify\"}", 50.0);

        service.recordUsage(bucket.id(), 60L, "idem-1");
        service.evaluateThresholds();

        List<ThresholdEvent> events = service.getThresholdEvents();
        assertEquals(1, events.size());
    }

    @Test
    void getBucketsForTenantReturnsOnlyMatchingBuckets() {
        service.createBucket("tenant-1", "render.job.create", 100L, "monthly");
        service.createBucket("tenant-1", "ai.model.standard", 500L, "monthly");
        service.createBucket("tenant-2", "render.job.create", 200L, "monthly");

        List<QuotaBucket> tenant1Buckets = service.getBucketsForTenant("tenant-1");
        assertEquals(2, tenant1Buckets.size());

        List<QuotaBucket> tenant2Buckets = service.getBucketsForTenant("tenant-2");
        assertEquals(1, tenant2Buckets.size());
    }

    @Test
    void quotaBucketWithUsageCreatesNewInstance() {
        QuotaBucket bucket = service.createBucket("tenant-1", "render.job.create", 1000L, "monthly");
        QuotaBucket updated = bucket.withUsage(500L);

        assertEquals(bucket.id(), updated.id());
        assertEquals(0L, bucket.currentUsage());
        assertEquals(500L, updated.currentUsage());
        assertEquals(bucket.limit(), updated.limit());
    }

    @Test
    void usageRecordValidationRequiresPositiveAmount() {
        assertThrows(IllegalArgumentException.class, () ->
                new UsageRecord("usr_1", "qbk_1", 0L, java.time.Instant.now(), null));
        assertThrows(IllegalArgumentException.class, () ->
                new UsageRecord("usr_1", "qbk_1", -1L, java.time.Instant.now(), null));
    }

    @Test
    void quotaPolicyValidationRequiresValidThreshold() {
        assertThrows(IllegalArgumentException.class, () ->
                new QuotaPolicy("p1", "test", "{}", -1.0));
        assertThrows(IllegalArgumentException.class, () ->
                new QuotaPolicy("p1", "test", "{}", 101.0));
    }
}
