package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MultiClipMaterializationTest {
    @Test void connectsEveryVisibleClipWithExactTimingAndMaterializedOutputs() {
        var red = new TimelineClip("red", "asset-red", "stream-red", "artifact-red", "a".repeat(64),
                MediaTime.ZERO, MediaTime.ofRational(1, 2), MediaTime.ZERO, MediaTime.ofRational(1, 2), "MEDIA_STREAM");
        var blue = new TimelineClip("blue", "asset-blue", "stream-blue", "artifact-blue", "b".repeat(64),
                MediaTime.ofRational(1, 1), MediaTime.ofRational(2, 1), MediaTime.ZERO, MediaTime.ofRational(1, 1), "MEDIA_STREAM");
        var doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("track", "Video", TrackType.VIDEO, List.of(red, blue))), TimelineMetadata.empty());
        var digester = new TimelineContentDigester();
        var snapshot = TestPlans.effectSnapshot(List.of(), List.of());
        var revision = TestPlans.revisionWithContext(doc, digester.digest(doc), snapshot);
        var verified = VerifiedTimelineRevisionFactory.verified(revision, digester);
        var base = TestPlans.inputWithTimeline(verified);
        var input = new RenderPlanningInput(base.authoredSnapshot(), base.request(),
                new SourceResolutionInput(java.util.Map.of(
                        new com.example.platform.shared.identity.ArtifactId("artifact-red"), RenderSourceResolutionState.RESOLVED,
                        new com.example.platform.shared.identity.ArtifactId("artifact-blue"), RenderSourceResolutionState.RESOLVED)),
                base.capabilities());
        var result = new DefaultRenderMaterializer().materialize(input);
        assertTrue(new DefaultRenderPlanner().plan(input).diagnostics().stream()
                .noneMatch(diagnostic -> diagnostic.severity() == RenderDiagnosticSeverity.ERROR));
        var timed = result.edges().stream().map(RenderDependencyEdge::dependency)
                .filter(RenderDependency.TimedCompositeInput.class::isInstance)
                .map(RenderDependency.TimedCompositeInput.class::cast).toList();
        assertEquals(2, timed.size());
        assertTrue(timed.contains(new RenderDependency.TimedCompositeInput(MediaTime.ZERO, MediaTime.ofRational(1, 2))));
        assertTrue(timed.contains(new RenderDependency.TimedCompositeInput(MediaTime.ofRational(1, 1), MediaTime.ofRational(2, 1))));
        assertEquals(2, result.nodes().stream().filter(node -> node.kind() instanceof RenderNodeKind.Decode)
                .filter(node -> node.artifactReferences().stream().anyMatch(RenderArtifactReference.IntermediateArtifactExpectation.class::isInstance)).count());
        assertEquals(1, result.nodes().stream().filter(node -> node.kind() instanceof RenderNodeKind.Composite).count());
    }
}
