package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.entitlement.domain.QuotaDecision;
import com.example.platform.entitlement.domain.QuotaProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuotaDecisionServiceTest {

    private QuotaDecisionService service;
    private QuotaPolicyService policyService;
    private QuotaUsageService usageService;

    @BeforeEach
    void setUp() {
        policyService = new QuotaPolicyService();
        usageService = new QuotaUsageService(java.util.Optional.empty());
        service = new QuotaDecisionService(policyService, usageService);
    }

    @Test
    void evaluateAllowsWhenUnderLimit() {
        QuotaDecision decision = service.evaluate("subject-1", "render.job.create", 5);
        assertTrue(decision.allowed());
        assertEquals("subject-1", decision.subjectId());
        assertEquals("render.job.create", decision.quotaCode());
    }

    @Test
    void evaluateDeniesWhenOverLimit() {
        usageService.setUsage("subject-1", "render.job.create", 9999);
        QuotaDecision decision = service.evaluate("subject-1", "render.job.create", 100);
        assertFalse(decision.allowed());
    }

    @Test
    void evaluateWithProfileUsesProfileLimit() {
        QuotaProfile profile = new QuotaProfile(
                "id", "pro", "Pro", "desc",
                500, 50, 5, 10737418240L, 200, 100,
                2000, 100, 120, 60, null, null);
        QuotaDecision decision = service.evaluateWithProfile("subject-1", "render", profile, 100);
        assertTrue(decision.allowed());
    }

    @Test
    void evaluateWithProfileDeniesWhenOverLimit() {
        QuotaProfile profile = new QuotaProfile(
                "id", "basic", "Basic", "desc",
                10, 5, 1, 10737418240L, 0, 0,
                50, 0, 60, 30, null, null);
        QuotaDecision decision = service.evaluateWithProfile("subject-1", "render", profile, 100);
        assertFalse(decision.allowed());
    }

    @Test
    void recordUsageIncrementsUsage() {
        service.recordUsage("subject-1", "render.job.create", 5);
        assertEquals(5, usageService.getUsage("subject-1", "render.job.create"));
        service.recordUsage("subject-1", "render.job.create", 3);
        assertEquals(8, usageService.getUsage("subject-1", "render.job.create"));
    }

    @Test
    void getRemainingReturnsCorrectValue() {
        QuotaDecision decision = service.evaluate("subject-1", "render.job.create", 0);
        assertTrue(decision.limitValue() > 0);
    }

    @Test
    void getRemainingWithProfileReturnsProfileLimit() {
        QuotaProfile profile = new QuotaProfile(
                "id", "test", "Test", "desc",
                1000, 50, 5, 10737418240L, 100, 50,
                500, 20, 120, 60, null, null);
        long remaining = service.getRemainingWithProfile("subject-1", "render", profile);
        assertEquals(1000, remaining);
    }
}
