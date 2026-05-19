package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.entitlement.domain.QuotaPolicy;
import com.example.platform.entitlement.domain.QuotaProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuotaPolicyServiceTest {

    private QuotaPolicyService service;

    @BeforeEach
    void setUp() {
        service = new QuotaPolicyService();
    }

    @Test
    void getQuotaPolicyReturnsDefaultForUnknownFeature() {
        QuotaPolicy policy = service.getQuotaPolicy("unknown.feature");
        assertNotNull(policy);
        assertEquals("unknown.feature", policy.featureCode());
    }

    @Test
    void isExceededReturnsFalseForLowUsage() {
        assertFalse(service.isExceeded("render.job.create", 5));
    }

    @Test
    void isExceededReturnsTrueForHighUsage() {
        assertTrue(service.isExceeded("ai.model.premium", 200));
    }

    @Test
    void isWarningReturnsTrueAboveThreshold() {
        assertTrue(service.isWarning("render.job.create", 9000));
    }

    @Test
    void isWarningReturnsFalseBelowThreshold() {
        assertFalse(service.isWarning("render.job.create", 100));
    }

    @Test
    void remainingReturnsCorrectValue() {
        long remaining = service.remaining("render.job.create", 200);
        assertEquals(9800, remaining);
    }

    @Test
    void remainingReturnsZeroWhenExceeded() {
        long remaining = service.remaining("render.job.create", 20000);
        assertEquals(0, remaining);
    }

    @Test
    void resolveLimitFromProfileMapsRenderFeature() {
        QuotaProfile profile = new QuotaProfile(
                "id", "test", "Test", "desc",
                500, 50, 5, 10737418240L, 200, 100,
                2000, 100, 120, 60, null, null);
        assertEquals(500, service.resolveLimitFromProfile(profile, "render.job.create"));
    }

    @Test
    void resolveLimitFromProfileMapsGpuFeature() {
        QuotaProfile profile = new QuotaProfile(
                "id", "test", "Test", "desc",
                500, 50, 5, 10737418240L, 200, 100,
                2000, 100, 120, 60, null, null);
        assertEquals(200, service.resolveLimitFromProfile(profile, "gpu.render"));
    }

    @Test
    void resolveLimitFromProfileMapsPromptFeature() {
        QuotaProfile profile = new QuotaProfile(
                "id", "test", "Test", "desc",
                500, 50, 5, 10737418240L, 200, 100,
                2000, 100, 120, 60, null, null);
        assertEquals(2000, service.resolveLimitFromProfile(profile, "prompt.execute"));
    }

    @Test
    void registerPolicyOverridesExisting() {
        service.registerPolicy(new QuotaPolicy("custom", "custom", "test.feature", 50, "DAILY", 90));
        assertTrue(service.isExceeded("test.feature", 60));
    }
}
