package com.example.platform.fontpipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.resource.FontFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link DefaultFontBoxManifestAdapter} using real system fonts.
 *
 * <p>No font is copied into the repository: samples are loaded from {@code /usr/share/fonts} and
 * each test is skipped when a suitable sample is absent. The TTC sample is synthesized in memory
 * from a real TTF (a minimal single-face {@code ttcf} container), so collection parsing is
 * exercised without shipping a fixture.
 */
class DefaultFontBoxManifestAdapterTest {

    private static final Path FONT_ROOT = Path.of("/usr/share/fonts");

    private final DefaultFontBoxManifestAdapter adapter = new DefaultFontBoxManifestAdapter();

    private static final List<String> TTF_PREFERENCE = List.of(
            "Roboto-Light.ttf", "Roboto-Regular.ttf", "DejaVuSans.ttf",
            "LiberationSans-Regular.ttf", "FreeSans.ttf");
    private static final List<String> OTF_PREFERENCE = List.of(
            "NimbusRoman-Regular.otf", "NimbusSans-Regular.otf");

    @Test
    void parsesTtfMetadata() {
        Path ttf = requireTtf();
        PartialFontManifest manifest = adapter.parse(
                FontContentDigest.ofBytes(read(ttf)), read(ttf), new FaceIndex(0));

        assertEquals(FontFormat.TRUETYPE, manifest.format());
        assertNotNull(manifest.familyName());
        assertFalse(manifest.familyName().isBlank(), "family name must not be blank");
        assertTrue(manifest.weightClass() > 0, "OS/2 weight class must be present");
        assertTrue(manifest.glyphCount() > 0, "maxp glyph count must be present");
        assertFalse(manifest.unicodeCoverage().isEmpty(), "cmap coverage must not be empty");
    }

    @Test
    void parsesOtfCffMetadata() {
        Path otf = requireOtf();
        PartialFontManifest manifest = adapter.parse(
                FontContentDigest.ofBytes(read(otf)), read(otf), new FaceIndex(0));

        assertEquals(FontFormat.OPENTYPE_CFF, manifest.format());
        assertNotNull(manifest.familyName());
        assertFalse(manifest.familyName().isBlank(), "family name must not be blank");
        assertTrue(manifest.glyphCount() > 0, "maxp glyph count must be present");
        assertFalse(manifest.unicodeCoverage().isEmpty(), "cmap coverage must not be empty");
    }

    @Test
    void parsesTtcFace() {
        Path ttf = requireTtf();
        byte[] ttfBytes = read(ttf);
        byte[] ttcBytes = singleFaceTtc(ttfBytes);

        PartialFontManifest fromTtf = adapter.parse(
                FontContentDigest.ofBytes(ttfBytes), ttfBytes, new FaceIndex(0));
        PartialFontManifest fromTtc = adapter.parse(
                FontContentDigest.ofBytes(ttcBytes), ttcBytes, new FaceIndex(0));

        assertEquals(FontFormat.TRUETYPE_COLLECTION, fromTtc.format());
        assertEquals(0, fromTtc.faceIndex().value());
        assertEquals(fromTtf.familyName(), fromTtc.familyName(), "TTC face must match the source face");
        assertEquals(fromTtf.glyphCount(), fromTtc.glyphCount());
    }

    @Test
    void malformedInputThrows() {
        Path ttf = requireTtf();
        byte[] truncated = java.util.Arrays.copyOf(read(ttf), 2000);

        assertThrows(FontManifestParseException.class,
                () -> adapter.parse(FontContentDigest.ofBytes(truncated), truncated, new FaceIndex(0)));
    }

    @Test
    void invalidFaceIndexThrows() {
        byte[] ttcBytes = singleFaceTtc(read(requireTtf()));

        assertThrows(FontManifestParseException.class,
                () -> adapter.parse(FontContentDigest.ofBytes(ttcBytes), ttcBytes, new FaceIndex(5)));
    }

    @Test
    void gsubGposGdefPresence() {
        Path roboto = findByName("Roboto-Light.ttf");
        assumeTrue(roboto != null, "Roboto-Light.ttf not installed");
        PartialFontManifest manifest = adapter.parse(
                FontContentDigest.ofBytes(read(roboto)), read(roboto), new FaceIndex(0));

        assertTrue(manifest.hasGsub(), "Roboto-Light has a GSUB table");
        assertTrue(manifest.hasGpos(), "Roboto-Light has a GPOS table");
        assertTrue(manifest.hasGdef(), "Roboto-Light has a GDEF table");
    }

    @Test
    void unicodeCoverageContainsAscii() {
        Path ttf = requireTtf();
        Set<Integer> coverage = adapter.parse(
                FontContentDigest.ofBytes(read(ttf)), read(ttf), new FaceIndex(0)).unicodeCoverage();

        assertTrue(coverage.contains((int) 'A'), "coverage must contain 'A' (U+0041)");
        assertTrue(coverage.contains((int) 'a'), "coverage must contain 'a' (U+0061)");
        assertTrue(coverage.contains((int) '0'), "coverage must contain '0' (U+0030)");
    }

    @Test
    void detectsItalicSlant() {
        Path italic = findByNameContains(".otf", "italic");
        assumeTrue(italic != null, "no italic OTF installed");
        PartialFontManifest manifest = adapter.parse(
                FontContentDigest.ofBytes(read(italic)), read(italic), new FaceIndex(0));

        assertEquals(DefaultFontBoxManifestAdapter.SLANT_ITALIC, manifest.slantStyle());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static Path requireTtf() {
        Path ttf = resolve(TTF_PREFERENCE, ".ttf");
        assumeTrue(ttf != null, "no TTF sample installed under " + FONT_ROOT);
        return ttf;
    }

    private static Path requireOtf() {
        Path otf = resolve(OTF_PREFERENCE, ".otf");
        assumeTrue(otf != null, "no OTF sample installed under " + FONT_ROOT);
        return otf;
    }

    private static Path resolve(List<String> preference, String suffix) {
        for (String name : preference) {
            Path p = findByName(name);
            if (p != null) {
                return p;
            }
        }
        return findByNameContains(suffix, "");
    }

    private static Path findByName(String fileName) {
        return walkedFonts().stream()
                .filter(p -> p.getFileName().toString().equalsIgnoreCase(fileName))
                .findFirst()
                .orElse(null);
    }

    private static Path findByNameContains(String suffix, String fragment) {
        String lowerFragment = fragment.toLowerCase(Locale.ROOT);
        return walkedFonts().stream()
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(suffix))
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).contains(lowerFragment))
                .findFirst()
                .orElse(null);
    }

    private static List<Path> walkedFonts() {
        if (!Files.isDirectory(FONT_ROOT)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(FONT_ROOT)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".ttf") || n.endsWith(".otf") || n.endsWith(".ttc");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Wrap one TTF as a minimal single-face TTC: prepend a 16-byte {@code ttcf} header and shift
     * every table offset by +16 (TTC table offsets are file-relative).
     */
    private static byte[] singleFaceTtc(byte[] ttf) {
        int numTables = ((ttf[4] & 0xff) << 8) | (ttf[5] & 0xff);
        byte[] out = new byte[ttf.length + 16];
        System.arraycopy(ttf, 0, out, 16, ttf.length);
        ByteBuffer bb = ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN);
        bb.putInt(0, 0x74746366); // 'ttcf'
        bb.putShort(4, (short) 1);
        bb.putShort(6, (short) 0);
        bb.putInt(8, 1);
        bb.putInt(12, 16);
        for (int i = 0; i < numTables; i++) {
            int record = 16 + 12 + i * 16;
            bb.putInt(record + 8, bb.getInt(record + 8) + 16);
        }
        return out;
    }

}
