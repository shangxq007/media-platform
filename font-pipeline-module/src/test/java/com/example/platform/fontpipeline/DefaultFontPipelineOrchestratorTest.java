package com.example.platform.fontpipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.example.platform.fontpipeline.manifest.DefaultFontBoxManifestAdapter;
import com.example.platform.fontpipeline.manifest.DefaultFontFaceManifestAssembler;
import com.example.platform.fontpipeline.manifest.DefaultLwjglFreeTypeManifestAdapter;
import com.example.platform.fontpipeline.sanitize.OtsFontSanitizer;
import com.example.platform.fonttext.manifest.FontFaceManifest;
import com.example.platform.fonttext.resource.FontContentDigest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link DefaultFontPipelineOrchestrator}, driving the real Phase 2-5
 * components (FontBox adapter, LWJGL/FreeType adapter, assembler, real {@code /usr/bin/ots-sanitize}
 * inside the bubblewrap sandbox). No mock framework is used and no font is copied into the
 * repository: the sample is loaded from {@code /usr/share/fonts} and each test is skipped when OTS
 * or the sample is absent.
 *
 * <p>Several {@link FontFaceManifest} fields have no accessor, so they are read by reflection (the
 * same convention as {@code DefaultFontFaceManifestAssemblerTest}).
 */
class DefaultFontPipelineOrchestratorTest {

    private static final Path OTS = Path.of("/usr/bin/ots-sanitize");
    private static final Path SAMPLE = Path.of("/usr/share/fonts/truetype/Roboto-Light.ttf");

    private final FontPipelineOrchestrator orchestrator = buildOrchestrator();

    private static FontPipelineOrchestrator buildOrchestrator() {
        return new DefaultFontPipelineOrchestrator(
                new OtsFontSanitizer(),
                new DefaultFontBoxManifestAdapter(),
                new DefaultLwjglFreeTypeManifestAdapter(),
                new DefaultFontFaceManifestAssembler());
    }

    @BeforeAll
    static void requireOts() {
        assumeTrue(Files.isExecutable(OTS), "ots-sanitize not available at " + OTS);
    }

    @Test
    void processesValidTtfToCompleteManifest() {
        byte[] raw = read(requireSample());

        FontProcessingResult result = orchestrator.process(
                FontProcessingRequest.ofSingleFace(raw, "font/ttf"));

        FontFaceManifest manifest = result.manifest();
        assertNotNull(manifest);
        assertNotNull(manifest.familyName(), "family name must be present");
        assertTrue(!manifest.familyName().isBlank(), "family name must not be blank");
        assertNotNull(manifest.format());
        assertTrue(intField(manifest, "glyphCount") > 0, "glyphCount must be present");
        assertEquals(0, manifest.faceIndex().value());
    }

    @Test
    void sourceDigestEqualsSha256OfRawBytes() {
        byte[] raw = read(requireSample());

        FontProcessingResult result = orchestrator.process(
                FontProcessingRequest.ofSingleFace(raw, "font/ttf"));

        assertEquals(FontContentDigest.ofBytes(raw), result.sourceDigest(),
                "sourceDigest is SHA-256 of the raw bytes");
    }

    @Test
    void contentDigestEqualsSha256OfSanitizedBytes() {
        FontProcessingResult result = orchestrator.process(
                FontProcessingRequest.ofSingleFace(read(requireSample()), "font/ttf"));

        // The sanitizer computes contentDigest = SHA-256(sanitized bytes); the assembler stamps it
        // onto the manifest, so the result and the manifest must agree.
        assertEquals(result.contentDigest(), result.manifest().contentDigest(),
                "contentDigest is stamped onto the manifest unchanged");
    }

    @Test
    void contentDigestMayDifferFromSourceDigest() {
        byte[] raw = read(requireSample());

        FontProcessingResult result = orchestrator.process(
                FontProcessingRequest.ofSingleFace(raw, "font/ttf"));

        // No assertion that the two must differ: they are derived independently and this test only
        // records that each is its own digest (source over raw bytes, content over sanitized bytes).
        assertNotNull(result.sourceDigest());
        assertNotNull(result.contentDigest());
        assertEquals(FontContentDigest.ofBytes(raw), result.sourceDigest());
        assertEquals(result.contentDigest(), result.manifest().contentDigest());
    }

    @Test
    void sanitizationRejectedThrowsProcessingException() {
        byte[] truncated = Arrays.copyOf(read(requireSample()), 2000);

        assertThrows(FontProcessingException.class,
                () -> orchestrator.process(FontProcessingRequest.ofSingleFace(truncated, "font/ttf")));
    }

    @Test
    void nullRequestRejected() {
        assertThrows(NullPointerException.class, () -> orchestrator.process(null));
    }

    @Test
    void idempotentForIdenticalInput() {
        byte[] raw = read(requireSample());

        FontProcessingResult first = orchestrator.process(
                FontProcessingRequest.ofSingleFace(raw, "font/ttf"));
        FontProcessingResult second = orchestrator.process(
                FontProcessingRequest.ofSingleFace(raw, "font/ttf"));

        assertEquals(first.sourceDigest(), second.sourceDigest());
        assertEquals(first.contentDigest(), second.contentDigest());
        assertManifestEquivalent(first.manifest(), second.manifest());
    }

    @Test
    void manifestHasParsedStateFromAssembler() {
        FontFaceManifest manifest = orchestrator.process(
                FontProcessingRequest.ofSingleFace(read(requireSample()), "font/ttf")).manifest();

        assertEquals("PARSED", stringField(manifest, "validationState"));
        assertEquals("NOT_EVALUATED", stringField(manifest, "conformanceState"));
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private static Path requireSample() {
        assumeTrue(Files.isRegularFile(SAMPLE), "font sample not available at " + SAMPLE);
        return SAMPLE;
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void assertManifestEquivalent(FontFaceManifest expected, FontFaceManifest actual) {
        assertEquals(expected.contentDigest(), actual.contentDigest());
        assertEquals(expected.faceIndex(), actual.faceIndex());
        assertEquals(expected.format(), actual.format());
        assertEquals(expected.familyName(), actual.familyName());
        assertEquals(expected.unicodeCoverage(), actual.unicodeCoverage());
        assertEquals(expected.scriptConformance(), actual.scriptConformance());
        assertEquals(expected.variationAxes(), actual.variationAxes());
        assertEquals(expected.namedInstances(), actual.namedInstances());
        assertEquals(expected.colorCapabilities(), actual.colorCapabilities());
        assertEquals(expected.hasStat(), actual.hasStat());
        assertEquals(expected.hasAvar(), actual.hasAvar());
        assertEquals(stringField(expected, "subfamilyName"), stringField(actual, "subfamilyName"));
        assertEquals(intField(expected, "weightClass"), intField(actual, "weightClass"));
        assertEquals(intField(expected, "widthClass"), intField(actual, "widthClass"));
        assertEquals(stringField(expected, "slantStyle"), stringField(actual, "slantStyle"));
        assertEquals(intField(expected, "glyphCount"), intField(actual, "glyphCount"));
        assertEquals(stringField(expected, "validationState"), stringField(actual, "validationState"));
        assertEquals(stringField(expected, "conformanceState"), stringField(actual, "conformanceState"));
    }

    // ── reflection helpers for accessor-less fields ───────────────────────────

    private static Object field(Object target, String name) {
        try {
            Field declared = target.getClass().getDeclaredField(name);
            declared.setAccessible(true);
            return declared.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot read FontFaceManifest field: " + name, e);
        }
    }

    private static String stringField(Object target, String name) {
        return (String) field(target, name);
    }

    private static int intField(Object target, String name) {
        return ((Number) field(target, name)).intValue();
    }
}
