package com.example.platform.shared.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.time.MediaTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MediaFrameExtractParametersV1Test {

    private static MediaFrameExtractParametersV1 sample() {
        return new MediaFrameExtractParametersV1(
                MediaTime.ofRational(3, 2),
                RasterImageEncoding.Jpeg.documentedDefault(),
                FrameWidth.Native.NATIVE);
    }

    @Test
    void constructsWithTypedValuesOnly() {
        MediaFrameExtractParametersV1 parameters = sample();
        assertEquals(MediaTime.ofRational(3, 2), parameters.position());
        assertInstanceOf(RasterImageEncoding.Jpeg.class, parameters.encoding());
        assertInstanceOf(FrameWidth.Native.class, parameters.width());
    }

    @Test
    void rejectsNullComponents() {
        assertThrows(NullPointerException.class,
                () -> new MediaFrameExtractParametersV1(null, RasterImageEncoding.Png.PNG, FrameWidth.Native.NATIVE));
        assertThrows(NullPointerException.class,
                () -> new MediaFrameExtractParametersV1(MediaTime.ZERO, null, FrameWidth.Native.NATIVE));
        assertThrows(NullPointerException.class,
                () -> new MediaFrameExtractParametersV1(MediaTime.ZERO, RasterImageEncoding.Png.PNG, null));
    }

    @Test
    void explicitWidthEnforcesCapabilityFloor() {
        assertEquals(16, FrameWidth.ExplicitPixels.MINIMUM_PIXELS);
        assertEquals(16, new FrameWidth.ExplicitPixels(16).pixels());
        assertThrows(IllegalArgumentException.class, () -> new FrameWidth.ExplicitPixels(15));
        assertThrows(IllegalArgumentException.class, () -> new FrameWidth.ExplicitPixels(0));
    }

    @Test
    void jpegQualityIsBoundedAndAbsenceIsExplicit() {
        assertEquals(50, new JpegQuality(50).value());
        assertThrows(IllegalArgumentException.class, () -> new JpegQuality(0));
        assertThrows(IllegalArgumentException.class, () -> new JpegQuality(101));

        RasterImageEncoding.Jpeg explicit = new RasterImageEncoding.Jpeg(new JpegQuality(80));
        assertTrue(explicit.hasExplicitQuality());
        assertEquals(80, explicit.quality().orElseThrow().value());

        RasterImageEncoding.Jpeg absent = RasterImageEncoding.Jpeg.documentedDefault();
        assertFalse(absent.hasExplicitQuality());
        assertTrue(absent.quality().isEmpty());
        assertThrows(NullPointerException.class, () -> new RasterImageEncoding.Jpeg((JpegQuality) null));
    }

    @Test
    void pngHasNoQualityParameter() {
        RasterImageEncoding png = RasterImageEncoding.Png.PNG;
        assertInstanceOf(RasterImageEncoding.Png.class, png);
        // Png carries no components at all (no quality surface).
        assertEquals(0, RasterImageEncoding.Png.class.getRecordComponents().length);
    }

    @Test
    void variantFieldTypesAreTypedAndProviderNeutral() {
        for (var component : MediaFrameExtractParametersV1.class.getRecordComponents()) {
            Class<?> type = component.getType();
            assertFalse(Map.class.isAssignableFrom(type), "no Map field: " + component.getName());
            assertFalse(type.getSimpleName().contains("JsonNode"), "no JsonNode field: " + component.getName());
            assertFalse(type.equals(Object.class), "no Object field: " + component.getName());
        }
        assertTrue(CapabilityParameterContract.class
                .isAssignableFrom(MediaFrameExtractParametersV1.class));
    }

    @Test
    void ofExactSecondsDecodesToAnExactRationalWithoutFloatingAuthority() {
        MediaFrameExtractParametersV1 parameters = MediaFrameExtractParametersV1.ofExactSeconds(
                "1.25", RasterImageEncoding.Png.PNG, FrameWidth.Native.NATIVE);
        assertEquals(MediaTime.ofRational(5, 4), parameters.position());

        // "0.1" must be exactly 1/10, never the binary-double approximation.
        assertEquals(MediaTime.ofRational(1, 10),
                MediaFrameExtractParametersV1.ofExactSeconds(
                        "0.1", RasterImageEncoding.Png.PNG, FrameWidth.Native.NATIVE).position());

        assertEquals(MediaTime.ZERO,
                MediaFrameExtractParametersV1.ofExactSeconds(
                        "0", RasterImageEncoding.Png.PNG, FrameWidth.Native.NATIVE).position());
    }

    @Test
    void ofExactSecondsFailsClosedOnInvalidTransportValues() {
        assertThrows(IllegalArgumentException.class, () -> MediaFrameExtractParametersV1.ofExactSeconds(
                "not-a-number", RasterImageEncoding.Png.PNG, FrameWidth.Native.NATIVE));
        assertThrows(IllegalArgumentException.class, () -> MediaFrameExtractParametersV1.ofExactSeconds(
                "-1", RasterImageEncoding.Png.PNG, FrameWidth.Native.NATIVE));
        assertThrows(IllegalArgumentException.class, () -> MediaFrameExtractParametersV1.ofExactSeconds(
                "1e3", RasterImageEncoding.Png.PNG, FrameWidth.Native.NATIVE));
        assertThrows(NullPointerException.class, () -> MediaFrameExtractParametersV1.ofExactSeconds(
                null, RasterImageEncoding.Png.PNG, FrameWidth.Native.NATIVE));
    }
}
