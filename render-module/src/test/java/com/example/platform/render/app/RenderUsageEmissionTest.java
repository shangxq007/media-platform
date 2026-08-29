package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.billing.usage.UsageDimension;
import com.example.platform.billing.usage.UsageQuantity;
import com.example.platform.billing.usage.UsageRecord;
import com.example.platform.billing.usage.UsageRecordEmissionPort;
import com.example.platform.billing.usage.UsageUnit;
import com.example.platform.render.domain.RenderJobPlan;
import com.example.platform.render.domain.RenderProfile;
import com.example.platform.render.domain.RenderStep;
import com.example.platform.render.domain.RenderStepStatus;
import com.example.platform.render.domain.RenderStepType;
import com.example.platform.shared.web.TenantContext;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the additive DURATION usage-emission side effect at the
 * {@link RenderStepExecutionService} step-completion boundary.
 *
 * <p>Pure unit tests (no Spring context, no DB): the {@link UsageRecordEmissionPort} is a
 * capturing lambda double, and {@link TenantContext} is set directly. These prove:</p>
 * <ul>
 *   <li>step completion emits exactly one DURATION record with a correct idempotency key;</li>
 *   <li>retry of the same attempt reuses the key (no double counting); a new attempt yields a new key;</li>
 *   <li>emission is independent of {@code billing.enforcement.enabled} (RED-004).</li>
 * </ul>
 */
class RenderUsageEmissionTest {

    private static final String TENANT = "tenant-render";

    private RenderPlanService planService;
    private RenderStepExecutionService service;
    private final AtomicReference<UsageRecord> captured = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        planService = new RenderPlanService();
        UsageRecordEmissionPort port = record -> { captured.set(record); return record; };
        service = new RenderStepExecutionService(planService, port);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void stepCompletion_emitsOneDurationRecord() {
        RenderJobPlan plan = planWithOneStep();
        String stepId = plan.steps().get(0).id();

        service.executeNextStep(plan.id());

        UsageRecord record = captured.get();
        assertNotNull(record, "expected a usage record to be emitted on step completion");
        assertEquals(TENANT, record.tenantId());
        assertEquals(UsageDimension.DURATION, record.dimension());
        assertEquals("REPORTED", record.provenance());
        assertEquals("render-step", record.source());
        assertEquals("render-" + stepId + "-1", record.idempotencyKey());
        assertEquals(UsageUnit.MILLISECONDS, record.quantity().unit());
        assertTrue(record.quantity().baseUnits() >= 0);
        assertNotNull(record.recordedAt());
        // operationRef carries the plan (operation) and step (attempt) identity.
        assertEquals(plan.id(), record.operationRef().operationId());
        assertEquals(stepId, record.operationRef().attemptId());
    }

    @Test
    void retryOfSameAttempt_doesNotDoubleCount() {
        RenderJobPlan plan = planWithOneStep();
        String stepId = plan.steps().get(0).id();

        // Same step identity + same attempt -> same idempotency key.
        RenderStep completed = plan.steps().get(0).markRunning().markCompleted(List.of("art-1"));
        service.emitStepUsage(plan, completed, 1);
        UsageRecord first = captured.get();
        assertNotNull(first);

        service.emitStepUsage(plan, completed, 1);
        UsageRecord second = captured.get();

        assertEquals(first.idempotencyKey(), second.idempotencyKey(),
                "retry of the same attempt must reuse the idempotency key (no double counting)");
        assertEquals("render-" + stepId + "-1", first.idempotencyKey());
    }

    @Test
    void newAttempt_getsNewIdempotencyKey() {
        RenderJobPlan plan = planWithOneStep();
        String stepId = plan.steps().get(0).id();
        RenderStep completed = plan.steps().get(0).markRunning().markCompleted(List.of("art-1"));

        service.emitStepUsage(plan, completed, 1);
        UsageRecord first = captured.get();

        service.emitStepUsage(plan, completed, 2);
        UsageRecord second = captured.get();

        assertNotNull(first);
        assertNotNull(second);
        assertTrue(!first.idempotencyKey().equals(second.idempotencyKey()),
                "a new attempt must yield a distinct idempotency key");
        assertEquals("render-" + stepId + "-2", second.idempotencyKey());
    }

    @Test
    void emission_isIndependentOfEnforcementFlag() {
        // RED-004: disabling billing enforcement must NOT suppress canonical usage emission.
        // The emission path never consults the flag, so emission happens regardless.
        assertNull(System.getProperty("billing.enforcement.enabled"),
                "sanity: this unit test does not set the enforcement flag");

        RenderJobPlan plan = planWithOneStep();
        service.executeNextStep(plan.id());

        assertNotNull(captured.get(),
                "usage emission must occur even though billing.enforcement.enabled is unset (defaults false)");
    }

    @Test
    void emission_skipsWhenNoTenant() {
        TenantContext.clear();
        RenderJobPlan plan = planWithOneStep();
        service.executeNextStep(plan.id());
        assertNull(captured.get(), "no usage record should be emitted without a tenant");
    }

    @Test
    void failedStepWithoutDuration_emitsNothing() {
        // RED-003 (no fabricated usage): emission is gated on the measured duration fact, not on
        // business success. A FAILED step that has no measurable duration (never ran) emits nothing.
        RenderJobPlan plan = planWithOneStep();
        RenderStep failedNoDuration = plan.steps().get(0).markFailed("ERR", "boom");
        service.emitStepUsage(plan, failedNoDuration, 1);
        assertNull(captured.get(), "a step with no measurable duration fact must not emit fabricated usage");
    }

    @Test
    void failedStepWithDuration_emitsOneDurationRecord() {
        // RED-003 (fact-driven emission, AR-OBS-03 repair): a FAILED step that actually ran and has
        // a real measured duration emits exactly one canonical DURATION record — success status must
        // not suppress a genuine consumption fact.
        RenderJobPlan plan = planWithOneStep();
        String stepId = plan.steps().get(0).id();
        RenderStep failed = plan.steps().get(0).markRunning().markFailed("ERR", "boom");

        service.emitStepUsage(plan, failed, 1);

        UsageRecord record = captured.get();
        assertNotNull(record, "expected a usage record to be emitted for a FAILED step with a real duration fact");
        assertEquals(TENANT, record.tenantId());
        assertEquals(UsageDimension.DURATION, record.dimension());
        assertEquals("REPORTED", record.provenance());
        assertEquals("render-step", record.source());
        assertEquals("render-" + stepId + "-1", record.idempotencyKey());
        assertEquals(UsageUnit.MILLISECONDS, record.quantity().unit());
        assertTrue(record.quantity().baseUnits() >= 0);
        assertNotNull(record.recordedAt());
        assertEquals(plan.id(), record.operationRef().operationId());
        assertEquals(stepId, record.operationRef().attemptId());
    }

    @Test
    void failedStepRetryOfSameAttempt_doesNotDoubleCount() {
        // RED-003 (idempotency on the failed path): replaying the SAME failed attempt reuses the
        // idempotency key, so it does not double count.
        RenderJobPlan plan = planWithOneStep();
        String stepId = plan.steps().get(0).id();
        RenderStep failed = plan.steps().get(0).markRunning().markFailed("ERR", "boom");

        service.emitStepUsage(plan, failed, 1);
        UsageRecord first = captured.get();
        assertNotNull(first);

        service.emitStepUsage(plan, failed, 1);
        UsageRecord second = captured.get();

        assertEquals(first.idempotencyKey(), second.idempotencyKey(),
                "retry of the same failed attempt must reuse the idempotency key (no double counting)");
        assertEquals("render-" + stepId + "-1", first.idempotencyKey());
    }

    @Test
    void failedStepEmission_isIndependentOfEnforcementFlag() {
        // RED-003 / RED-004: fact-driven emission for a FAILED step never consults
        // billing.enforcement.enabled, so emission happens regardless of the flag.
        assertNull(System.getProperty("billing.enforcement.enabled"),
                "sanity: this unit test does not set the enforcement flag");

        RenderJobPlan plan = planWithOneStep();
        RenderStep failed = plan.steps().get(0).markRunning().markFailed("ERR", "boom");

        service.emitStepUsage(plan, failed, 1);

        assertNotNull(captured.get(),
                "FAILED-step usage emission must occur even though billing.enforcement.enabled is unset (defaults false)");
    }

    private RenderJobPlan planWithOneStep() {
        String planId = "plan-" + System.nanoTime();
        // Leave the step PENDING: executeNextStep selects the next pending step, marks it
        // RUNNING (recording startedAt), then completes it (computing the duration fact).
        RenderStep step = RenderStep.pending("step-" + System.nanoTime(), planId, RenderStepType.PROVIDER_TRANSCODE);
        RenderJobPlan plan = RenderJobPlan.create(planId, "job-1", RenderProfile.social1080p(), List.of(step));
        return planService.save(plan);
    }
}
