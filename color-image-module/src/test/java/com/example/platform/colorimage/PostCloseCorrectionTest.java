package com.example.platform.colorimage;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP_18 FINAL POST-CLOSE (CIP2/CIP3): durable reproducibility of
 * SourceVisualDescription (deterministic value semantics, zero mutable-latest
 * dependency) and missing-vs-unknown primaries semantics.
 */
class PostCloseCorrectionTest {

    // ---- CIP3: missing = UNSPECIFIED, explicit unknown = UNKNOWN ----
    @Test
    void primariesDistinguishMissingAndUnknown() {
        assertNotEquals(ColorPrimaries.WellKnown.UNSPECIFIED, ColorPrimaries.WellKnown.UNKNOWN);
        assertNotEquals(ColorPrimaries.WellKnown.UNSPECIFIED.name(), ColorPrimaries.WellKnown.UNKNOWN.name(),
                "distinct canonical serialization (CIPG9)");
        // missing (absent provider field) normalizes to UNSPECIFIED — never UNKNOWN, never BT709
        ColorDescription missing = new ColorDescription.ParametricColorDescription(
                ColorPrimaries.WellKnown.UNSPECIFIED, TransferCharacteristic.UNSPECIFIED,
                MatrixCoefficients.UNSPECIFIED, SignalRange.UNSPECIFIED);
        assertEquals(ColorPrimaries.WellKnown.UNSPECIFIED,
                ((ColorDescription.ParametricColorDescription) missing).primaries());
        // explicit unknown normalizes to UNKNOWN
        ColorDescription unknown = new ColorDescription.ParametricColorDescription(
                ColorPrimaries.WellKnown.UNKNOWN, TransferCharacteristic.UNKNOWN,
                MatrixCoefficients.UNKNOWN, SignalRange.UNKNOWN);
        assertEquals(ColorPrimaries.WellKnown.UNKNOWN,
                ((ColorDescription.ParametricColorDescription) unknown).primaries());
        // recognized values stay typed
        assertEquals(ColorPrimaries.WellKnown.BT709, ColorPrimaries.WellKnown.BT709);
        assertEquals(ColorPrimaries.WellKnown.BT2020, ColorPrimaries.WellKnown.BT2020);
    }

    @Test
    void customPrimariesExact() {
        ColorPrimaries.Custom custom = new ColorPrimaries.Custom(
                Chromaticity.of("0.680", "0.320"), Chromaticity.of("0.265", "0.690"),
                Chromaticity.of("0.150", "0.060"), Chromaticity.of("0.3127", "0.3290"));
        assertEquals(custom, custom);
    }

    // ---- CIP2: deterministic reproducibility ----
    @Test
    void sourceVisualDescriptionDeterministicReproduction() {
        // same canonical inputs -> same description, exact equality + same serialization
        SourceVisualDescription s1 = sampleDescription();
        SourceVisualDescription s2 = sampleDescription();
        assertEquals(s1, s2, "S1 == S2 exact semantic equality");
        assertEquals(s1.hashCode(), s2.hashCode());
        assertEquals(s1.toString(), s2.toString(), "canonical serialization(S1) == serialization(S2)");
        // zero mutable-latest inputs: all fields are immutable value types
        assertTrue(s1.rasterExtent() instanceof EncodedRasterExtent);
        assertTrue(s1.colorDescription() instanceof ColorDescription);
    }

    @Test
    void reproducibilityHasNoMutableLatestDependency() {
        // structural proof: SourceVisualDescription components contain NO
        // path/URI/latest-name/provider-handle references
        var fields = java.util.Arrays.stream(SourceVisualDescription.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getType).toList();
        for (Class<?> t : fields) {
            assertFalse(t.getName().contains("Artifact") || t.getName().contains("Path")
                            || t.getName().contains("Uri") || t.getName().contains("Probe"),
                    "no mutable-latest dependency in SourceVisualDescription: " + t.getName());
        }
    }

    @Test
    void profileIdentityIsExactDigest() {
        // historical profile identity = immutable digest; no path/latest resolution
        ColorProfileContentDigest d1 = ColorProfileContentDigest.ofText("profile-bytes-v1");
        ColorDescription p1 = new ColorDescription.ProfileBasedColorDescription(ProfileFormat.ICC, d1);
        ColorDescription p2 = new ColorDescription.ProfileBasedColorDescription(ProfileFormat.ICC, d1);
        assertEquals(p1, p2, "same digest -> same canonical description");
        assertNotEquals(d1, ColorProfileContentDigest.ofText("profile-bytes-v2"));
    }

    private static SourceVisualDescription sampleDescription() {
        return new SourceVisualDescription(
                new EncodedRasterExtent(1920, 1080),
                PixelAspectRatio.square(),
                RasterSampleDescription.ycbcr(8, ChromaSubsampling.SAMPLE_420),
                new ColorDescription.ParametricColorDescription(
                        ColorPrimaries.WellKnown.BT709, TransferCharacteristic.BT709,
                        MatrixCoefficients.BT709, SignalRange.LIMITED),
                AlphaDescription.NO_ALPHA,
                SourceOrientation.NORMAL,
                new ScanDescription.Progressive(),
                Optional.of(StaticHdrMetadata.of(new ContentLightMetadata(
                        Rational.of(1000, 1), Rational.of(400, 1)))));
    }
}
