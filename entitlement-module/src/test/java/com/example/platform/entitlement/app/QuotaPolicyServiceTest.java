package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.entitlement.domain.QuotaProfile;
import org.junit.jupiter.api.BeforeEach;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class QuotaPolicyServiceTest {

    private QuotaPolicyService service;

    @BeforeEach
    void setUp() {
        service = new QuotaPolicyService();
    }

    @Test
    void getQuotaPolicyFailsClosedForUnknownFeature() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getQuotaPolicy("unknown.feature"));
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
        BigDecimal remaining = service.remaining("render.job.create", BigDecimal.valueOf(200));
        assertEquals(BigDecimal.valueOf(9800), remaining);
    }

    @Test
    void remainingReturnsZeroWhenExceeded() {
        BigDecimal remaining = service.remaining("render.job.create", BigDecimal.valueOf(20000));
        assertEquals(BigDecimal.ZERO, remaining);
    }

    @Test
    void resolveLimitFromProfileMapsRenderFeature() {
        QuotaProfile profile = new QuotaProfile(
                "id", "test", "Test", "desc",
                500, 50, 5, 10737418240L, 200, 100,
                2000, 100, 120, 60, null, null);
        assertEquals(BigDecimal.valueOf(500), service.resolveLimitFromProfile(profile, "render.job.create"));
    }

    @Test
    void resolveLimitFromProfileMapsGpuFeature() {
        QuotaProfile profile = new QuotaProfile(
                "id", "test", "Test", "desc",
                500, 50, 5, 10737418240L, 200, 100,
                2000, 100, 120, 60, null, null);
        assertEquals(BigDecimal.valueOf(200), service.resolveLimitFromProfile(profile, "gpu.render"));
    }

    @Test
    void resolveLimitFromProfileMapsPromptFeature() {
        QuotaProfile profile = new QuotaProfile(
                "id", "test", "Test", "desc",
                500, 50, 5, 10737418240L, 200, 100,
                2000, 100, 120, 60, null, null);
        assertEquals(BigDecimal.valueOf(2000), service.resolveLimitFromProfile(profile, "prompt.execute"));
    }

}
