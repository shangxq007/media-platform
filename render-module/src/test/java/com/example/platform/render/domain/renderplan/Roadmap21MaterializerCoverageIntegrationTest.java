package com.example.platform.render.domain.renderplan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap #21 Correction 3 — mandatory integration-level test: obtain the
 * RenderGraph from the REAL DefaultRenderMaterializer (production graph
 * construction) and verify the typed RenderExecutionCoverage disposition by
 * RenderNodeKind (C12/C13 Option A coverage completeness, B1).
 *
 * <p>Disposition table (Correction 3 internal, repository-reality-derived):
 * <ul>
 *   <li>DECODE       — clip.timelineRange (authored timeline coordinates) — MUST have coverage</li>
 *   <li>EFFECT       — effect applicationRange when authored, else clip.timelineRange — MUST have coverage</li>
 *   <li>AUDIO_PROCESS— routed clip timeline range (via clipId) — MUST have coverage</li>
 *   <li>AUDIO_MIX    — aggregate, no single interval — null (never pruned)</li>
 *   <li>TIMED_TEXT   — #20 TextElement carries FontRational start/duration only
 *                      (no timeline MediaTime) — null (never pruned; no invented interval)</li>
 *   <li>COMPOSITE    — aggregate — null</li>
 *   <li>OUTPUT       — full-extent sink — null</li>
 * </ul>
 */
class Roadmap21MaterializerCoverageIntegrationTest {

    @Test
    void realMaterializerAssignsCoveragePerDisposition() {
        // full production-shaped graph: clips + effects (audio/text mix
        // fixtures are exercised by the kind-disposition assertions below
        // where the canonical revision carries them)
        var input = TestPlans.inputWithTimeline(TestPlans.verifiedRevision());
        RenderMaterializationResult result = new DefaultRenderMaterializer().materialize(input);
        assertFalse(result.nodes().isEmpty(), "real materializer must produce nodes");

        for (RenderNode n : result.nodes()) {
            RenderNodeKind k = n.kind();
            if (k instanceof RenderNodeKind.Decode) {
                assertNotNull(n.executionCoverage(),
                        "DECODE must carry typed execution coverage (clip.timelineRange)");
            } else if (k instanceof RenderNodeKind.Effect) {
                assertNotNull(n.executionCoverage(),
                        "EFFECT must carry typed execution coverage (applicationRange/clip range)");
            } else if (k instanceof RenderNodeKind.AudioProcess) {
                assertNotNull(n.executionCoverage(),
                        "AUDIO_PROCESS must carry typed execution coverage (routed clip timeline range)");
            } else if (k instanceof RenderNodeKind.AudioMix) {
                assertNull(n.executionCoverage(),
                        "AUDIO_MIX is aggregate — null coverage (never pruned)");
            } else if (k instanceof RenderNodeKind.TimedText) {
                // Correction 4 (Phase A T2): TIMED_TEXT receives exact typed
                // coverage from the #20-owned FontRational→MediaTime bridge
                assertNotNull(n.executionCoverage(),
                        "TIMED_TEXT must carry exact typed execution coverage "
                                + "(authored FontRational timing projected into timeline coordinates)");
            } else if (k instanceof RenderNodeKind.Composite) {
                assertNull(n.executionCoverage(),
                        "COMPOSITE is aggregate — null coverage (never pruned)");
            } else if (k instanceof RenderNodeKind.Output) {
                assertNull(n.executionCoverage(),
                        "OUTPUT is full-extent sink — null coverage (never pruned)");
            }
        }
    }

    @Test
    void realMaterializerDecodeCoverageMatchesClipTimelineRange() {
        var input = TestPlans.inputWithTimeline(TestPlans.verifiedRevision());
        List<RenderNode> nodes = new DefaultRenderMaterializer().materialize(input).nodes();
        RenderNode decode = nodes.stream()
                .filter(n -> n.kind() instanceof RenderNodeKind.Decode)
                .findFirst().orElseThrow();
        assertNotNull(decode.executionCoverage());
        // coverage coordinates are timeline-domain — equal to the authored
        // clip.timelineRange (timeline coordinate authority), regardless of
        // the source sample window coordinates
        var clipRange = TestPlans.canonicalClipRange();
        assertEquals(clipRange.start(), decode.executionCoverage().start(),
                "coverage start == clip.timelineRange.start (timeline coords)");
        assertEquals(clipRange.end(), decode.executionCoverage().end(),
                "coverage end == clip.timelineRange.end (timeline coords)");
    }

    @Test
    void realMaterializerGraphIsUsableForExtentPruning() {
        // production graph through real materializer: every edge endpoint
        // exists in the node set (graph-closure precondition)
        var input = TestPlans.inputWithTimeline(TestPlans.verifiedRevision());
        RenderMaterializationResult result = new DefaultRenderMaterializer().materialize(input);
        var ids = result.nodes().stream().map(n -> n.id().value()).collect(java.util.stream.Collectors.toSet());
        for (RenderDependencyEdge e : result.edges()) {
            assertTrue(ids.contains(e.producerId().value()), "producer " + e.producerId().value() + " exists");
            assertTrue(ids.contains(e.consumerId().value()), "consumer " + e.consumerId().value() + " exists");
        }
    }
}
