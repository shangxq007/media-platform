package com.example.platform.render.domain.renderplan;

import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.planning.ExecutionPlanningEntry;
import com.example.platform.execution.planning.ExecutionPlanningException;
import com.example.platform.execution.planning.ExecutionPlanningFailureReason;
import com.example.platform.execution.planning.LogicalExecutionGraph;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalPlannerV1;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap #21 Correction 6 (C6-C) — end-to-end fail-closed execution entry:
 *
 * <pre>
 *   DefaultRenderPlanner.plan(input)
 *     → RenderPlanningResult
 *     → guarded ExecutionPlanningEntry (only PLANNABLE accepted)
 *     → LogicalPhysicalPlanner.plan(...)
 * </pre>
 *
 * Overflow TIMED_TEXT → PLANNING_UNSUPPORTED ERROR → UNRENDERABLE → entry
 * rejects → NO LogicalExecutionGraph / NO PhysicalExecutionPlan.
 * Valid TIMED_TEXT → PLANNABLE → entry accepts → #21 results produced.
 */
class Roadmap21ExecutionEntryBoundaryTest {

    static com.example.platform.timeline.canonical.TextElement unrepresentableStart() {
        var base = TestPlans.textElement();
        return new com.example.platform.timeline.canonical.TextElement(
                base.id(),
                new com.example.platform.fonttext.typography.FontRational(
                        BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), BigInteger.ONE),
                base.duration(), base.styledText(), base.frame(),
                base.fallbackPolicy(), base.resolvedFontRuns());
    }

    static com.example.platform.timeline.canonical.TextElement unrepresentableEnd() {
        var base = TestPlans.textElement();
        return new com.example.platform.timeline.canonical.TextElement(
                base.id(), base.start(),
                new com.example.platform.fonttext.typography.FontRational(
                        BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), BigInteger.ONE),
                base.styledText(), base.frame(),
                base.fallbackPolicy(), base.resolvedFontRuns());
    }

    static RenderPlanningResult planWithText(com.example.platform.timeline.canonical.TextElement text) {
        var input = TestPlans.inputWithTimeline(TestPlans.verifiedRevisionWithText(text));
        return new DefaultRenderPlanner().plan(input);
    }

    @Test
    void startOverflowUnrenderableAndRejected() { // C6-T09 + T10
        var result = planWithText(unrepresentableStart());
        assertTrue(result.diagnostics().stream()
                        .anyMatch(d -> d.code() == RenderPlanningDiagnosticCode.PLANNING_UNSUPPORTED),
                "C6-T09 overflow diagnostic PLANNING_UNSUPPORTED");
        assertEquals(RenderPlanStatus.UNRENDERABLE, result.status(),
                "C6-T09 overflow -> UNRENDERABLE");
        var ex = assertThrows(ExecutionPlanningException.class,
                () -> ExecutionPlanningEntry.plan(result, new ExecutionPlanId("pep-x")),
                "C6-T10 guarded entry rejects UNRENDERABLE");
        assertEquals(ExecutionPlanningFailureReason.RENDER_PLANNING_RESULT_NOT_PLANNABLE, ex.reason());
        assertNotNull(ex.context());
        assertEquals(ExecutionPlanningFailureReason.RENDER_PLANNING_RESULT_NOT_PLANNABLE, ex.reason());
    }

    @Test
    void endOverflowUnrenderableAndRejected() { // C6-T11 + T12
        var result = planWithText(unrepresentableEnd());
        assertEquals(RenderPlanStatus.UNRENDERABLE, result.status(),
                "C6-T11 duration/end overflow -> UNRENDERABLE");
        assertThrows(ExecutionPlanningException.class,
                () -> ExecutionPlanningEntry.plan(result, new ExecutionPlanId("pep-x")),
                "C6-T12 guarded entry rejects");
    }

    @Test
    void no21ResultAfterOverflow() { // logical/physical after overflow = NONE
        var result = planWithText(unrepresentableStart());
        assertThrows(ExecutionPlanningException.class,
                () -> ExecutionPlanningEntry.plan(result, new ExecutionPlanId("pep-x")));
        // no planning result was produced — entry never reached the planner
        assertTrue(result.graph() == null || result.status() != RenderPlanStatus.PLANNABLE);
    }

    @Test
    void validTimedTextReaches21() { // C6-T13 positive control
        var result = planWithText(TestPlans.textElement());
        assertEquals(RenderPlanStatus.PLANNABLE, result.status(),
                "C6-T13 valid TIMED_TEXT -> PLANNABLE");
        var planned = ExecutionPlanningEntry.plan(result, new ExecutionPlanId("pep-ok"));
        assertNotNull(planned.logicalExecutionGraph(), "logical result produced");
        assertNotNull(planned.physicalExecutionPlan(), "physical result produced");
        assertTrue(planned.logicalExecutionGraph().nodes().size() > 0);
        assertTrue(planned.physicalExecutionPlan().units().size() > 0);
    }

    @Test
    void preparationRequiredRejected() { // general boundary: ONLY PLANNABLE enters
        var plannable = planWithText(TestPlans.textElement());
        var result = new RenderPlanningResult(
                plannable.plan(), plannable.graph(), RenderPlanStatus.PREPARATION_REQUIRED, List.of());
        assertThrows(ExecutionPlanningException.class,
                () -> ExecutionPlanningEntry.plan(result, new ExecutionPlanId("pep-x")),
                "PREPARATION_REQUIRED must not enter #21");
    }
}
