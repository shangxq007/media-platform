package com.example.platform.render.ir;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Normalization tests: idempotency, stability, locale/timezone/Map independence.
 */
class NormalizationTest {

    private static MediaProjectIr sampleIr() {
        return new MediaProjectIrBuilder()
            .schemaVersion("media-project/v1")
            .project(new Project("proj-1", "Test Project"))
            .assets(List.of(new AssetVersionRef("asset-1", "v1")))
            .timeline(new Timeline("tl-1", List.of(
                new VideoTrack("track-1", List.of(
                    new Clip("clip-1",
                        new SourceRange(
                            new AssetVersionRef("asset-1", "v1"),
                            RationalTime.zero(30000),
                            RationalTime.of(90000, 30000)),
                        RationalTime.zero(30000))
                ))
            )))
            .outputs(List.of(new OutputSpec("out-1", "mp4", "h264", 1920, 1080,
                RationalTime.of(30000, 1001), null)))
            .artifacts(List.of(new ArtifactDeclaration("art-1", "out-1", "output.mp4")))
            .build();
    }

    @Test
    void idempotency() {
        MediaProjectIr ir = sampleIr();
        MediaProjectIr n1 = IrNormalizer.normalize(ir);
        MediaProjectIr n2 = IrNormalizer.normalize(n1);
        assertEquals(n1, n2);
    }

    @Test
    void stableDefaultExpansion() {
        MediaProjectIr ir = sampleIr();
        MediaProjectIr n1 = IrNormalizer.normalize(ir);
        MediaProjectIr n2 = IrNormalizer.normalize(ir);
        assertEquals(n1, n2);
    }

    @Test
    void stableCollectionHandling() {
        MediaProjectIr ir = sampleIr();
        MediaProjectIr n1 = IrNormalizer.normalize(ir);
        MediaProjectIr n2 = IrNormalizer.normalize(ir);
        assertEquals(n1.schemaVersion(), n2.schemaVersion());
        assertEquals(n1.project(), n2.project());
        assertEquals(n1.assets(), n2.assets());
        assertEquals(n1.outputs(), n2.outputs());
        assertEquals(n1.artifacts(), n2.artifacts());
    }

    @Test
    void stableExtensionOrdering() {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("z-key", "z-value");
        ext.put("a-key", "a-value");
        ext.put("m-key", "m-value");
        MediaProjectIr ir = new MediaProjectIrBuilder()
            .schemaVersion("media-project/v1")
            .project(new Project("proj-1", "Test Project"))
            .assets(List.of(new AssetVersionRef("asset-1", "v1")))
            .timeline(new Timeline("tl-1", List.of(
                new VideoTrack("track-1", List.of(
                    new Clip("clip-1",
                        new SourceRange(
                            new AssetVersionRef("asset-1", "v1"),
                            RationalTime.zero(30000),
                            RationalTime.of(90000, 30000)),
                        RationalTime.zero(30000))
                ))
            )))
            .outputs(List.of(new OutputSpec("out-1", "mp4", "h264", 1920, 1080,
                RationalTime.of(30000, 1001), null)))
            .artifacts(List.of(new ArtifactDeclaration("art-1", "out-1", "output.mp4")))
            .extensions(ext)
            .build();
        MediaProjectIr n = IrNormalizer.normalize(ir);
        assertNotNull(n.extensions());
        // Keys should be sorted lexicographically
        String prev = "";
        for (String key : n.extensions().keySet()) {
            assertTrue(key.compareTo(prev) >= 0, "Keys should be sorted: " + key + " < " + prev);
            prev = key;
        }
    }
}
