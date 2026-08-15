package com.example.platform.colorimage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** ROADMAP_18: pure-domain invariants (CIC1-CIC4, exact numerics, fail-closed). */
class ColorImageFoundationTest {

    // ---- exact numerics (CI14) ----
    @Test
    void rationalNormalizesEquality() {
        assertEquals(Rational.of(1, 2), Rational.of(2, 4));
        assertEquals(Rational.of(1, 2).hashCode(), Rational.of(2, 4).hashCode());
        assertEquals(Rational.of("0.3127"), Rational.of(3127, 10000));
    }

    @Test
    void chromaticityExactEquality() {
        assertEquals(Chromaticity.of(3127, 10000, 3290, 10000),
                Chromaticity.of("0.3127", "0.3290"));
        assertThrows(IllegalArgumentException.class, () -> Chromaticity.of(-1, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> Chromaticity.of(0, 1, 3, 2));
    }

    // ---- CIC3: profile format required ----
    @Test
    void profileFormatRequired() {
        ColorProfileContentDigest d = ColorProfileContentDigest.ofText("icc-profile-bytes");
        ColorDescription profile = new ColorDescription.ProfileBasedColorDescription(ProfileFormat.ICC, d);
        assertEquals(ProfileFormat.ICC, ((ColorDescription.ProfileBasedColorDescription) profile).profileFormat());
        assertThrows(IllegalArgumentException.class,
                () -> new ColorDescription.ProfileBasedColorDescription(null, d));
        assertThrows(IllegalArgumentException.class,
                () -> new ColorDescription.ProfileBasedColorDescription(ProfileFormat.ICC, null));
    }

    @Test
    void profileDigestValidates() {
        assertThrows(IllegalArgumentException.class, () -> ColorProfileContentDigest.of("short"));
        assertThrows(IllegalArgumentException.class, () -> ColorProfileContentDigest.of("zz".repeat(32)));
        assertEquals(64, ColorProfileContentDigest.ofText("x").sha256Hex().length());
    }

    // ---- CIC1: sealed root, one authority ----
    @Test
    void colorDescriptionIsSealedSingleAuthority() {
        ColorDescription parametric = new ColorDescription.ParametricColorDescription(
                ColorPrimaries.WellKnown.BT709, TransferCharacteristic.BT709,
                MatrixCoefficients.BT709, SignalRange.LIMITED);
        ColorDescription profile = new ColorDescription.ProfileBasedColorDescription(
                ProfileFormat.ICC, ColorProfileContentDigest.ofText("p"));
        assertTrue(parametric instanceof ColorDescription);
        assertTrue(profile instanceof ColorDescription);
    }

    @Test
    void parametricFieldsRequiredTyped() {
        assertThrows(IllegalArgumentException.class, () -> new ColorDescription.ParametricColorDescription(
                null, TransferCharacteristic.BT709, MatrixCoefficients.BT709, SignalRange.LIMITED));
        assertThrows(IllegalArgumentException.class, () -> new ColorDescription.ParametricColorDescription(
                ColorPrimaries.WellKnown.BT709, null, MatrixCoefficients.BT709, SignalRange.LIMITED));
    }

    // ---- raster ----
    @Test
    void rasterExtentPositiveBounded() {
        assertThrows(IllegalArgumentException.class, () -> new EncodedRasterExtent(0, 100));
        assertThrows(IllegalArgumentException.class, () -> new EncodedRasterExtent(-1, 100));
        assertEquals(new EncodedRasterExtent(1920, 1080), new EncodedRasterExtent(1920, 1080));
    }

    @Test
    void pixelAspectRatioPositiveRational() {
        assertEquals(PixelAspectRatio.of(1, 1), PixelAspectRatio.of(2, 2));
        assertThrows(IllegalArgumentException.class, () -> PixelAspectRatio.of(0, 1));
        assertThrows(IllegalArgumentException.class, () -> PixelAspectRatio.of(-1, 2));
    }

    @Test
    void rasterSampleDescriptionValidates() {
        RasterSampleDescription rgb = RasterSampleDescription.rgb(8, false);
        assertFalse(rgb.alphaComponentPresent());
        RasterSampleDescription yuv = RasterSampleDescription.ycbcr(10, ChromaSubsampling.SAMPLE_420);
        assertEquals(SampleFamily.YCbCr, yuv.family());
        assertThrows(IllegalArgumentException.class,
                () -> new RasterSampleDescription(SampleFamily.RGB, SampleOrganization.INTERLEAVED,
                        8, ChromaSubsampling.SAMPLE_420, ChromaLocation.LEFT, false));
        assertThrows(IllegalArgumentException.class, () -> RasterSampleDescription.rgb(0, false));
        assertThrows(IllegalArgumentException.class, () -> RasterSampleDescription.rgb(128, false));
    }

    // ---- CIC4: alpha consistency ----
    @Test
    void alphaConsistencyEnforced() {
        RasterSampleDescription noAlpha = RasterSampleDescription.rgb(8, false);
        RasterSampleDescription withAlpha = RasterSampleDescription.rgb(8, true);
        assertThrows(IllegalArgumentException.class, () -> new SourceVisualDescription(
                new EncodedRasterExtent(1920, 1080), PixelAspectRatio.square(), noAlpha,
                new ColorDescription.ParametricColorDescription(ColorPrimaries.WellKnown.BT709,
                        TransferCharacteristic.BT709, MatrixCoefficients.BT709, SignalRange.LIMITED),
                AlphaDescription.STRAIGHT, SourceOrientation.NORMAL, new ScanDescription.Progressive(),
                java.util.Optional.empty()));
        // no-alpha + NO_ALPHA valid
        new SourceVisualDescription(new EncodedRasterExtent(1920, 1080), PixelAspectRatio.square(), noAlpha,
                new ColorDescription.ParametricColorDescription(ColorPrimaries.WellKnown.BT709,
                        TransferCharacteristic.BT709, MatrixCoefficients.BT709, SignalRange.LIMITED),
                AlphaDescription.NO_ALPHA, SourceOrientation.NORMAL, new ScanDescription.Progressive(),
                java.util.Optional.empty());
        // alpha present + UNSPECIFIED valid
        new SourceVisualDescription(new EncodedRasterExtent(1920, 1080), PixelAspectRatio.square(), withAlpha,
                new ColorDescription.ParametricColorDescription(ColorPrimaries.WellKnown.BT709,
                        TransferCharacteristic.BT709, MatrixCoefficients.BT709, SignalRange.LIMITED),
                AlphaDescription.UNSPECIFIED, SourceOrientation.NORMAL, new ScanDescription.Progressive(),
                java.util.Optional.empty());
    }

    // ---- CIC2: static HDR non-empty ----
    @Test
    void staticHdrNonEmptyInvariant() {
        MasteringDisplayMetadata mastering = new MasteringDisplayMetadata(
                Chromaticity.of("0.680", "0.320"), Chromaticity.of("0.265", "0.690"),
                Chromaticity.of("0.150", "0.060"), Chromaticity.of("0.3127", "0.3290"),
                Rational.of(1, 1000), Rational.of(1000, 1));
        ContentLightMetadata light = new ContentLightMetadata(Rational.of(1000, 1), Rational.of(400, 1));
        assertThrows(IllegalArgumentException.class, () -> new StaticHdrMetadata(
                java.util.Optional.empty(), java.util.Optional.empty()));
        StaticHdrMetadata.of(mastering);
        StaticHdrMetadata.of(light);
        StaticHdrMetadata.of(mastering, light);
    }

    @Test
    void masteringValidation() {
        Chromaticity r = Chromaticity.of("0.680", "0.320");
        assertThrows(IllegalArgumentException.class, () -> new MasteringDisplayMetadata(
                r, r, r, r, Rational.of(-1, 1), Rational.of(1000, 1)));
        assertThrows(IllegalArgumentException.class, () -> new MasteringDisplayMetadata(
                r, r, r, r, Rational.of(1000, 1), Rational.of(500, 1)));
        // max == min is valid (max >= min invariant)
        new MasteringDisplayMetadata(r, r, r, r, Rational.of(1000, 1), Rational.of(1000, 1));
    }

    @Test
    void contentLightValidation() {
        assertThrows(IllegalArgumentException.class, () -> new ContentLightMetadata(
                Rational.of(-1, 1), Rational.of(400, 1)));
        assertThrows(IllegalArgumentException.class, () -> new ContentLightMetadata(
                Rational.of(1000, 1), Rational.of(-1, 1)));
    }

    // ---- scan / orientation ----
    @Test
    void scanDescriptionTyped() {
        ScanDescription.Progressive p = new ScanDescription.Progressive();
        ScanDescription.Interlaced i = new ScanDescription.Interlaced(ScanDescription.FieldOrder.TOP_FIELD_FIRST);
        assertThrows(IllegalArgumentException.class,
                () -> new ScanDescription.Interlaced(null));
        assertNotEquals(p, i);
    }

    // ---- CIC1 structural: single color authority by type ----
    @Test
    void sourceVisualDescriptionSingleColorAuthority() {
        var fields = java.util.Arrays.stream(SourceVisualDescription.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        long colorFields = fields.stream().filter(f -> f.toLowerCase().contains("colordescription")).count();
        assertEquals(1, colorFields, "exactly ONE color description authority (CIC1)");
    }
}
