package com.example.platform.render.infrastructure.ffmpeg;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.RenderProfile;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FfmpegCommandFactoryTest {

    private FfmpegCommandFactory factory;

    @BeforeEach
    void setUp() {
        factory = new FfmpegCommandFactory();
    }

    @Test
    void shouldBuildProbeCommand() {
        List<String> args = factory.buildProbeCommand("storage://input.mp4");

        assertTrue(args.contains("-i"));
        assertTrue(args.contains("storage://input.mp4"));
        assertTrue(args.contains("-print_format"));
        assertTrue(args.contains("json"));
        assertTrue(args.contains("-show_format"));
        assertTrue(args.contains("-show_streams"));
    }

    @Test
    void shouldBuildThumbnailCommand() {
        List<String> args = factory.buildThumbnailCommand(
                "storage://input.mp4", "storage://thumb.jpg", 5.0, 320);

        assertTrue(args.contains("-ss"));
        assertTrue(args.contains("5.0"));
        assertTrue(args.contains("-i"));
        assertTrue(args.contains("storage://input.mp4"));
        assertTrue(args.contains("-frames:v"));
        assertTrue(args.contains("1"));
        assertTrue(args.contains("-vf"));
        assertTrue(args.contains("scale=320:-1"));
        assertTrue(args.contains("storage://thumb.jpg"));
    }

    @Test
    void shouldBuildTranscodeCommandFromProfile() {
        RenderProfile profile = RenderProfile.social1080p();
        List<String> args = factory.buildTranscodeCommand(
                "storage://input.mp4", "storage://output.mp4", profile);

        assertTrue(args.contains("-i"));
        assertTrue(args.contains("storage://input.mp4"));
        assertTrue(args.contains("-c:v"));
        assertTrue(args.contains("libx264"));
        assertTrue(args.contains("-b:v"));
        assertTrue(args.contains("8000k"));
        assertTrue(args.contains("-s"));
        assertTrue(args.contains("1920x1080"));
        assertTrue(args.contains("-c:a"));
        assertTrue(args.contains("aac"));
        assertTrue(args.contains("-y"));
        assertTrue(args.contains("storage://output.mp4"));
    }

    @Test
    void shouldBuildTranscodeCommandWithH265() {
        RenderProfile profile = RenderProfile.of("custom", "1920x1080", "h265");
        List<String> args = factory.buildTranscodeCommand(
                "storage://input.mp4", "storage://output.mp4", profile);

        assertTrue(args.contains("-c:v"));
        assertTrue(args.contains("libx265"));
    }

    @Test
    void shouldBuildFaststartCommand() {
        List<String> args = factory.buildFaststartCommand(
                "storage://input.mp4", "storage://output.mp4");

        assertTrue(args.contains("-i"));
        assertTrue(args.contains("storage://input.mp4"));
        assertTrue(args.contains("-c"));
        assertTrue(args.contains("copy"));
        assertTrue(args.contains("-movflags"));
        assertTrue(args.contains("+faststart"));
        assertTrue(args.contains("storage://output.mp4"));
    }

    @Test
    void shouldBuildTranscodeFromTimeline() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", output);
        List<String> args = factory.buildTranscodeFromTimeline(timeline, "storage://output.mp4");

        assertTrue(args.contains("-c:v"));
        assertTrue(args.contains("libx264"));
        assertTrue(args.contains("-s"));
        assertTrue(args.contains("1920x1080"));
        assertTrue(args.contains("-pix_fmt"));
        assertTrue(args.contains("yuv420p"));
        assertTrue(args.contains("storage://output.mp4"));
    }

    @Test
    void shouldMapCodecNames() {
        assertEquals("libx264", factory.buildTranscodeCommand(
                "i", "o", RenderProfile.of("p", "1920x1080", "h264")).stream()
                .dropWhile(a -> !a.equals("-c:v")).skip(1).findFirst().orElse(""));
    }

    @Test
    void shouldNotUseShellConcatenation() {
        // Verify that all args are individual strings, not shell-concatenated
        RenderProfile profile = RenderProfile.social1080p();
        List<String> args = factory.buildTranscodeCommand(
                "storage://input.mp4", "storage://output.mp4", profile);

        for (String arg : args) {
            assertFalse(arg.contains(" "),
                    "Argument should not contain spaces (shell concatenation): " + arg);
            assertFalse(arg.contains(";"),
                    "Argument should not contain semicolons: " + arg);
            assertFalse(arg.contains("|"),
                    "Argument should not contain pipe: " + arg);
            assertFalse(arg.contains("&&"),
                    "Argument should not contain &&: " + arg);
        }
    }
}
