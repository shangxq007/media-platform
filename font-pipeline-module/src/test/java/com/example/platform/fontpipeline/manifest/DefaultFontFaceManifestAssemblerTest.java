package com.example.platform.fontpipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.fonttext.manifest.FontFaceManifest;
import com.example.platform.fonttext.manifest.FontFaceManifest.AxisCapability;
import com.example.platform.fonttext.manifest.FontFaceManifest.ColorCapability;
import com.example.platform.fonttext.manifest.FontFaceManifest.NamedInstance;
import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.resource.FontFormat;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.VariationAxisTag;
import com.example.platform.fonttext.typography.VariationCoordinate;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultFontFaceManifestAssembler}.
 *
 * <p>Pure unit tests: partial manifests are constructed directly (no real font, no filesystem, no
 * mock framework). Several {@link FontFaceManifest} fields have no accessor, so they are read by
 * reflection to assert the assembler's field wiring.
 */
class DefaultFontFaceManifestAssemblerTest {

    private static final FontContentDigest SOURCE = FontContentDigest.ofText("source-font-bytes");
    private static final FontContentDigest SANITIZED = FontContentDigest.ofText("sanitized-font-bytes");
    private static final FontContentDigest OTHER = FontContentDigest.ofText("other-font-bytes");
    private static final FaceIndex FACE_0 = new FaceIndex(0);
    private static final FaceIndex FACE_1 = new FaceIndex(1);

    private final DefaultFontFaceManifestAssembler assembler = new DefaultFontFaceManifestAssembler();

    @Test
    void assemblesCompleteManifest() {
        FontFaceManifest manifest = assembler.assemble(
                fontBox(SOURCE, FACE_0), lwjgl(SOURCE, FACE_0), SANITIZED);

        assertNotNull(manifest);
        assertEquals(SANITIZED, manifest.contentDigest());
        assertEquals(FACE_0, manifest.faceIndex());
        assertEquals(FontFormat.TRUETYPE, manifest.format());
        assertEquals("Test Family", manifest.familyName());
        assertEquals(1, manifest.variationAxes().size());
        assertTrue(manifest.hasStat());
        assertEquals(1, manifest.colorCapabilities().size());
    }

    @Test
    void contentDigestIsFromParameter() {
        FontFaceManifest manifest = assembler.assemble(
                fontBox(SOURCE, FACE_0), lwjgl(SOURCE, FACE_0), SANITIZED);

        assertEquals(SANITIZED, manifest.contentDigest(), "contentDigest comes from the caller");
        assertNotEquals(SOURCE, manifest.contentDigest(), "no contentDigest = sourceDigest fallback");
    }

    @Test
    void sourceDigestMismatchFailsClosed() {
        FontManifestMergeException thrown = assertThrows(FontManifestMergeException.class,
                () -> assembler.assemble(fontBox(SOURCE, FACE_0), lwjgl(OTHER, FACE_0), SANITIZED));

        assertTrue(thrown.getMessage().contains("sourceDigest"));
    }

    @Test
    void faceIndexMismatchFailsClosed() {
        FontManifestMergeException thrown = assertThrows(FontManifestMergeException.class,
                () -> assembler.assemble(fontBox(SOURCE, FACE_0), lwjgl(SOURCE, FACE_1), SANITIZED));

        assertTrue(thrown.getMessage().contains("faceIndex"));
    }

    @Test
    void nullFontBoxRejected() {
        assertThrows(NullPointerException.class,
                () -> assembler.assemble(null, lwjgl(SOURCE, FACE_0), SANITIZED));
    }

    @Test
    void nullLwjglRejected() {
        assertThrows(NullPointerException.class,
                () -> assembler.assemble(fontBox(SOURCE, FACE_0), null, SANITIZED));
    }

    @Test
    void nullContentDigestRejected() {
        assertThrows(NullPointerException.class,
                () -> assembler.assemble(fontBox(SOURCE, FACE_0), lwjgl(SOURCE, FACE_0), null));
    }

    @Test
    void scriptConformanceIsEmpty() {
        FontFaceManifest manifest = assembler.assemble(
                fontBox(SOURCE, FACE_0), lwjgl(SOURCE, FACE_0), SANITIZED);

        assertEquals(Set.of(), manifest.scriptConformance());
    }

    @Test
    void validationStateIsParsed() {
        FontFaceManifest manifest = assembler.assemble(
                fontBox(SOURCE, FACE_0), lwjgl(SOURCE, FACE_0), SANITIZED);

        assertEquals("PARSED", stringField(manifest, "validationState"));
    }

    @Test
    void conformanceStateIsNotEvaluated() {
        FontFaceManifest manifest = assembler.assemble(
                fontBox(SOURCE, FACE_0), lwjgl(SOURCE, FACE_0), SANITIZED);

        assertEquals("NOT_EVALUATED", stringField(manifest, "conformanceState"));
    }

    @Test
    void fontBoxFieldsPreserved() {
        PartialFontManifest fontBox = fontBox(SOURCE, FACE_0);
        FontFaceManifest manifest = assembler.assemble(fontBox, lwjgl(SOURCE, FACE_0), SANITIZED);

        assertEquals(fontBox.faceIndex(), manifest.faceIndex());
        assertEquals(fontBox.format(), manifest.format());
        assertEquals(fontBox.familyName(), manifest.familyName());
        assertEquals(fontBox.unicodeCoverage(), manifest.unicodeCoverage());
        assertEquals(fontBox.subfamilyName(), stringField(manifest, "subfamilyName"));
        assertEquals(fontBox.weightClass(), intField(manifest, "weightClass"));
        assertEquals(fontBox.widthClass(), intField(manifest, "widthClass"));
        assertEquals(fontBox.slantStyle(), stringField(manifest, "slantStyle"));
        assertEquals(fontBox.glyphCount(), intField(manifest, "glyphCount"));
        assertEquals(fontBox.hasGsub(), booleanField(manifest, "hasGsub"));
        assertEquals(fontBox.hasGpos(), booleanField(manifest, "hasGpos"));
        assertEquals(fontBox.hasGdef(), booleanField(manifest, "hasGdef"));
    }

    @Test
    void lwjglFieldsPreserved() {
        LwjglPartialManifest lwjgl = lwjgl(SOURCE, FACE_0);
        FontFaceManifest manifest = assembler.assemble(fontBox(SOURCE, FACE_0), lwjgl, SANITIZED);

        assertEquals(lwjgl.faceIndex(), manifest.faceIndex());
        assertEquals(lwjgl.variationAxes(), manifest.variationAxes());
        assertEquals(lwjgl.namedInstances(), manifest.namedInstances());
        assertEquals(lwjgl.hasStat(), manifest.hasStat());
        assertEquals(lwjgl.hasAvar(), manifest.hasAvar());
        assertEquals(lwjgl.colorCapabilities(), manifest.colorCapabilities());
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private static PartialFontManifest fontBox(FontContentDigest sourceDigest, FaceIndex faceIndex) {
        return new PartialFontManifest(
                sourceDigest,
                FontFormat.TRUETYPE,
                faceIndex,
                "Test Family",
                "Regular",
                400,
                5,
                "UPRIGHT",
                1000,
                Set.of(65, 66, 67),
                true,
                true,
                true);
    }

    private static LwjglPartialManifest lwjgl(FontContentDigest sourceDigest, FaceIndex faceIndex) {
        AxisCapability axis = new AxisCapability(
                new VariationAxisTag("wght"), 100L << 16, 400L << 16, 900L << 16, 0);
        NamedInstance instance = new NamedInstance(
                "Regular",
                List.of(new VariationCoordinate(
                        new VariationAxisTag("wght"), FontRational.of(400L << 16, 1L << 16))));
        return new LwjglPartialManifest(
                sourceDigest,
                faceIndex,
                List.of(axis),
                List.of(instance),
                true,
                true,
                List.of(new ColorCapability("COLR")));
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

    private static boolean booleanField(Object target, String name) {
        return (Boolean) field(target, name);
    }
}
