package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UsageMeteringServiceTest {

    private UsageMeteringService service;

    @BeforeEach
    void setUp() {
        service = new UsageMeteringService();
    }

    @Test
    void shouldRecordUsage() {
        UsageRecord record = service.recordUsage(
                "tenant-1", "ws-1", "user-1",
                "api_calls", 100.0, "calls",
                Instant.now(), null);
        assertNotNull(record);
        assertNotNull(record.recordId());
        assertEquals("tenant-1", record.tenantId());
        assertEquals("api_calls", record.meterKey());
        assertEquals(100.0, record.quantity());
    }

    @Test
    void shouldReturnDuplicateOnIdempotencyKey() {
        Instant now = Instant.now();
        UsageRecord first = service.recordUsage(
                "tenant-1", "ws-1", "user-1",
                "api_calls", 100.0, "calls", now, "idem-001");
        UsageRecord second = service.recordUsage(
                "tenant-1", "ws-1", "user-1",
                "api_calls", 200.0, "calls", now, "idem-001");
        assertEquals(first.recordId(), second.recordId());
        assertEquals(100.0, second.quantity());
    }

    @Test
    void shouldRegisterMeter() {
        UsageMeter meter = service.registerMeter(
                "render_seconds", "Render Seconds", "GPU render time",
                "seconds", "SUM");
        assertNotNull(meter);
        assertEquals("render_seconds", meter.meterKey());
        assertEquals("SUM", meter.aggregationType());
        assertEquals("ACTIVE", meter.status());
    }

    @Test
    void shouldGetMeters() {
        service.registerMeter("m1", "Meter 1", "", "unit", "SUM");
        service.registerMeter("m2", "Meter 2", "", "unit", "MAX");
        List<UsageMeter> meters = service.getMeters();
        assertEquals(2, meters.size());
    }

    @Test
    void shouldGetUsageByTenant() {
        service.recordUsage("t1", "ws-1", "u1", "api_calls", 10, "calls", Instant.now(), null);
        service.recordUsage("t1", "ws-1", "u1", "api_calls", 20, "calls", Instant.now(), null);
        service.recordUsage("t2", "ws-1", "u1", "api_calls", 30, "calls", Instant.now(), null);
        List<UsageRecord> t1Usage = service.getUsageByTenant("t1");
        assertEquals(2, t1Usage.size());
    }

    @Test
    void shouldGetUsageByMeterKey() {
        service.recordUsage("t1", "ws-1", "u1", "api_calls", 10, "calls", Instant.now(), null);
        service.recordUsage("t2", "ws-1", "u1", "storage_gb", 5, "gb", Instant.now(), null);
        List<UsageRecord> apiUsage = service.getUsage(null, "api_calls");
        assertEquals(1, apiUsage.size());
        assertEquals("api_calls", apiUsage.get(0).meterKey());
    }

    @Test
    void shouldGetUsageRecordById() {
        UsageRecord record = service.recordUsage(
                "t1", "ws-1", "u1", "api_calls", 10, "calls", Instant.now(), null);
        UsageRecord found = service.getUsageRecord(record.recordId());
        assertNotNull(found);
        assertEquals(record.recordId(), found.recordId());
    }
}
