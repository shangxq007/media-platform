package com.example.platform.render.infrastructure.gpac;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Mp4BoxCommandFactoryTest {

    private Mp4BoxCommandFactory factory;

    @BeforeEach
    void setUp() {
        factory = new Mp4BoxCommandFactory();
    }

    @Test
    void shouldBuildDashCommand() {
        List<String> args = factory.buildDashCommand(
                "storage://input.mp4", "storage://output/manifest.mpd", 4000);

        assertTrue(args.contains("-dash"));
        assertTrue(args.contains("4000"));
        assertTrue(args.contains("-out"));
        assertTrue(args.contains("storage://output/manifest.mpd"));
        assertTrue(args.contains("-segment-name"));
        assertTrue(args.contains("storage://input.mp4"));
    }

    @Test
    void shouldBuildHlsCommand() {
        List<String> args = factory.buildHlsCommand(
                "storage://input.mp4", "storage://output/master.m3u8", 6000);

        assertTrue(args.contains("-hls"));
        assertTrue(args.contains("6000"));
        assertTrue(args.contains("-out"));
        assertTrue(args.contains("storage://output/master.m3u8"));
        assertTrue(args.contains("storage://input.mp4"));
    }

    @Test
    void shouldBuildCmafCommand() {
        List<String> args = factory.buildCmafCommand(
                "storage://input.mp4", "storage://output", 4000);

        assertTrue(args.contains("-dash"));
        assertTrue(args.contains("4000"));
        assertTrue(args.contains("-segment-name"));
        assertTrue(args.contains("-out"));
        assertTrue(args.contains("storage://output/manifest.mpd"));
    }

    @Test
    void shouldBuildInspectCommand() {
        List<String> args = factory.buildInspectCommand("storage://input.mp4");

        assertTrue(args.contains("-info"));
        assertTrue(args.contains("-std"));
        assertTrue(args.contains("storage://input.mp4"));
    }

    @Test
    void shouldNotUseShellConcatenation() {
        List<String> args = factory.buildDashCommand(
                "storage://input.mp4", "storage://output/manifest.mpd", 4000);

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
