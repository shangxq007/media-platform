package com.example.platform.fontpipeline.manifest;

import static org.lwjgl.util.freetype.FreeType.FT_Done_Face;
import static org.lwjgl.util.freetype.FreeType.FT_Done_FreeType;
import static org.lwjgl.util.freetype.FreeType.FT_Done_MM_Var;
import static org.lwjgl.util.freetype.FreeType.FT_Get_MM_Var;
import static org.lwjgl.util.freetype.FreeType.FT_Get_Sfnt_Name;
import static org.lwjgl.util.freetype.FreeType.FT_Get_Sfnt_Name_Count;
import static org.lwjgl.util.freetype.FreeType.FT_HAS_SVG;
import static org.lwjgl.util.freetype.FreeType.FT_Init_FreeType;
import static org.lwjgl.util.freetype.FreeType.FT_Load_Sfnt_Table;
import static org.lwjgl.util.freetype.FreeType.FT_New_Memory_Face;
import static org.lwjgl.util.freetype.FreeType.FT_Palette_Data_Get;

import com.example.platform.fonttext.manifest.FontFaceManifest.AxisCapability;
import com.example.platform.fonttext.manifest.FontFaceManifest.ColorCapability;
import com.example.platform.fonttext.manifest.FontFaceManifest.NamedInstance;
import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.VariationAxisTag;
import com.example.platform.fonttext.typography.VariationCoordinate;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.lwjgl.CLongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_MM_Var;
import org.lwjgl.util.freetype.FT_Palette_Data;
import org.lwjgl.util.freetype.FT_SfntName;
import org.lwjgl.util.freetype.FT_Var_Axis;
import org.lwjgl.util.freetype.FT_Var_Named_Style;

/**
 * LWJGL/FreeType-backed {@link LwjglFreeTypeManifestAdapter}.
 *
 * <p><b>Lifecycle (per the Phase 3 environment probe):</b> native handles are released in the
 * order {@code FT_Done_MM_Var → FT_Done_Face → FT_Done_FreeType}, and LWJGL structs allocated on
 * a {@link MemoryStack} are never explicitly freed (double-free of stack structs was the probe's
 * SIGSEGV root cause). Only the LWJGL-allocated file buffer is freed.
 *
 * <p><b>Axis values:</b> FreeType reports axis bounds as {@code FT_Fixed} (16.16 fixed point).
 * {@code AxisCapability.min/def/max} mirror {@code FT_Var_Axis} and therefore store the raw
 * 16.16 value; the user-space value is {@code raw / 65536.0}.
 *
 * <p><b>Axis flags:</b> LWJGL 3.3.6's {@code FT_Var_Axis} binding does not expose FreeType's
 * {@code flags} field, so {@code AxisCapability.flags} is always {@code 0}. (Every system font in
 * the environment has flags 0; this is recorded as a known limitation rather than fabricated.)
 *
 * <p><b>Color capabilities:</b> detected from authoritative table presence via
 * {@code FT_Load_Sfnt_Table} (plus the {@code COLR} table version) and {@code FT_Palette_Data_Get}
 * for {@code CPAL}. This is O(1) and exact; the glyph-layer iteration API is not used because the
 * manifest records technology capability, not layer content.
 */
public final class DefaultLwjglFreeTypeManifestAdapter implements LwjglFreeTypeManifestAdapter {

    /** {@code FT_Fixed} denominator: 16.16 fixed point. */
    static final long FIXED_ONE = 1 << 16;

    private static final long TAG_STAT = tagOf("STAT");
    private static final long TAG_AVAR = tagOf("avar");
    private static final long TAG_COLR = tagOf("COLR");
    private static final long TAG_SVG = tagOf("SVG ");
    private static final long TAG_CBDT = tagOf("CBDT");
    private static final long TAG_CBLC = tagOf("CBLC");
    private static final long TAG_SBIX = tagOf("sbix");

    @Override
    public LwjglPartialManifest parse(FontContentDigest sourceDigest, byte[] fontBytes, FaceIndex faceIndex) {
        Objects.requireNonNull(sourceDigest, "sourceDigest");
        Objects.requireNonNull(fontBytes, "fontBytes");
        Objects.requireNonNull(faceIndex, "faceIndex");
        if (fontBytes.length < 4) {
            throw new FontManifestParseException("Font bytes are too short to contain an sfnt signature");
        }
        ByteBuffer fileBuffer = MemoryUtil.memAlloc(fontBytes.length);
        try {
            fileBuffer.put(fontBytes).flip();
            try {
                return parseFace(sourceDigest, fileBuffer, faceIndex.value());
            } catch (FontManifestParseException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new FontManifestParseException("LWJGL/FreeType font parse failed", e);
            }
        } finally {
            MemoryUtil.memFree(fileBuffer);
        }
    }

    private LwjglPartialManifest parseFace(FontContentDigest sourceDigest, ByteBuffer fileBuffer, int faceIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pLibrary = stack.mallocPointer(1);
            if (FT_Init_FreeType(pLibrary) != 0) {
                throw new FontManifestParseException("FT_Init_FreeType failed");
            }
            long library = pLibrary.get(0);
            try {
                PointerBuffer pFace = stack.mallocPointer(1);
                int rc = FT_New_Memory_Face(library, fileBuffer, faceIndex, pFace);
                if (rc != 0) {
                    throw new FontManifestParseException(
                            "FT_New_Memory_Face failed (rc=" + rc + ") for face index " + faceIndex);
                }
                FT_Face face = FT_Face.create(pFace.get(0));
                try {
                    return extract(sourceDigest, new FaceIndex(faceIndex), face, library, stack);
                } finally {
                    FT_Done_Face(face);
                }
            } finally {
                FT_Done_FreeType(library);
            }
        }
    }

    private LwjglPartialManifest extract(
            FontContentDigest sourceDigest, FaceIndex faceIndex, FT_Face face, long library, MemoryStack stack) {
        List<AxisCapability> variationAxes = List.of();
        List<NamedInstance> namedInstances = List.of();
        PointerBuffer pMmVar = stack.mallocPointer(1);
        if (FT_Get_MM_Var(face, pMmVar) == 0) {
            FT_MM_Var mmVar = FT_MM_Var.create(pMmVar.get(0));
            try {
                variationAxes = readAxes(mmVar);
                namedInstances = readNamedInstances(face, mmVar, stack);
            } finally {
                FT_Done_MM_Var(library, mmVar);
            }
        }
        boolean hasStat = hasSfntTable(face, TAG_STAT, stack);
        boolean hasAvar = hasSfntTable(face, TAG_AVAR, stack);
        List<ColorCapability> colorCapabilities = readColorCapabilities(face, stack);
        return new LwjglPartialManifest(
                sourceDigest, faceIndex, variationAxes, namedInstances, hasStat, hasAvar, colorCapabilities);
    }

    private static List<AxisCapability> readAxes(FT_MM_Var mmVar) {
        int axisCount = mmVar.num_axis();
        FT_Var_Axis.Buffer axisBuffer = mmVar.axis();
        List<AxisCapability> axes = new ArrayList<>(axisCount);
        for (int i = 0; i < axisCount; i++) {
            FT_Var_Axis axis = axisBuffer.get(i);
            axes.add(new AxisCapability(
                    new VariationAxisTag(tagToString(axis.tag())),
                    axis.minimum(),
                    axis.def(),
                    axis.maximum(),
                    0));
        }
        return axes;
    }

    private static List<NamedInstance> readNamedInstances(FT_Face face, FT_MM_Var mmVar, MemoryStack stack) {
        int axisCount = mmVar.num_axis();
        int instanceCount = mmVar.num_namedstyles();
        FT_Var_Axis.Buffer axisBuffer = mmVar.axis();
        List<VariationAxisTag> tags = new ArrayList<>(axisCount);
        for (int i = 0; i < axisCount; i++) {
            tags.add(new VariationAxisTag(tagToString(axisBuffer.get(i).tag())));
        }
        FT_Var_Named_Style.Buffer styles = mmVar.namedstyle();
        List<NamedInstance> instances = new ArrayList<>(instanceCount);
        for (int i = 0; i < instanceCount; i++) {
            FT_Var_Named_Style style = styles.get(i);
            CLongBuffer coords = style.coords(axisCount);
            List<VariationCoordinate> coordinates = new ArrayList<>(axisCount);
            for (int k = 0; k < axisCount; k++) {
                coordinates.add(new VariationCoordinate(tags.get(k), FontRational.of(coords.get(k), FIXED_ONE)));
            }
            String name = resolveName(face, style.strid(), stack);
            instances.add(new NamedInstance(name == null ? "" : name, coordinates));
        }
        return instances;
    }

    private static List<ColorCapability> readColorCapabilities(FT_Face face, MemoryStack stack) {
        List<ColorCapability> capabilities = new ArrayList<>();
        ByteBuffer colrHeader = readSfntTable(face, TAG_COLR, 2, stack);
        if (colrHeader != null) {
            int version = Short.toUnsignedInt(colrHeader.getShort(0));
            capabilities.add(new ColorCapability(version >= 1 ? "COLRv1" : "COLR"));
        }
        FT_Palette_Data paletteData = FT_Palette_Data.malloc(stack);
        if (FT_Palette_Data_Get(face, paletteData) == 0
                && Short.toUnsignedInt(paletteData.num_palettes()) > 0) {
            capabilities.add(new ColorCapability("CPAL"));
        }
        if (hasSfntTable(face, TAG_SVG, stack) || FT_HAS_SVG(face)) {
            capabilities.add(new ColorCapability("SVG"));
        }
        if (hasSfntTable(face, TAG_CBDT, stack) || hasSfntTable(face, TAG_CBLC, stack)) {
            capabilities.add(new ColorCapability("CBDT/CBLC"));
        }
        if (hasSfntTable(face, TAG_SBIX, stack)) {
            capabilities.add(new ColorCapability("sbix"));
        }
        return capabilities;
    }

    private static boolean hasSfntTable(FT_Face face, long tag, MemoryStack stack) {
        return readSfntTable(face, tag, 1, stack) != null;
    }

    private static ByteBuffer readSfntTable(FT_Face face, long tag, int maxBytes, MemoryStack stack) {
        CLongBuffer length = stack.callocCLong(1);
        length.put(0, maxBytes);
        ByteBuffer buffer = stack.malloc(maxBytes);
        if (FT_Load_Sfnt_Table(face, tag, 0L, buffer, length) != 0) {
            return null;
        }
        buffer.limit((int) Math.min(maxBytes, length.get(0)));
        return buffer;
    }

    /**
     * Resolve a {@code name} table record by name ID, preferring Windows Unicode English
     * (platform 3 / encoding 1 / language 0x409) then any Windows record then any record. Returns
     * {@code null} when no record matches.
     */
    private static String resolveName(FT_Face face, int nameId, MemoryStack stack) {
        int recordCount = FT_Get_Sfnt_Name_Count(face);
        FT_SfntName record = FT_SfntName.malloc(stack);
        String best = null;
        int bestScore = -1;
        for (int i = 0; i < recordCount; i++) {
            if (FT_Get_Sfnt_Name(face, i, record) != 0
                    || Short.toUnsignedInt(record.name_id()) != nameId) {
                continue;
            }
            int platform = Short.toUnsignedInt(record.platform_id());
            int encoding = Short.toUnsignedInt(record.encoding_id());
            int language = Short.toUnsignedInt(record.language_id());
            int score = platform == 3 && encoding == 1 && language == 0x409 ? 3 : platform == 3 ? 2 : 1;
            if (score <= bestScore) {
                continue;
            }
            int length = record.string_len();
            ByteBuffer raw = MemoryUtil.memByteBuffer(MemoryUtil.memAddress(record.string()), length);
            byte[] bytes = new byte[length];
            raw.get(bytes);
            String value = new String(bytes, platform == 3 ? StandardCharsets.UTF_16BE : StandardCharsets.ISO_8859_1)
                    .replace("\u0000", "")
                    .trim();
            if (!value.isEmpty()) {
                best = value;
                bestScore = score;
            }
        }
        return best;
    }

    private static String tagToString(long tag) {
        return new String(new char[] {
                (char) ((tag >> 24) & 0xff),
                (char) ((tag >> 16) & 0xff),
                (char) ((tag >> 8) & 0xff),
                (char) (tag & 0xff)});
    }

    private static long tagOf(String tag) {
        return ((long) (tag.charAt(0) & 0xff) << 24)
                | ((long) (tag.charAt(1) & 0xff) << 16)
                | ((long) (tag.charAt(2) & 0xff) << 8)
                | (tag.charAt(3) & 0xff);
    }
}
