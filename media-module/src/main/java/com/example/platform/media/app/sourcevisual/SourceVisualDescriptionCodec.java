package com.example.platform.media.app.sourcevisual;

import com.example.platform.colorimage.AlphaDescription;
import com.example.platform.colorimage.ChromaLocation;
import com.example.platform.colorimage.ChromaSubsampling;
import com.example.platform.colorimage.Chromaticity;
import com.example.platform.colorimage.ColorDescription;
import com.example.platform.colorimage.ColorPrimaries;
import com.example.platform.colorimage.ColorProfileContentDigest;
import com.example.platform.colorimage.ContentLightMetadata;
import com.example.platform.colorimage.EncodedRasterExtent;
import com.example.platform.colorimage.MasteringDisplayMetadata;
import com.example.platform.colorimage.MatrixCoefficients;
import com.example.platform.colorimage.PixelAspectRatio;
import com.example.platform.colorimage.ProfileFormat;
import com.example.platform.colorimage.RasterSampleDescription;
import com.example.platform.colorimage.Rational;
import com.example.platform.colorimage.SampleFamily;
import com.example.platform.colorimage.SampleOrganization;
import com.example.platform.colorimage.ScanDescription;
import com.example.platform.colorimage.SignalRange;
import com.example.platform.colorimage.SourceOrientation;
import com.example.platform.colorimage.SourceVisualDescription;
import com.example.platform.colorimage.StaticHdrMetadata;
import com.example.platform.colorimage.TransferCharacteristic;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * ROADMAP_18 CIP2: deterministic lossless canonical encoding of
 * SourceVisualDescription. Line-based key/value format; zero Jackson default
 * serialization as semantic authority; zero provider strings; zero double.
 * Roundtrip: persist(S1) -> load -> S1 (exact equality).
 */
public final class SourceVisualDescriptionCodec {

    public static final String FORMAT = "source-visual-v1";

    private SourceVisualDescriptionCodec() {
    }

    public static String encode(SourceVisualDescription s) {
        StringBuilder sb = new StringBuilder();
        sb.append("format=").append(FORMAT).append('\n');
        sb.append("extent=").append(s.rasterExtent().width()).append('x').append(s.rasterExtent().height()).append('\n');
        sb.append("par=").append(r(s.pixelAspectRatio().value())).append('\n');
        RasterSampleDescription rs = s.rasterSampleDescription();
        sb.append("sample=").append(rs.family()).append('|').append(rs.organization()).append('|')
                .append(rs.bitDepth()).append('|').append(rs.chromaSubsampling()).append('|')
                .append(rs.chromaLocation()).append('|').append(rs.alphaComponentPresent()).append('\n');
        encodeColor(sb, s.colorDescription());
        sb.append("alpha=").append(s.alphaDescription()).append('\n');
        sb.append("orient=").append(s.sourceOrientation()).append('\n');
        encodeScan(sb, s.scanDescription());
        encodeHdr(sb, s.staticHdrMetadata());
        return sb.toString();
    }

    private static void encodeColor(StringBuilder sb, ColorDescription c) {
        if (c instanceof ColorDescription.ParametricColorDescription p) {
            sb.append("color=parametric|").append(encodePrimaries(p.primaries())).append('|')
                    .append(p.transfer()).append('|').append(p.matrix()).append('|').append(p.range()).append('\n');
        } else if (c instanceof ColorDescription.ProfileBasedColorDescription pr) {
            sb.append("color=profile|").append(pr.profileFormat()).append('|')
                    .append(pr.profileContentDigest().sha256Hex()).append('\n');
        } else {
            throw new IllegalArgumentException("unrepresentable color description");
        }
    }

    private static String encodePrimaries(ColorPrimaries p) {
        // ':' inner separator avoids collision with the '|' line-field separator
        if (p instanceof ColorPrimaries.WellKnown w) {
            return "wellknown:" + w;
        }
        if (p instanceof ColorPrimaries.Custom cu) {
            return "custom:" + c(cu.red()) + ":" + c(cu.green()) + ":" + c(cu.blue()) + ":" + c(cu.whitePoint());
        }
        throw new IllegalArgumentException("unrepresentable primaries");
    }

    private static String c(Chromaticity ch) {
        return r(ch.x()) + "," + r(ch.y());
    }

    private static String r(Rational q) {
        return q.numerator() + "/" + q.denominator();
    }

    private static void encodeScan(StringBuilder sb, ScanDescription scan) {
        if (scan instanceof ScanDescription.Progressive) {
            sb.append("scan=progressive\n");
        } else if (scan instanceof ScanDescription.Interlaced i) {
            sb.append("scan=interlaced|").append(i.fieldOrder()).append('\n');
        }
    }

    private static void encodeHdr(StringBuilder sb, Optional<StaticHdrMetadata> hdr) {
        if (hdr.isEmpty()) {
            sb.append("hdr=absent\n");
            return;
        }
        StaticHdrMetadata m = hdr.get();
        sb.append("hdr=present\n");
        m.masteringDisplay().ifPresent(md -> sb.append("hdr-mastering=")
                .append(c(md.redPrimary())).append('|').append(c(md.greenPrimary())).append('|')
                .append(c(md.bluePrimary())).append('|').append(c(md.whitePoint())).append('|')
                .append(r(md.minMasteringLuminance())).append('|').append(r(md.maxMasteringLuminance())).append('\n'));
        m.contentLight().ifPresent(cl -> sb.append("hdr-contentlight=")
                .append(r(cl.maxCll())).append('|').append(r(cl.maxFall())).append('\n'));
    }

    public static SourceVisualDescription decode(String payload) {
        java.util.Map<String, String> kv = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> hdrMastering = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> hdrContentLight = new java.util.LinkedHashMap<>();
        for (String line : payload.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("invalid canonical payload line: " + line);
            }
            String key = line.substring(0, eq);
            String value = line.substring(eq + 1);
            if (key.equals("hdr-mastering")) {
                hdrMastering.put("v", value);
            } else if (key.equals("hdr-contentlight")) {
                hdrContentLight.put("v", value);
            } else {
                kv.put(key, value);
            }
        }
        if (!FORMAT.equals(kv.get("format"))) {
            throw new IllegalArgumentException("unknown canonical format");
        }
        String[] extent = kv.get("extent").split("x");
        EncodedRasterExtent rasterExtent = new EncodedRasterExtent(
                Integer.parseInt(extent[0]), Integer.parseInt(extent[1]));
        PixelAspectRatio par = new PixelAspectRatio(q(kv.get("par")));
        String[] sample = kv.get("sample").split("\\|");
        RasterSampleDescription rs = new RasterSampleDescription(
                SampleFamily.valueOf(sample[0]), SampleOrganization.valueOf(sample[1]),
                Integer.parseInt(sample[2]), ChromaSubsampling.valueOf(sample[3]),
                ChromaLocation.valueOf(sample[4]), Boolean.parseBoolean(sample[5]));
        ColorDescription color = decodeColor(kv.get("color"));
        AlphaDescription alpha = AlphaDescription.valueOf(kv.get("alpha"));
        SourceOrientation orient = SourceOrientation.valueOf(kv.get("orient"));
        ScanDescription scan = decodeScan(kv.get("scan"));
        Optional<StaticHdrMetadata> hdr = decodeHdr(kv.get("hdr"), hdrMastering, hdrContentLight);
        return new SourceVisualDescription(rasterExtent, par, rs, color, alpha, orient, scan, hdr);
    }

    private static ColorDescription decodeColor(String value) {
        String[] parts = value.split("\\|");
        if (parts[0].equals("parametric")) {
            return new ColorDescription.ParametricColorDescription(
                    decodePrimaries(parts[1]),
                    TransferCharacteristic.valueOf(parts[2]),
                    MatrixCoefficients.valueOf(parts[3]),
                    SignalRange.valueOf(parts[4]));
        }
        if (parts[0].equals("profile")) {
            return new ColorDescription.ProfileBasedColorDescription(
                    ProfileFormat.valueOf(parts[1]),
                    ColorProfileContentDigest.of(parts[2]));
        }
        throw new IllegalArgumentException("invalid color encoding");
    }

    private static ColorPrimaries decodePrimaries(String value) {
        String[] parts = value.split(":");
        if (parts[0].equals("wellknown")) {
            return ColorPrimaries.WellKnown.valueOf(parts[1]);
        }
        if (parts[0].equals("custom")) {
            return new ColorPrimaries.Custom(ch(parts[1]), ch(parts[2]), ch(parts[3]), ch(parts[4]));
        }
        throw new IllegalArgumentException("invalid primaries encoding");
    }

    private static Chromaticity ch(String value) {
        String[] xy = value.split(",");
        return new Chromaticity(q(xy[0]), q(xy[1]));
    }

    private static Rational q(String value) {
        String[] parts = value.split("/");
        return new Rational(new BigInteger(parts[0]), new BigInteger(parts[1]));
    }

    private static ScanDescription decodeScan(String value) {
        if (value.equals("progressive")) {
            return new ScanDescription.Progressive();
        }
        String[] parts = value.split("\\|");
        return new ScanDescription.Interlaced(ScanDescription.FieldOrder.valueOf(parts[1]));
    }

    private static Optional<StaticHdrMetadata> decodeHdr(String state,
                                                         java.util.Map<String, String> mastering,
                                                         java.util.Map<String, String> contentLight) {
        if (state.equals("absent")) {
            return Optional.empty();
        }
        Optional<MasteringDisplayMetadata> m = mastering.containsKey("v")
                ? Optional.of(decodeMastering(mastering.get("v"))) : Optional.empty();
        Optional<ContentLightMetadata> cl = contentLight.containsKey("v")
                ? Optional.of(decodeContentLight(contentLight.get("v"))) : Optional.empty();
        // StaticHdrMetadata constructor enforces the non-empty invariant (CIC2)
        return Optional.of(new StaticHdrMetadata(m, cl));
    }

    private static MasteringDisplayMetadata decodeMastering(String value) {
        String[] p = value.split("\\|");
        return new MasteringDisplayMetadata(ch(p[0]), ch(p[1]), ch(p[2]), ch(p[3]), q(p[4]), q(p[5]));
    }

    private static ContentLightMetadata decodeContentLight(String value) {
        String[] p = value.split("\\|");
        return new ContentLightMetadata(q(p[0]), q(p[1]));
    }
}
