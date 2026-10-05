package com.example.platform.fontpipeline.manifest;

import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.resource.FontFormat;
import java.util.Objects;
import java.util.Set;

/**
 * Statically parsed font metadata produced by a single parser route.
 *
 * <p>This is the subset that the FontBox route can produce from font bytes. A complementary
 * subset (variation axes, color capabilities, {@code STAT}) is produced by the LWJGL/FreeType
 * route; the two are merged into the canonical manifest by an assembler in a later phase.
 *
 * <p>Field semantics follow the parsed source tables: names come from the OpenType {@code name}
 * table, {@code weightClass}/{@code widthClass} from {@code OS/2}, {@code glyphCount} from
 * {@code maxp}, {@code unicodeCoverage} from the best Unicode {@code cmap}, and the
 * {@code hasGsub}/{@code hasGpos}/{@code hasGdef} flags are pure table-presence observations.
 *
 * <p>Names may be {@code null} when the source table is absent; {@code slantStyle} is empty when
 * no slant evidence is available. Values are never fabricated.
 *
 * @param sourceDigest content digest of the parsed bytes
 * @param format detected container/format
 * @param faceIndex face index within a collection (0 for single-face fonts)
 * @param familyName {@code name} table family name (may be {@code null})
 * @param subfamilyName {@code name} table subfamily name (may be {@code null})
 * @param weightClass {@code OS/2} {@code usWeightClass} (0 when absent)
 * @param widthClass {@code OS/2} {@code usWidthClass} (0 when absent)
 * @param slantStyle derived slant observation ({@code "ITALIC"}, {@code "OBLIQUE"}, {@code "UPRIGHT"}, or empty)
 * @param glyphCount {@code maxp} glyph count (0 when absent)
 * @param unicodeCoverage Unicode code points mapped by the best Unicode {@code cmap}
 * @param hasGsub {@code GSUB} table present
 * @param hasGpos {@code GPOS} table present
 * @param hasGdef {@code GDEF} table present
 */
public record PartialFontManifest(
        FontContentDigest sourceDigest,
        FontFormat format,
        FaceIndex faceIndex,
        String familyName,
        String subfamilyName,
        int weightClass,
        int widthClass,
        String slantStyle,
        int glyphCount,
        Set<Integer> unicodeCoverage,
        boolean hasGsub,
        boolean hasGpos,
        boolean hasGdef) {

    public PartialFontManifest {
        Objects.requireNonNull(sourceDigest, "sourceDigest");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(faceIndex, "faceIndex");
        unicodeCoverage = Set.copyOf(unicodeCoverage);
    }
}
