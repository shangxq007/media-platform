package com.example.platform.render.app.planner;

import com.example.platform.render.domain.planner.ExecutionPlan;
import com.example.platform.render.domain.planner.ExecutionStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PRE-#21 W1 — planner purity tests.
 *
 * The logical planner is pure computation over FrozenPlanningContext:
 * - no mutable runtime reads
 * - frozen facts only
 * - READY target → empty plan (no re-render)
 * - non-ready target → planned step with frozen producer/backend facts
 * - non-ready dependencies → planned steps
 */
class ExecutionPlannerServiceTest {

    private final ExecutionPlannerService planner = new ExecutionPlannerService();

    private FrozenPlanningContext ctx(boolean targetReady, String type,
                                      Map<String, FrozenPlanningContext.CapabilityResolutionFact> caps,
                                      Map<String, FrozenPlanningContext.DependencyFact> deps) {
        return FrozenPlanningContext.of("prod-1", type, "ten-1", "proj-1", targetReady, caps, deps);
    }

    private FrozenPlanningContext.CapabilityResolutionFact resolved(String producer, String backend) {
        return new FrozenPlanningContext.CapabilityResolutionFact("TRANSCRIPT", "ASR",
                producer, backend, "ASR", "frozen fact", true);
    }

    @Test
    void readyTargetProducesEmptyPlan() {
        var plan = planner.plan(ctx(true, "TRANSCRIPT", Map.of(), Map.of()));
        assertEquals("CREATED", plan.planStatus());
        assertTrue(plan.stages().isEmpty(), "READY target must not be re-planned");
        assertEquals("prod-1", plan.targetProductId());
        assertEquals("ten-1", plan.tenantId());
        assertEquals("proj-1", plan.projectId());
    }

    @Test
    void nonReadyTargetProducesSingleStepWithFrozenFacts() {
        var cap = resolved("producer-a", "backend-asr");
        var plan = planner.plan(ctx(false, "TRANSCRIPT", Map.of("TRANSCRIPT", cap), Map.of()));
        assertEquals(1, plan.stages().size());
        List<ExecutionStep> steps = plan.stages().get(0).steps();
        assertEquals(1, steps.size());
        ExecutionStep step = steps.get(0);
        assertEquals("producer-a", step.producerId());
        assertEquals(List.of("prod-1"), step.inputProductIds());
        assertEquals("backend-asr", step.backendId());
        assertTrue(step.backendResolved());
    }

    @Test
    void nonReadyDependenciesProduceSteps() {
        var cap = resolved("producer-a", "backend-asr");
        var dep = new FrozenPlanningContext.DependencyFact("upstream-1", "THUMBNAIL", "PROCESSING");
        var plan = planner.plan(ctx(false, "TRANSCRIPT", Map.of("TRANSCRIPT", cap),
                Map.of("upstream-1", dep)));
        List<ExecutionStep> steps = plan.stages().get(0).steps();
        assertEquals(2, steps.size(), "target step + non-ready dependency step");
    }

    @Test
    void readyDependenciesAreNotPlanned() {
        var cap = resolved("producer-a", "backend-asr");
        var dep = new FrozenPlanningContext.DependencyFact("upstream-1", "THUMBNAIL", "READY");
        var plan = planner.plan(ctx(false, "TRANSCRIPT", Map.of("TRANSCRIPT", cap),
                Map.of("upstream-1", dep)));
        assertEquals(1, plan.stages().get(0).steps().size(),
                "READY dependencies must not be planned");
    }

    @Test
    void unresolvedCapabilityFactMarksStepUnresolved() {
        var cap = new FrozenPlanningContext.CapabilityResolutionFact("TRANSCRIPT", "ASR",
                null, null, null, "no producer", false);
        var plan = planner.plan(ctx(false, "TRANSCRIPT", Map.of("TRANSCRIPT", cap), Map.of()));
        ExecutionStep step = plan.stages().get(0).steps().get(0);
        assertEquals("unknown", step.producerId());
        assertFalse(step.backendResolved());
    }

    @Test
    void explainIsDeterministicOverPlan() {
        var cap = resolved("producer-a", "backend-asr");
        var plan = planner.plan(ctx(false, "TRANSCRIPT", Map.of("TRANSCRIPT", cap), Map.of()));
        String e1 = planner.explain(plan);
        String e2 = planner.explain(plan);
        assertEquals(e1, e2, "explain must be deterministic");
        assertTrue(e1.contains("producer-a"));
    }
}
