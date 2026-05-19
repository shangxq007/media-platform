package com.example.platform.render.infrastructure.mlt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MeltCommandFactoryTest {

    private MLTCommandFactory factory;

    @BeforeEach
    void setUp() {
        factory = new MLTCommandFactory();
    }

    @Test
    void shouldBuildRenderCommand() {
        List<String> args = factory.buildRenderCommand(
                "/tmp/project.xml", "storage://output.mp4", "atsc_1080p_30");

        assertTrue(args.contains("/tmp/project.xml"));
        assertTrue(args.contains("-consumer"));
        assertTrue(args.contains("avformat:storage://output.mp4"));
        assertTrue(args.contains("profile=atsc_1080p_30"));
    }

    @Test
    void shouldBuildRenderCommandWithSettings() {
        List<String> args = factory.buildRenderCommand(
                "/tmp/project.xml", "storage://output.mp4",
                1920, 1080, 30.0, "libx264", "aac");

        assertTrue(args.contains("width=1920"));
        assertTrue(args.contains("height=1080"));
        assertTrue(args.contains("frame_rate_num=30"));
        assertTrue(args.contains("vcodec=libx264"));
        assertTrue(args.contains("acodec=aac"));
    }

    @Test
    void shouldBuildPreviewCommand() {
        List<String> args = factory.buildPreviewCommand(
                "/tmp/project.xml", "storage://preview.mp4");

        assertTrue(args.contains("/tmp/project.xml"));
        assertTrue(args.contains("-consumer"));
        assertTrue(args.contains("avformat:storage://preview.mp4"));
        assertTrue(args.contains("width=854"));
        assertTrue(args.contains("height=480"));
        assertTrue(args.contains("preset=ultrafast"));
    }

    @Test
    void shouldNotUseShellConcatenation() {
        List<String> args = factory.buildRenderCommand(
                "/tmp/project.xml", "storage://output.mp4", "atsc_1080p_30");

        for (String arg : args) {
            assertFalse(arg.contains(";"),
                    "Argument should not contain semicolons: " + arg);
            assertFalse(arg.contains("|"),
                    "Argument should not contain pipe: " + arg);
            assertFalse(arg.contains("&&"),
                    "Argument should not contain &&: " + arg);
        }
    }
}
