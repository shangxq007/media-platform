package com.example.platform.render.ir;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

/**
 * Determinism tests: canonical bytes and digest MUST be identical across
 * locale, timezone, Map insertion order, and repeated JVM executions.
 */
class DeterminismTest {

    private static MediaProjectIr sampleIr(Map<String, Object> extensions) {
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
            .extensions(extensions)
            .build();
    }

    /**
     * Verify determinism across 3 repeated JVM runs (simulated by repeated test execution).
     */
    @RepeatedTest(3)
    void repeatedJvmExecution() {
        MediaProjectIr ir = sampleIr(null);
        MediaProjectIr n = IrNormalizer.normalize(ir);
        byte[] bytes = CanonicalSerializer.serialize(n);
        IrDigest digest = IrDigest.compute(n);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        assertNotNull(digest);
        assertFalse(digest.encoded().isEmpty());
    }

    /**
     * Verify determinism across Locale.US and Locale.CHINA.
     */
    @Test
    void localeIndependence() {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("a", 1);
        ext.put("b", 2);
        MediaProjectIr ir = sampleIr(ext);

        // Save original locale
        Locale original = Locale.getDefault();

        try {
            // Locale.US
            Locale.setDefault(Locale.US);
            MediaProjectIr n1 = IrNormalizer.normalize(ir);
            byte[] bytes1 = CanonicalSerializer.serialize(n1);
            IrDigest digest1 = IrDigest.compute(n1);

            // Locale.CHINA
            Locale.setDefault(Locale.CHINA);
            MediaProjectIr n2 = IrNormalizer.normalize(ir);
            byte[] bytes2 = CanonicalSerializer.serialize(n2);
            IrDigest digest2 = IrDigest.compute(n2);

            assertArrayEquals(bytes1, bytes2, "Canonical bytes must be identical across locales");
            assertEquals(digest1.encoded(), digest2.encoded(), "Digest must be identical across locales");
        } finally {
            Locale.setDefault(original);
        }
    }

    /**
     * Verify determinism across UTC and Asia/Shanghai timezones.
     */
    @Test
    void timezoneIndependence() {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("a", 1);
        ext.put("b", 2);
        MediaProjectIr ir = sampleIr(ext);

        TimeZone original = TimeZone.getDefault();

        try {
            // UTC
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            MediaProjectIr n1 = IrNormalizer.normalize(ir);
            byte[] bytes1 = CanonicalSerializer.serialize(n1);
            IrDigest digest1 = IrDigest.compute(n1);

            // Asia/Shanghai
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            MediaProjectIr n2 = IrNormalizer.normalize(ir);
            byte[] bytes2 = CanonicalSerializer.serialize(n2);
            IrDigest digest2 = IrDigest.compute(n2);

            assertArrayEquals(bytes1, bytes2, "Canonical bytes must be identical across timezones");
            assertEquals(digest1.encoded(), digest2.encoded(), "Digest must be identical across timezones");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    /**
     * Verify determinism across different Map implementations (HashMap, LinkedHashMap, TreeMap).
     */
    @Test
    void mapInsertionOrderIndependence() {
        MediaProjectIr ir = sampleIr(null);

        // Build extension maps with different insertion orders
        // HashMap: no insertion order guarantee
        Map<String, Object> ext1 = new HashMap<>();
        ext1.put("z", "last");
        ext1.put("a", "first");
        ext1.put("m", "middle");

        Map<String, Object> ext2 = new LinkedHashMap<>();
        ext2.put("a", "first");
        ext2.put("m", "middle");
        ext2.put("z", "last");

        Map<String, Object> ext3 = new TreeMap<>();
        ext3.put("z", "last");
        ext3.put("a", "first");
        ext3.put("m", "middle");

        // Build IRs with different extension maps
        MediaProjectIr ir1 = sampleIr(ext1);
        MediaProjectIr ir2 = sampleIr(ext2);
        MediaProjectIr ir3 = sampleIr(ext3);

        MediaProjectIr n1 = IrNormalizer.normalize(ir1);
        MediaProjectIr n2 = IrNormalizer.normalize(ir2);
        MediaProjectIr n3 = IrNormalizer.normalize(ir3);

        byte[] bytes1 = CanonicalSerializer.serialize(n1);
        byte[] bytes2 = CanonicalSerializer.serialize(n2);
        byte[] bytes3 = CanonicalSerializer.serialize(n3);

        IrDigest digest1 = IrDigest.compute(n1);
        IrDigest digest2 = IrDigest.compute(n2);
        IrDigest digest3 = IrDigest.compute(n3);

        assertArrayEquals(bytes1, bytes2, "HashMap vs LinkedHashMap must produce identical bytes");
        assertArrayEquals(bytes1, bytes3, "HashMap vs TreeMap must produce identical bytes");
        assertEquals(digest1.encoded(), digest2.encoded(), "HashMap vs LinkedHashMap must produce identical digest");
        assertEquals(digest1.encoded(), digest3.encoded(), "HashMap vs TreeMap must produce identical digest");
    }

    /**
     * Comprehensive determinism: locale + timezone + Map — all permutations.
     */
    @Test
    void fullDeterminismMatrix() {
        Map<String, Object> extHm = new HashMap<>();
        extHm.put("z", "last");
        extHm.put("a", "first");

        Map<String, Object> extLhm = new LinkedHashMap<>();
        extLhm.put("a", "first");
        extLhm.put("z", "last");

        Map<String, Object> extTm = new TreeMap<>();
        extTm.put("z", "last");
        extTm.put("a", "first");

        Locale[] locales = {Locale.US, Locale.CHINA};
        TimeZone[] timezones = {TimeZone.getTimeZone("UTC"), TimeZone.getTimeZone("Asia/Shanghai")};
        @SuppressWarnings("unchecked")
        Map<String, Object>[] extMaps = new Map[]{extHm, extLhm, extTm};

        Locale originalLocale = Locale.getDefault();
        TimeZone originalTz = TimeZone.getDefault();

        try {
            Set<String> allBytes = new LinkedHashSet<>();
            Set<String> allDigests = new LinkedHashSet<>();

            for (Locale loc : locales) {
                for (TimeZone tz : timezones) {
                    for (Map<String, Object> ext : extMaps) {
                        Locale.setDefault(loc);
                        TimeZone.setDefault(tz);
                        MediaProjectIr ir = sampleIr(ext);
                        MediaProjectIr n = IrNormalizer.normalize(ir);
                        byte[] bytes = CanonicalSerializer.serialize(n);
                        IrDigest digest = IrDigest.compute(n);
                        allBytes.add(Base64.getEncoder().encodeToString(bytes));
                        allDigests.add(digest.encoded());
                    }
                }
            }

            assertEquals(1, allBytes.size(),
                "All 12 permutations (2 locales × 2 timezones × 3 map types) must produce identical canonical bytes");
            assertEquals(1, allDigests.size(),
                "All 12 permutations must produce identical digests");
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTz);
        }
    }
}
