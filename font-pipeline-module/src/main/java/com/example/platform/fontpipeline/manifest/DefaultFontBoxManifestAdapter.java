package com.example.platform.fontpipeline.manifest;

import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.resource.FontFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.HeaderTable;
import org.apache.fontbox.ttf.MaximumProfileTable;
import org.apache.fontbox.ttf.NamingTable;
import org.apache.fontbox.ttf.OS2WindowsMetricsTable;
import org.apache.fontbox.ttf.OTFParser;
import org.apache.fontbox.ttf.PostScriptTable;
import org.apache.fontbox.ttf.TTFTable;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;

/**
 * Apache FontBox-backed {@link FontBoxManifestAdapter}.
 *
 * <p>Container/format is detected from the leading sfnt signature rather than by exception-driven
 * retry: {@code ttcf} → TTC, {@code OTTO} → CFF-OTF, otherwise TrueType. Malformed bytes or an
 * out-of-range collection face index surface as {@link FontManifestParseException}.
 *
 * <p>{@code slantStyle} is derived (FontBox exposes no single "slant" accessor) with the rule:
 * italic iff the {@code OS/2} {@code fsSelection} italic bit or the {@code head} {@code macStyle}
 * italic bit is set; else oblique iff the {@code OS/2} oblique bit is set or the {@code post}
 * italic angle is non-zero; otherwise upright. All three inputs are optional; a missing table
 * degrades to the neutral value for that input.
 */
public final class DefaultFontBoxManifestAdapter implements FontBoxManifestAdapter {

    private static final int TTC_TAG = 0x74746366; // 'ttcf'
    private static final int OTTO_TAG = 0x4F54544F; // 'OTTO'
    private static final int MAX_UNICODE_CODE_POINT = 0x10FFFF;
    private static final int OS2_FS_SELECTION_ITALIC = 0x0001;
    private static final int OS2_FS_SELECTION_OBLIQUE = 0x0200;
    private static final int HEAD_MAC_STYLE_ITALIC = 0x0002;

    /** {@code "ITALIC"} when the font is slanted by design. */
    public static final String SLANT_ITALIC = "ITALIC";
    /** {@code "OBLIQUE"} when the font is mechanically slanted. */
    public static final String SLANT_OBLIQUE = "OBLIQUE";
    /** {@code "UPRIGHT"} when no slant evidence is present. */
    public static final String SLANT_UPRIGHT = "UPRIGHT";

    @Override
    public PartialFontManifest parse(FontContentDigest sourceDigest, byte[] fontBytes, FaceIndex faceIndex) {
        Objects.requireNonNull(sourceDigest, "sourceDigest");
        Objects.requireNonNull(fontBytes, "fontBytes");
        Objects.requireNonNull(faceIndex, "faceIndex");
        if (fontBytes.length < 4) {
            throw new FontManifestParseException("Font bytes are too short to contain an sfnt signature");
        }
        int signature = readSignature(fontBytes);
        try {
            if (signature == TTC_TAG) {
                return parseCollection(sourceDigest, fontBytes, faceIndex.value());
            }
            if (signature == OTTO_TAG) {
                return parseSingle(sourceDigest, FontFormat.OPENTYPE_CFF, faceIndex,
                        new OTFParser().parse(new RandomAccessReadBuffer(fontBytes)));
            }
            return parseSingle(sourceDigest, FontFormat.TRUETYPE, faceIndex,
                    new TTFParser().parse(new RandomAccessReadBuffer(fontBytes)));
        } catch (IOException e) {
            throw new FontManifestParseException("Malformed or unparseable font bytes", e);
        }
    }

    private PartialFontManifest parseSingle(
            FontContentDigest sourceDigest, FontFormat format, FaceIndex faceIndex, TrueTypeFont font)
            throws IOException {
        try {
            return extract(sourceDigest, format, faceIndex, font);
        } finally {
            font.close();
        }
    }

    private PartialFontManifest parseCollection(FontContentDigest sourceDigest, byte[] fontBytes, int faceIndex)
            throws IOException {
        try (TrueTypeCollection collection = new TrueTypeCollection(new ByteArrayInputStream(fontBytes))) {
            PartialFontManifest[] requested = new PartialFontManifest[1];
            int[] seen = {0};
            collection.processAllFonts(font -> {
                if (seen[0] == faceIndex) {
                    requested[0] = extract(sourceDigest, FontFormat.TRUETYPE_COLLECTION, new FaceIndex(faceIndex), font);
                }
                seen[0]++;
            });
            if (requested[0] == null) {
                throw new FontManifestParseException(
                        "faceIndex " + faceIndex + " is out of range for a collection with " + seen[0] + " face(s)");
            }
            return requested[0];
        }
    }

    private PartialFontManifest extract(
            FontContentDigest sourceDigest, FontFormat format, FaceIndex faceIndex, TrueTypeFont font)
            throws IOException {
        Map<String, TTFTable> tables = font.getTableMap();
        NamingTable naming = font.getNaming();
        String familyName = naming == null ? null : naming.getFontFamily();
        String subfamilyName = naming == null ? null : naming.getFontSubFamily();
        OS2WindowsMetricsTable os2 = font.getOS2Windows();
        int weightClass = os2 == null ? 0 : os2.getWeightClass();
        int widthClass = os2 == null ? 0 : os2.getWidthClass();
        MaximumProfileTable maximumProfile = font.getMaximumProfile();
        int glyphCount = maximumProfile == null ? 0 : maximumProfile.getNumGlyphs();
        String slantStyle = deriveSlantStyle(os2, font.getHeader(), font.getPostScript());
        Set<Integer> unicodeCoverage = unicodeCoverage(font.getUnicodeCmapLookup());
        return new PartialFontManifest(
                sourceDigest,
                format,
                faceIndex,
                familyName,
                subfamilyName,
                weightClass,
                widthClass,
                slantStyle,
                glyphCount,
                unicodeCoverage,
                tables.containsKey("GSUB"),
                tables.containsKey("GPOS"),
                tables.containsKey("GDEF"));
    }

    private static String deriveSlantStyle(
            OS2WindowsMetricsTable os2, HeaderTable head, PostScriptTable post) {
        int fsSelection = os2 == null ? 0 : os2.getFsSelection();
        int macStyle = head == null ? 0 : head.getMacStyle();
        if ((fsSelection & OS2_FS_SELECTION_ITALIC) != 0 || (macStyle & HEAD_MAC_STYLE_ITALIC) != 0) {
            return SLANT_ITALIC;
        }
        if ((fsSelection & OS2_FS_SELECTION_OBLIQUE) != 0) {
            return SLANT_OBLIQUE;
        }
        if (post != null && post.getItalicAngle() != 0f) {
            return SLANT_OBLIQUE;
        }
        return SLANT_UPRIGHT;
    }

    private static Set<Integer> unicodeCoverage(CmapLookup cmapLookup) {
        Set<Integer> coverage = new LinkedHashSet<>();
        if (cmapLookup == null) {
            return coverage;
        }
        for (int codePoint = 0; codePoint <= MAX_UNICODE_CODE_POINT; codePoint++) {
            if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
                continue;
            }
            if (cmapLookup.getGlyphId(codePoint) != 0) {
                coverage.add(codePoint);
            }
        }
        return coverage;
    }

    private static int readSignature(byte[] fontBytes) {
        return ((fontBytes[0] & 0xff) << 24)
                | ((fontBytes[1] & 0xff) << 16)
                | ((fontBytes[2] & 0xff) << 8)
                | (fontBytes[3] & 0xff);
    }
}
