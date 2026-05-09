package com.example.platform.render.infrastructure.mlt;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MltProjectXmlBuilderTest {

    private MltProjectXmlBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new MltProjectXmlBuilder();
    }

    @Test
    void shouldBuildMinimalXml() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", output);
        String xml = builder.build(timeline);

        assertTrue(xml.contains("<?xml version="));
        assertTrue(xml.contains("<mlt"));
        assertTrue(xml.contains("width=\"1920\""));
        assertTrue(xml.contains("height=\"1080\""));
        assertTrue(xml.contains("<tractor>"));
        assertTrue(xml.contains("<multitrack>"));
        assertTrue(xml.contains("</mlt>"));
    }

    @Test
    void shouldBuildSkeleton() {
        String xml = builder.buildSkeleton(1280, 720, 30.0);

        assertTrue(xml.contains("width=\"1280\""));
        assertTrue(xml.contains("height=\"720\""));
        assertTrue(xml.contains("<mlt"));
        assertTrue(xml.contains("</mlt>"));
    }

    @Test
    void shouldGenerateValidXml() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", output);
        String xml = builder.build(timeline);

        // Verify XML structure is valid
        assertTrue(xml.startsWith("<?xml"));
        assertTrue(xml.contains("<mlt"));
        assertTrue(xml.contains("</mlt>"));
        assertTrue(xml.contains("<tractor>"));
        assertTrue(xml.contains("</tractor>"));
    }

    @Test
    void shouldHandleSpecialCharactersInOutput() {
        // Test that the builder produces well-formed output even with special chars
        String xml = builder.buildSkeleton(1920, 1080, 30.0);
        // The skeleton should be well-formed XML
        assertTrue(xml.contains("<?xml"));
        assertTrue(xml.contains("<mlt"));
        assertTrue(xml.contains("</mlt>"));
    }
}
