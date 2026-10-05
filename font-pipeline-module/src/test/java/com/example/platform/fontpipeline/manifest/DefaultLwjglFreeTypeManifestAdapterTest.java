package com.example.platform.fontpipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.example.platform.fonttext.manifest.FontFaceManifest.AxisCapability;
import com.example.platform.fonttext.manifest.FontFaceManifest.ColorCapability;
import com.example.platform.fonttext.manifest.FontFaceManifest.NamedInstance;
import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link DefaultLwjglFreeTypeManifestAdapter} using real system fonts.
 *
 * <p>No font is copied into the repository: samples are loaded from {@code /usr/share/fonts} (and
 * the browser's bundled Twemoji) and each test is skipped when a suitable sample is absent. Axis
 * expectations are the raw FreeType {@code FT_Fixed} values (16.16).
 */
class DefaultLwjglFreeTypeManifestAdapterTest {

    private static final String ADWAITA = "/usr/share/fonts/Adwaita/AdwaitaSans-Regular.ttf";
    private static final String CANTARELL = "/usr/share/fonts/truetype/Cantarell-VF.otf";
    private static final String TWEMOJI = "/usr/lib64/firefox/fonts/TwemojiMozilla.ttf";
    private static final String STATIC_TTF = "/usr/share/fonts/truetype/Roboto-Light.ttf";

    private final DefaultLwjglFreeTypeManifestAdapter adapter = new DefaultLwjglFreeTypeManifestAdapter();

    @Test
    void parsesVariableTtfAxes() {
        Path font = require(ADWAITA);
        LwjglPartialManifest manifest = parse(font);

        assertEquals(2, manifest.variationAxes().size(), "Adwaita Sans has opsz + wght");
        Map<String, AxisCapability> byTag = manifest.variationAxes().stream()
                .collect(Collectors.toMap(axis -> axis.axis().value(), axis -> axis));
        AxisCapability opsz = byTag.get("opsz");
        AxisCapability wght = byTag.get("wght");
        assertNotNull(opsz, "opsz axis present");
        assertNotNull(wght, "wght axis present");
        assertEquals(fx(14), opsz.min());
        assertEquals(fx(14), opsz.def());
        assertEquals(fx(32), opsz.max());
        assertEquals(fx(100), wght.min());
        assertEquals(fx(400), wght.def());
        assertEquals(fx(900), wght.max());

        assertFalse(manifest.namedInstances().isEmpty(), "Adwaita declares named instances");
        for (NamedInstance instance : manifest.namedInstances()) {
            assertEquals(2, instance.coordinates().size(), "each instance coordinates every axis");
        }
        assertTrue(manifest.namedInstances().stream().anyMatch(instance -> !instance.name().isBlank()),
                "at least one instance name resolves from the name table");
    }

    @Test
    void parsesCffVariableAxes() {
        Path font = require(CANTARELL);
        LwjglPartialManifest manifest = parse(font);

        assertEquals(1, manifest.variationAxes().size(), "Cantarell-VF has a single wght axis");
        AxisCapability wght = manifest.variationAxes().get(0);
        assertEquals("wght", wght.axis().value());
        assertEquals(fx(100), wght.min());
        assertEquals(fx(400), wght.def());
        assertEquals(fx(800), wght.max());
        assertEquals(5, manifest.namedInstances().size());
    }

    @Test
    void parsesColorFontCapabilities() {
        Path font = require(TWEMOJI);
        LwjglPartialManifest manifest = parse(font);

        List<String> technologies = manifest.colorCapabilities().stream()
                .map(ColorCapability::technology)
                .collect(Collectors.toList());
        assertTrue(technologies.contains("COLR"), "Twemoji is a COLR font: " + technologies);
        assertTrue(technologies.contains("CPAL"), "Twemoji has a CPAL palette: " + technologies);
        assertTrue(manifest.variationAxes().isEmpty(), "Twemoji is not variable");
        assertFalse(manifest.hasStat(), "Twemoji has no STAT table");
    }

    @Test
    void nonVariableFontHasEmptyAxes() {
        LwjglPartialManifest manifest = parse(require(STATIC_TTF));

        assertTrue(manifest.variationAxes().isEmpty(), "static font has no variation axes");
        assertTrue(manifest.namedInstances().isEmpty(), "static font has no named instances");
        assertTrue(manifest.colorCapabilities().isEmpty(), "static font has no color capabilities");
    }

    @Test
    void hasStatTracksTablePresence() {
        assertTrue(parse(require(ADWAITA)).hasStat(), "Adwaita Sans has a STAT table");
        assertFalse(parse(require(STATIC_TTF)).hasStat(), "Roboto Light has no STAT table");
    }

    @Test
    void malformedInputThrows() {
        byte[] truncated = Arrays.copyOf(read(require(ADWAITA)), 2000);

        assertThrows(FontManifestParseException.class,
                () -> adapter.parse(FontContentDigest.ofBytes(truncated), truncated, new FaceIndex(0)));
    }

    @Test
    void invalidFaceIndexThrows() {
        byte[] bytes = read(require(ADWAITA));

        assertThrows(FontManifestParseException.class,
                () -> adapter.parse(FontContentDigest.ofBytes(bytes), bytes, new FaceIndex(5)));
    }

    @Test
    void lifecycleStableAcrossRuns() {
        byte[] bytes = read(require(ADWAITA));
        FontContentDigest digest = FontContentDigest.ofBytes(bytes);

        for (int run = 0; run < 5; run++) {
            LwjglPartialManifest manifest = adapter.parse(digest, bytes, new FaceIndex(0));
            assertEquals(2, manifest.variationAxes().size(), "run " + run + " must remain stable");
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private LwjglPartialManifest parse(Path font) {
        byte[] bytes = read(font);
        return adapter.parse(FontContentDigest.ofBytes(bytes), bytes, new FaceIndex(0));
    }

    private static Path require(String path) {
        Path font = Path.of(path);
        assumeTrue(Files.exists(font), "font sample not installed: " + path);
        return font;
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Whole user-space axis value to the raw FreeType 16.16 {@code FT_Fixed} value. */
    private static long fx(long whole) {
        return whole << 16;
    }
}
