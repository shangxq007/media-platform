package com.example.platform.billing.app;

import com.example.platform.billing.domain.UsageMeter;
import com.example.platform.billing.usage.UsageRecord;
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
                "tenant-1",
                "api_calls", 100.0, "calls",
                Instant.now(), null);
        assertNotNull(record);
        assertNotNull(record.recordId());
        assertEquals("tenant-1", record.tenantId());
        assertEquals("REQUEST", record.dimension().name());
        assertEquals(100L, record.quantity().baseUnits());
    }

    @Test
    void shouldReturnDuplicateOnIdempotencyKey() {
        Instant now = Instant.now();
        UsageRecord first = service.recordUsage(
                "tenant-1",
                "api_calls", 100.0, "calls", now, "idem-001");
        UsageRecord second = service.recordUsage(
                "tenant-1",
                "api_calls", 200.0, "calls", now, "idem-001");
        assertEquals(first.recordId(), second.recordId());
        assertEquals(100L, second.quantity().baseUnits());
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
        List<UsageMeter> meters = service.getMeters();
        assertEquals(1, meters.size());
        assertEquals("m1", meters.get(0).meterKey());
    }

    @Test
    void shouldGetUsageByTenant() {
        service.recordUsage("tenant-1", "render_seconds", 12.5, "seconds", Instant.now(), null);
        service.recordUsage("tenant-2", "render_seconds", 99.0, "seconds", Instant.now(), null);
        List<UsageRecord> tenant1 = service.getUsageByTenant("tenant-1");
        assertEquals(1, tenant1.size());
        assertEquals("DURATION", tenant1.get(0).dimension().name());
        assertEquals(13L, tenant1.get(0).quantity().baseUnits());
    }
}
