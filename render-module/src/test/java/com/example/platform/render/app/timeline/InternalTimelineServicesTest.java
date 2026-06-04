package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.internal.SemanticChangeType;
import com.example.platform.render.domain.timeline.internal.DirtyScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.example.platform.shared.test.FixturePath;
import java.nio.file.Files;
import java.nio.file.Path;

class InternalTimelineServicesTest {

    private TimelineCanonicalizer canonicalizer;
    private TimelineSemanticDiffService diffService;
    private RenderImpactAnalyzer impactAnalyzer;

    @BeforeEach
    void setUp() {
        canonicalizer = new TimelineCanonicalizer();
        diffService = new TimelineSemanticDiffService(canonicalizer);
        impactAnalyzer = new RenderImpactAnalyzer();
    }

    @Test
    void canonicalizeV1SampleIsStable() throws Exception {
        String json = loadSample();
        var r1 = canonicalizer.canonicalize(json);
        var r2 = canonicalizer.canonicalize(json);
        assertEquals(r1.timelineJson(), r2.timelineJson());
        assertEquals("1.0", r1.schemaVersion());
    }

    @Test
    void detectsPackagingOnlyChange() throws Exception {
        String base = loadSample();
        String patched = base.replace("\"segmentDurationSec\": 4", "\"segmentDurationSec\": 6");
        var diff = diffService.diff(base, patched);
        assertTrue(diff.hasChanges());
        assertTrue(diff.changes().stream()
                .anyMatch(c -> c.type() == SemanticChangeType.PACKAGING_PARAM_CHANGED));
        var impact = impactAnalyzer.analyze(diff);
        assertFalse(impact.fullReRenderRequired());
        assertTrue(impact.dirtyScopes().contains(DirtyScope.PACKAGING));
    }

    @Test
    void revisionOnlyYieldsNoDirtyScopes() throws Exception {
        String base = loadSample();
        String bumped = base.replace("\"revision\": 42", "\"revision\": 43");
        var diff = diffService.diff(base, bumped);
        assertEquals(1, diff.changes().size());
        assertEquals(SemanticChangeType.REVISION_ONLY, diff.changes().get(0).type());
        var impact = impactAnalyzer.analyze(diff);
        assertTrue(impact.dirtyScopes().isEmpty());
        assertFalse(impact.fullReRenderRequired());
    }

    @Test
    void rejectsLegacyTracksOnlyJson() {
        String legacy = """
                {"id":"tl-legacy","tracks":[{"id":"t1","type":"VIDEO","clips":[]}]}
                """;
        assertThrows(IllegalArgumentException.class, () -> canonicalizer.canonicalize(legacy));
    }

    private String loadSample() throws Exception {
        Path path = FixturePath.docsFixture("media-rendering/examples/timeline-v1-full-sample.json");
        return Files.readString(path);
    }
}
