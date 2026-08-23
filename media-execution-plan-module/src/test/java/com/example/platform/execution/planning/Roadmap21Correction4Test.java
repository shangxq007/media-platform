package com.example.platform.execution.planning;

import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.ExecutionPlanSchemaVersion;
import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap #21 Correction 4 — TIMED_TEXT temporal bridge (Phase A T2) +
 * canonical framing collision safety.
 */
class Roadmap21Correction4Test {

    // ---------- TT: bridge exactness ----------

    static RenderExecutionCoverage coverage(long startTicks, long endTicks, long scale) {
        return new RenderExecutionCoverage(
                MediaTime.ofTicks(startTicks, scale), MediaTime.ofTicks(endTicks, scale), FrameRate.of(25, 1));
    }

    static boolean disjoint(RenderExecutionCoverage c, RenderExtent e) {
        return LogicalExecutionGraphBuilder.coverageDisjointFromExtent(c, e);
    }

    static final RenderExtent EXTENT_0_10 = new RenderExtent(
            MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1));

    @Test
    void timedTextBeforeExtentPruned() { // TT01 → TT02 semantics
        assertTrue(disjoint(coverage(20000, 25000, 1000), EXTENT_0_10),
                "text [20,25) extent [0,10) -> pruned");
    }

    @Test
    void timedTextInsideExtentSurvives() { // TT01
        assertFalse(disjoint(coverage(0, 5000, 1000), EXTENT_0_10), "text [0,5) extent [0,10) -> survives");
    }

    @Test
    void timedTextPartialOverlapSurvives() { // TT03
        assertFalse(disjoint(coverage(5000, 15000, 1000), EXTENT_0_10), "text [5,15) extent [0,10) -> survives");
    }

    @Test
    void timedTextEndAtExtentStartPruned() { // TT04 half-open
        assertTrue(disjoint(coverage(0, 10000, 1000), new RenderExtent(
                        MediaTime.ofMillis(10000), MediaTime.ofMillis(20000), FrameRate.of(25, 1))),
                "text.end == extent.start -> pruned (half-open)");
    }

    @Test
    void timedTextStartAtExtentEndPruned() { // TT05 half-open
        assertTrue(disjoint(coverage(10000, 15000, 1000), EXTENT_0_10),
                "text.start == extent.end -> pruned (half-open)");
    }

    @Test
    void fractionalExactTimingNoRounding() { // TT06 — 1/3 second not integer-millis
        var c = coverage(1, 4, 3); // [1/3, 4/3) seconds — exact rational
        assertEquals(1, c.start().ticks());
        assertEquals(3, c.start().timeScale());
        assertFalse(disjoint(c, EXTENT_0_10), "fractional timing is exact, no rounding, no false prune");
    }

    @Test
    void bridgeProjectionExactRational() { // TT06 mechanical
        var projected = com.example.platform.render.domain.renderplan.ExactTextTimelineTimeProjection.project(
                new com.example.platform.fonttext.typography.FontRational(
                        java.math.BigInteger.valueOf(1), java.math.BigInteger.valueOf(3)));
        assertTrue(projected.exact());
        var mt = ((com.example.platform.render.domain.renderplan.ExactTextTimelineTimeProjection.Projected) projected).mediaTime();
        assertEquals(1, mt.ticks());
        assertEquals(3, mt.timeScale());
    }

    @Test
    void bridgeOverflowFailsClosed() { // TT14 — beyond long range
        var projected = com.example.platform.render.domain.renderplan.ExactTextTimelineTimeProjection.project(
                new com.example.platform.fonttext.typography.FontRational(
                        java.math.BigInteger.valueOf(Long.MAX_VALUE).add(java.math.BigInteger.ONE),
                        java.math.BigInteger.ONE));
        assertFalse(projected.exact(), "unrepresentable rational -> typed fail-closed (no approximation)");
    }

    @Test
    void bridgeEndIsStartPlusDuration() { // coverage.end = exact(start + duration)
        var end = com.example.platform.render.domain.renderplan.ExactTextTimelineTimeProjection.projectEnd(
                new com.example.platform.fonttext.typography.FontRational(
                        java.math.BigInteger.ONE, java.math.BigInteger.valueOf(2)),   // 1/2
                new com.example.platform.fonttext.typography.FontRational(
                        java.math.BigInteger.ONE, java.math.BigInteger.valueOf(3)));  // 1/3
        assertTrue(end.exact());
        var mt = ((com.example.platform.render.domain.renderplan.ExactTextTimelineTimeProjection.Projected) end).mediaTime();
        assertEquals(5, mt.ticks());   // 1/2 + 1/3 = 5/6
        assertEquals(6, mt.timeScale());
    }

    // ---------- canonical framing: delimiter collision ----------

    @Test
    void framedEncodingInjective() { // CANONICAL_DELIMITER_COLLISION_TEST
        // parameter value containing delimiters must not collide with framing
        String a = Canonical.framed("b,c=d");
        String b = Canonical.framed("b") + "," + Canonical.framed("c") + "=" + Canonical.framed("d");
        assertNotEquals(a, b, "length-prefixed framing is injective over delimiter-bearing values");
        // distinct values -> distinct frames
        assertNotEquals(Canonical.framed("x|y"), Canonical.framed("x") + "|" + Canonical.framed("y"));
        assertNotEquals(Canonical.framed("a\nb"), Canonical.framed("a") + "\n" + Canonical.framed("b"));
        // same value -> same frame
        assertEquals(Canonical.framed("a|b"), Canonical.framed("a|b"));
    }

    @Test
    void materializationCanonicalUsesFrozenCodec() {
        // materialization encoding delegates to the #20 authoritative codec —
        // no local reimplementation, no record toString semantics
        String src = "";
        try {
            src = java.nio.file.Files.readString(java.nio.file.Paths.get(
                    "src/main/java/com/example/platform/execution/planning/Canonical.java"));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        assertTrue(src.contains("materializationRequirementCanonicalPublic"),
                "CANONICAL_ENCODER delegates materialization to the #20 frozen codec");
        String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
        assertFalse(code.contains("t.toString()") || code.contains("a.toString()"),
                "no aggregate toString semantic fallback in Canonical code");
    }

    // ---------- REUSE exactness ----------

    @Test
    void schemaVersionFrozenSurface() {
        assertEquals(ExecutionPlanSchemaVersion.V1, new ExecutionPlanSchemaVersion(1));
        assertEquals(ExecutionPlanSchemaVersion.of(2), new ExecutionPlanSchemaVersion(2));
        assertEquals("1", ExecutionPlanSchemaVersion.V1.toString());
        assertThrows(IllegalArgumentException.class, () -> new ExecutionPlanSchemaVersion(0));
    }

    @Test
    void creationContextFrozenInvariants() {
        // createdAt REQUIRED
        assertThrows(NullPointerException.class, () -> new com.example.platform.execution.domain.ExecutionCreationContext(
                "u", "t", "p", null, "trace", "parent", "c"));
        var ctx = com.example.platform.execution.domain.ExecutionCreationContext.forUser("u1", "t1",
                java.time.Instant.now());
        assertTrue(ctx.getRequestedByUserId().isPresent());
        assertEquals("u1", ctx.getRequestedByUserId().get());
        assertTrue(ctx.getParentPlanId().isEmpty());
    }

    // ---------- unit propagated extent participates in physical digest ----------

    @Test
    void unitPropagatedExtentMutationChangesPhysicalDigest() {
        var n = nodeWithArtifacts("n1");
        var logical = LogicalExecutionGraphBuilder.build(
                new com.example.platform.render.domain.renderplan.RenderGraph("render-graph-v1",
                        new com.example.platform.render.domain.renderplan.RenderPlanFingerprint("fp-1"),
                        java.util.List.of(n), java.util.List.of(),
                        new com.example.platform.render.domain.renderplan.RenderGraphFingerprint("gf-1")),
                new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1)));
        var extentA = new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1));
        var extentB = new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(5000), FrameRate.of(25, 1));
        var planLevel = new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1));
        var unit = PhysicalPlannerV1.plan(logical, planLevel, new ExecutionPlanId("pep-1")).units().get(0);
        var dA = PhysicalExecutionPlanDigest.compute("physical-execution-plan-v1",
                ExecutionPlanSchemaVersion.V1, java.util.List.of(unitWithExtent(unit, extentA)),
                logical.planFingerprint(), planLevel);
        var dB = PhysicalExecutionPlanDigest.compute("physical-execution-plan-v1",
                ExecutionPlanSchemaVersion.V1, java.util.List.of(unitWithExtent(unit, extentB)),
                logical.planFingerprint(), planLevel);
        assertNotEquals(dA, dB,
                "UNIT_PROPAGATED_EXTENT_MUTATION_TEST — unit extent [0,10] vs [0,5] must differ "
                        + "(plan-level extent unchanged)");
    }

    private static com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit unitWithExtent(
            com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit unit, RenderExtent extent) {
        return new com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit(
                unit.stepId(), unit.logicalNodeId(), unit.sourceRenderNodeId(),
                unit.sourceRenderNodeKind(), unit.operationKey(), unit.typedInputs(), unit.typedOutputs(),
                unit.typedDependencies(), unit.temporalWindow(), unit.executionCoverage(),
                unit.capabilityRequirementRefs(), unit.executionIntentRefs(), extent,
                unit.deterministicallyCacheable());
    }

    static com.example.platform.render.domain.renderplan.RenderNode nodeWithArtifacts(String id) {
        return new com.example.platform.render.domain.renderplan.RenderNode(
                new com.example.platform.render.domain.renderplan.RenderNodeId(id),
                new com.example.platform.render.domain.renderplan.RenderNodeKind.Source(),
                com.example.platform.render.domain.renderplan.RenderComponentPath.of(
                        com.example.platform.render.domain.renderplan.RenderComponentKind.CLIP, "clip-" + id),
                "transcode",
                java.util.List.of(), java.util.List.of(
                        new com.example.platform.extension.domain.CapabilityRequirement(
                                com.example.platform.extension.domain.CapabilityId.of("media.transcode"),
                                com.example.platform.extension.domain.ContractVersionRange.atLeast(
                                        com.example.platform.extension.domain.ContractVersion.of(1, 0)),
                                true, java.util.List.of())),
                java.util.List.of(com.example.platform.render.domain.renderplan.RenderOutputRequirement.of(
                        com.example.platform.render.domain.renderplan.RenderOutputRole.RENDER_MASTER)),
                java.util.List.of(new com.example.platform.render.domain.renderplan.RenderExecutionRequirement(
                        com.example.platform.render.domain.renderplan.RenderExecutionRequirement.GpuRequirement.NONE,
                        com.example.platform.render.domain.renderplan.RenderExecutionRequirement.RenderDeterminismClass.DETERMINISTIC,
                        false)),
                java.util.List.of(), java.util.Optional.empty(),
                coverage(0, 10000, 1000));
    }
}
