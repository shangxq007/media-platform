package com.example.platform.render.infrastructure.mlt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class MltSegmentConcatXmlTest {

    @Test
    void buildSegmentConcatContainsProducersAndPlaylist() {
        MltProjectXmlBuilder builder = new MltProjectXmlBuilder();
        String xml = builder.buildSegmentConcat(
                List.of(
                        new MltProjectXmlBuilder.SegmentMediaEntry("seg_0", "file:///tmp/seg0.mp4"),
                        new MltProjectXmlBuilder.SegmentMediaEntry("seg_1", "file:///tmp/seg1.mp4")),
                1920,
                1080,
                30.0);
        assertTrue(xml.contains("producer id=\"seg_0\""));
        assertTrue(xml.contains("producer id=\"seg_1\""));
        assertTrue(xml.contains("playlist id=\"segment_concat\""));
        assertTrue(xml.contains("<entry producer=\"seg_0\""));
    }
}
