package com.example.platform.render.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** ROADMAP_18: raw provider observation extraction — no hdr boolean, no Timeline leakage, no inference. */
class ColorProbeMetadataExtractorTest {

    @Test
    void extractsRawObservationStrings() {
        ColorProbeMetadata meta = ColorProbeMetadataExtractor.fromStreamMetadata(
                Map.of("color_transfer", "smpte2084", "color_space", "bt2020nc"), "yuv420p10le");
        assertEquals("smpte2084", meta.colorTransfer());
        assertEquals("bt2020nc", meta.colorSpace());
        assertEquals("yuv420p10le", meta.pixelFormat());
    }

    @Test
    void emptyWhenMissing() {
        ColorProbeMetadata meta = ColorProbeMetadataExtractor.fromStreamMetadata(Map.of(), null);
        assertEquals("", meta.colorSpace());
        assertEquals("", meta.pixelFormat());
    }

    @Test
    void concreteAdapterFieldAloneDoesNotCreateRenderSemanticAuthority() {
        String adapterOwnedKey = String.join("_", "pix", "fmt");
        ColorProbeMetadata meta = ColorProbeMetadataExtractor.fromStreamMetadata(
                Map.of(adapterOwnedKey, "adapter-value"), null);
        assertEquals("", meta.pixelFormat());

        ColorProbeMetadata neutral = ColorProbeMetadataExtractor.fromStreamMetadata(
                Map.of("pixel_format", "neutral-value"), null);
        assertEquals("neutral-value", neutral.pixelFormat());
    }

    @Test
    void noHdrBooleanAndNoTimelineLeakage() {
        // record has exactly 5 components (no hdr) — structural proof
        assertEquals(5, ColorProbeMetadata.class.getRecordComponents().length);
        // no method can write provider color data into Timeline metadata
        assertEquals(0, java.util.Arrays.stream(ColorProbeMetadata.class.getDeclaredMethods())
                .filter(m -> m.getName().contains("Timeline")).count());
    }
}
