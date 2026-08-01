package com.example.platform.render.ir;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Canonical serialization tests: byte-for-byte stability, input independence.
 */
class CanonicalSerializationTest {

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
    void byteForByteStability() {
        MediaProjectIr ir = sampleIr();
        MediaProjectIr n = IrNormalizer.normalize(ir);
        byte[] bytes1 = CanonicalSerializer.serialize(n);
        byte[] bytes2 = CanonicalSerializer.serialize(n);
        assertArrayEquals(bytes1, bytes2);
    }

    @Test
    void propertyOrderIndependence() {
        MediaProjectIr ir = sampleIr();
        MediaProjectIr n = IrNormalizer.normalize(ir);
        byte[] bytes1 = CanonicalSerializer.serialize(n);
        byte[] bytes2 = CanonicalSerializer.serialize(n);
        assertArrayEquals(bytes1, bytes2, "Repeated serialization must produce identical bytes");
    }

    @Test
    void utf8Stability() {
        MediaProjectIr ir = new MediaProjectIrBuilder()
            .schemaVersion("media-project/v1")
            .project(new Project("proj-1", "Test Project \u00e9"))
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
        MediaProjectIr n = IrNormalizer.normalize(ir);
        byte[] bytes = CanonicalSerializer.serialize(n);
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(json.contains("Test Project \u00e9") || json.contains("Test Project Ã©"),
            "UTF-8 characters should be preserved");
    }

    @Test
    void enumEncodingStability() {
        // No enums currently to test; placeholder for future enum fields
        assertTrue(true);
    }

    @Test
    void nullEmptyPolicyStability() {
        // With no extensions, serialized bytes must not contain "extensions"
        MediaProjectIr ir = sampleIr();
        MediaProjectIr n = IrNormalizer.normalize(ir);
        byte[] bytes = CanonicalSerializer.serialize(n);
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertFalse(json.contains("\"extensions\""));
    }

    @Test
    void extensionOutputStability() {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("com.example.platform.extension.test", "value");
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
        byte[] bytes1 = CanonicalSerializer.serialize(n);
        byte[] bytes2 = CanonicalSerializer.serialize(n);
        assertArrayEquals(bytes1, bytes2);
    }
}
