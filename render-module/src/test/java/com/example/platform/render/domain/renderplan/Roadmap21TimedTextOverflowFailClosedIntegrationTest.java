package com.example.platform.render.domain.renderplan;

import com.example.platform.fonttext.typography.FontRational;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap #21 Correction 5 — B4-EVIDENCE: unrepresentable TIMED_TEXT exact
 * rational timing must FAIL CLOSED through the real render-planning pipeline.
 *
 * <p>T2 frozen policy: FontRational → checked exact #20 projection → MediaTime,
 * or PLANNING_UNSUPPORTED (no rounding, no clamp). This test proves that an
 * authored TIMED_TEXT whose exact rational exceeds the bounded MediaTime
 * long-backed representation causes the materializer to emit a planning error
 * and produces NO valid RenderGraph suitable for #21 planning.
 */
class Roadmap21TimedTextOverflowFailClosedIntegrationTest {

    /** FontRational beyond long range: Long.MAX_VALUE + 1 / 1. */
    static FontRational unrepresentable() {
        return new FontRational(
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), BigInteger.ONE);
    }

    @Test
    void unrepresentableTextTimeFailsClosedInMaterializer() {
        // authored TextElement with out-of-range start
        com.example.platform.timeline.canonical.TextElement badText = TestPlans.textElement();
        // rebuild with unrepresentable start via the same shape as TestPlans
        com.example.platform.timeline.canonical.TextElement overflowText =
                new com.example.platform.timeline.canonical.TextElement(
                        badText.id(), unrepresentable(), badText.duration(),
                        badText.styledText(), badText.frame(),
                        badText.fallbackPolicy(), badText.resolvedFontRuns());

        var input = TestPlans.inputWithTimeline(TestPlans.verifiedRevisionWithText(overflowText));
        RenderMaterializationResult result = new DefaultRenderMaterializer().materialize(input);

        // fail closed: planning error emitted (PLANNING_UNSUPPORTED)
        boolean planningError = result.diagnostics().stream()
                .anyMatch(d -> d.code() == RenderPlanningDiagnosticCode.PLANNING_UNSUPPORTED
                        || d.message().contains("not exactly representable"));
        assertTrue(planningError,
                "TIMED_TEXT_UNREPRESENTABLE_EXACT_TIME_FAILS_CLOSED=PASS — materializer emits typed planning error");

        // NO valid TIMED_TEXT node with invented/approximate coverage reaches
        // the graph: either the node is absent or the graph is invalid
        boolean textNodeWithCoverage = result.nodes().stream()
                .anyMatch(n -> n.kind() instanceof RenderNodeKind.TimedText
                        && n.executionCoverage() != null);
        assertFalse(textNodeWithCoverage,
                "no TIMED_TEXT node with invented/approximate coverage reaches the graph");
    }

    @Test
    void unrepresentableEndAlsoFailsClosed() {
        com.example.platform.timeline.canonical.TextElement badText = TestPlans.textElement();
        // duration unrepresentable (start + duration overflows)
        com.example.platform.timeline.canonical.TextElement overflowEnd =
                new com.example.platform.timeline.canonical.TextElement(
                        badText.id(), badText.start(), unrepresentable(),
                        badText.styledText(), badText.frame(),
                        badText.fallbackPolicy(), badText.resolvedFontRuns());
        var input = TestPlans.inputWithTimeline(TestPlans.verifiedRevisionWithText(overflowEnd));
        RenderMaterializationResult result = new DefaultRenderMaterializer().materialize(input);
        boolean planningError = result.diagnostics().stream()
                .anyMatch(d -> d.code() == RenderPlanningDiagnosticCode.PLANNING_UNSUPPORTED
                        || d.message().contains("not exactly representable"));
        assertTrue(planningError, "overflow of start+duration also fails closed");
        boolean textNodeWithCoverage = result.nodes().stream()
                .anyMatch(n -> n.kind() instanceof RenderNodeKind.TimedText
                        && n.executionCoverage() != null);
        assertFalse(textNodeWithCoverage, "no approximate coverage node");
    }
}
