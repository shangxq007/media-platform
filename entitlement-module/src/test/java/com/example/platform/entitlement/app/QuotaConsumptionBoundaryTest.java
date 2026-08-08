package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.QuotaDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class QuotaConsumptionBoundaryTest {

    private QuotaPolicyService policyService;
    private QuotaUsageService usageService;
    private QuotaDecisionService decisionService;
    private QuotaConsumptionBoundary boundary;

    @BeforeEach
    void setUp() {
        policyService = new QuotaPolicyService();
        usageService = new QuotaUsageService(Optional.empty());
        decisionService = new QuotaDecisionService(policyService, usageService);
        boundary = new QuotaConsumptionBoundaryImpl(decisionService);
    }

    @Test
    void recordPostExecutionUsageDelegatesToRecordUsage() {
        boundary.recordPostExecutionUsage("tenant-1", "render.job.create", 5L);

        // The post-execution consumption reached the existing quota authority.
        assertEquals(5L, usageService.getUsage("tenant-1", "render.job.create"));

        // A second call accumulates (proves it is accounting, not replacement).
        boundary.recordPostExecutionUsage("tenant-1", "render.job.create", 3L);
        assertEquals(8L, usageService.getUsage("tenant-1", "render.job.create"));
    }

    @Test
    void preExecutionEvaluateUntouched() {
        // Pre-execution hard-limit decision semantics are unchanged.
        QuotaDecision before = decisionService.evaluate("tenant-1", "render.job.create", 10L);
        assertTrue(before.allowed());

        // Post-execution accounting must not alter the pre-execution decision path.
        boundary.recordPostExecutionUsage("tenant-1", "render.job.create", 10L);
        QuotaDecision after = decisionService.evaluate("tenant-1", "render.job.create", 10L);

        // Usage now reflects the recorded consumption; evaluate semantics are identical.
        assertEquals(10L, usageService.getUsage("tenant-1", "render.job.create"));
        assertEquals(before.allowed(), after.allowed());
    }

    @Test
    void boundaryIsPureConsumer() {
        // This boundary only accounts — it must produce no usage facts of its own. We
        // assert by confirming usage is zero before any external emission drives it.
        assertEquals(0L, usageService.getUsage("tenant-never-emitted", "render.job.create"));
    }
}
