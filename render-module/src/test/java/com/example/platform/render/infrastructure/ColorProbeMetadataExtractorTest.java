package com.example.platform.render.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ColorProbeMetadataExtractorTest {

    @Test
    void detectsHdrFromTransfer() {
        ColorProbeMetadata meta = ColorProbeMetadataExtractor.fromStreamMetadata(
                Map.of("color_transfer", "smpte2084"), "yuv420p10le");
        assertTrue(meta.hdr());
        assertEquals("smpte2084", meta.colorTransfer());
        assertTrue(meta.toTimelineMetadata().containsKey("platform.color.hdr"));
    }
}
