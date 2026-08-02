package com.example.platform.render.ir;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Golden fixture tests: canonical bytes, digest, and field reordering stability.
 */
class GoldenFixtureTest {

    private static MediaProjectIr goldenIr() {
        return new MediaProjectIrBuilder()
            .schemaVersion("media-project/v1")
            .project(new Project("proj-golden", "Golden IR"))
            .assets(List.of(
                new AssetVersionRef("asset-a", "v1"),
                new AssetVersionRef("asset-b", "v2")
            ))
            .timeline(new Timeline("tl-golden", List.of(
                new VideoTrack("track-main", List.of(
                    new Clip("clip-main",
                        new SourceRange(
                            new AssetVersionRef("asset-a", "v1"),
                            RationalTime.of(100, 30),
                            RationalTime.of(500, 30)),
                        RationalTime.zero(30))
                ))
            )))
            .outputs(List.of(
                new OutputSpec("out-main", "mp4", "h264", 1920, 1080,
                    RationalTime.of(30, 1), null),
                new OutputSpec("out-thumb", "jpg", "mjpeg", 320, 180,
                    RationalTime.of(1, 1), null)
            ))
            .artifacts(List.of(
                new ArtifactDeclaration("art-main", "out-main", "render.mp4"),
                new ArtifactDeclaration("art-thumb", "out-thumb", "thumbnail.jpg")
            ))
            .extensions(null)
            .build();
    }

    @Test
    void goldenCanonicalBytes() {
        MediaProjectIr ir = goldenIr();
        MediaProjectIr normalized = IrNormalizer.normalize(ir);
        byte[] bytes = CanonicalSerializer.serialize(normalized);

        byte[] recomputed = CanonicalSerializer.serialize(IrNormalizer.normalize(goldenIr()));
        assertArrayEquals(bytes, recomputed,
            "Golden canonical bytes must be stable across repeated computations");

        String json = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(json.startsWith("{"), "Canonical JSON must start with '{'");
        assertTrue(json.endsWith("}"), "Canonical JSON must end with '}'");
        assertTrue(json.contains("\"schemaVersion\""), "Must contain schemaVersion");
        assertTrue(json.contains("\"media-project/v1\""), "Must contain canonical schema version");
        assertTrue(json.contains("\"proj-golden\""), "Must contain project id");
        assertTrue(json.contains("\"Golden IR\""), "Must contain project name");
    }

    @Test
    void goldenDigest() {
        MediaProjectIr ir = goldenIr();
        IrDigest digest1 = IrDigest.normalizeAndCompute(ir);
        IrDigest digest2 = IrDigest.normalizeAndCompute(goldenIr());

        assertEquals(digest1, digest2, "Golden digest must be stable");
        assertEquals(digest1.algorithm(), "SHA-256");
        assertEquals(digest1.version(), 1);
        assertFalse(digest1.encoded().isEmpty());
        assertFalse(digest1.encoded().endsWith("="));
        assertFalse(digest1.encoded().contains("+"));
        assertFalse(digest1.encoded().contains("/"));

        IrDigest digest3 = IrDigest.normalizeAndCompute(ir);
        assertEquals(digest1.encoded(), digest3.encoded(),
            "Same IR must produce identical digest on third computation");
    }

    @Test
    void fieldReorderingProducesSameCanonicalBytes() {
        MediaProjectIr original = IrNormalizer.normalize(goldenIr());
        byte[] goldenBytes = CanonicalSerializer.serialize(original);

        MediaProjectIr reversedOrder = new MediaProjectIr(
            "media-project/v1",
            new Project("proj-golden", "Golden IR"),
            List.of(new AssetVersionRef("asset-b", "v2"), new AssetVersionRef("asset-a", "v1")),
            new Timeline("tl-golden", List.of(
                new VideoTrack("track-main", List.of(
                    new Clip("clip-main",
                        new SourceRange(
                            new AssetVersionRef("asset-a", "v1"),
                            RationalTime.of(100, 30),
                            RationalTime.of(500, 30)),
                        RationalTime.zero(30))
                ))
            )),
            List.of(
                new OutputSpec("out-thumb", "jpg", "mjpeg", 320, 180,
                    RationalTime.of(1, 1), null),
                new OutputSpec("out-main", "mp4", "h264", 1920, 1080,
                    RationalTime.of(30, 1), null)
            ),
            List.of(
                new ArtifactDeclaration("art-thumb", "out-thumb", "thumbnail.jpg"),
                new ArtifactDeclaration("art-main", "out-main", "render.mp4")
            ),
            null
        );
        MediaProjectIr normalizedReordered = IrNormalizer.normalize(reversedOrder);
        byte[] reorderedBytes = CanonicalSerializer.serialize(normalizedReordered);

        assertArrayEquals(goldenBytes, reorderedBytes,
            "Canonical bytes must be identical regardless of input field ordering");
    }

    @Test
    void fieldReorderingProducesSameDigest() {
        IrDigest originalDigest = IrDigest.normalizeAndCompute(goldenIr());

        MediaProjectIr reversedOrder = new MediaProjectIr(
            "media-project/v1",
            new Project("proj-golden", "Golden IR"),
            List.of(new AssetVersionRef("asset-b", "v2"), new AssetVersionRef("asset-a", "v1")),
            new Timeline("tl-golden", List.of(
                new VideoTrack("track-main", List.of(
                    new Clip("clip-main",
                        new SourceRange(
                            new AssetVersionRef("asset-a", "v1"),
                            RationalTime.of(100, 30),
                            RationalTime.of(500, 30)),
                        RationalTime.zero(30))
                ))
            )),
            List.of(
                new OutputSpec("out-thumb", "jpg", "mjpeg", 320, 180,
                    RationalTime.of(1, 1), null),
                new OutputSpec("out-main", "mp4", "h264", 1920, 1080,
                    RationalTime.of(30, 1), null)
            ),
            List.of(
                new ArtifactDeclaration("art-thumb", "out-thumb", "thumbnail.jpg"),
                new ArtifactDeclaration("art-main", "out-main", "render.mp4")
            ),
            null
        );
        IrDigest reorderedDigest = IrDigest.normalizeAndCompute(reversedOrder);

        assertEquals(originalDigest.encoded(), reorderedDigest.encoded(),
            "Digest must be identical regardless of input field ordering");
    }

    @Test
    void zeroRationalTimeNormalizesToCanonicalForm() {
        RationalTime t1 = RationalTime.of(0, 1001);
        RationalTime t2 = RationalTime.of(0, 30);
        RationalTime t3 = RationalTime.zero(24000);

        assertEquals(t1.numerator().intValue(), 0);
        assertEquals(t1.denominator(), 1L, "Zero time must normalize denominator to 1");
        assertEquals(t2.denominator(), 1L, "Zero time must normalize denominator to 1");
        assertEquals(t3.denominator(), 1L, "Zero time must normalize denominator to 1");

        assertEquals(t1, t2, "Zero times with different denominators must be equal");
        assertEquals(t2, t3, "Zero times with different denominators must be equal");
        assertEquals(t1.hashCode(), t2.hashCode(), "Hash codes must match for equal zero times");
    }
}
