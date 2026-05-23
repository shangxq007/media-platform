package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

class QuotaUsageServiceTest {

    private QuotaUsageService service;

    @BeforeEach
    void setUp() {
        service = new QuotaUsageService(java.util.Optional.empty());
    }

    @Test
    void getUsageReturnsZeroForUnknownSubject() {
        assertEquals(0, service.getUsage("unknown", "feature"));
    }

    @Test
    void incrementUsageAccumulates() {
        service.incrementUsage("sub-1", "render", 5);
        assertEquals(5, service.getUsage("sub-1", "render"));
        service.incrementUsage("sub-1", "render", 3);
        assertEquals(8, service.getUsage("sub-1", "render"));
    }

    @Test
    void setUsageOverridesValue() {
        service.setUsage("sub-1", "render", 100);
        assertEquals(100, service.getUsage("sub-1", "render"));
    }

    @Test
    void resetUsageSetsToZero() {
        service.setUsage("sub-1", "render", 100);
        service.resetUsage("sub-1", "render");
        assertEquals(0, service.getUsage("sub-1", "render"));
    }

    @Test
    void resetAllClearsAllFeatures() {
        service.incrementUsage("sub-1", "render", 10);
        service.incrementUsage("sub-1", "ai", 5);
        service.resetAll("sub-1");
        assertEquals(0, service.getUsage("sub-1", "render"));
        assertEquals(0, service.getUsage("sub-1", "ai"));
    }

    @Test
    void getAllUsageReturnsCopy() {
        service.incrementUsage("sub-1", "render", 10);
        service.incrementUsage("sub-1", "ai", 5);
        Map<String, Long> all = service.getAllUsage("sub-1");
        assertEquals(2, all.size());
        assertEquals(10L, all.get("render"));
        assertEquals(5L, all.get("ai"));
    }

    @Test
    void multipleSubjectsTrackedIndependently() {
        service.incrementUsage("sub-1", "render", 10);
        service.incrementUsage("sub-2", "render", 20);
        assertEquals(10, service.getUsage("sub-1", "render"));
        assertEquals(20, service.getUsage("sub-2", "render"));
    }
}
