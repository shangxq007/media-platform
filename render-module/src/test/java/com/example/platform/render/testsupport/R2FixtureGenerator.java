package com.example.platform.render.testsupport;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Utilities for generating deterministic test media and subtitle fixtures
 * using real FFmpeg. Used by Timeline Core Testable R2 real baseline render smoke.
 *
 * <p>All generated assets are tiny, deterministic, and suitable for CI.
 * No external network access or production secrets required.</p>
 */
public final class R2FixtureGenerator {

    private R2FixtureGenerator() {}

    /**
     * Checks if FFmpeg is available on PATH.
     */
    public static boolean isFfmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Assumes FFmpeg is available; skips test with explicit message if not.
     */
    public static void assumeFfmpegAvailable() {
        Assumptions.assumeTrue(isFfmpegAvailable(),
                "FFmpeg not available; real baseline render smoke skipped.");
    }

    /**
     * Generates a tiny test video using FFmpeg testsrc filter.
     *
     * @param outputDir directory to write the video into
     * @param durationSec duration in seconds (1-3 recommended)
     * @param width width in pixels
     * @param height height in pixels
     * @param fps frame rate
     * @return path to the generated video file
     */
    public static Path generateTestVideo(Path outputDir, double durationSec,
                                          int width, int height, int fps) throws IOException {
        Files.createDirectories(outputDir);
        Path output = outputDir.resolve("test-input-" + UUID.randomUUID().toString().substring(0, 8) + ".mp4");

        List<String> cmd = List.of(
                "ffmpeg", "-y",
                "-f", "lavfi", "-i",
                "testsrc=duration=" + durationSec + ":size=" + width + "x" + height + ":rate=" + fps,
                "-f", "lavfi", "-i",
                "sine=frequency=440:duration=" + durationSec + ":sample_rate=48000",
                "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "64k",
                "-shortest",
                output.toString()
        );

        ProcessResult result = executeCommand(cmd);
        if (!result.success) {
            throw new IOException("FFmpeg test video generation failed: " + result.stderr);
        }

        if (!Files.exists(output) || Files.size(output) == 0) {
            throw new IOException("Generated test video is missing or zero-byte: " + output);
        }

        return output;
    }

    /**
     * Generates a minimal SRT subtitle file.
     *
     * @param outputDir directory to write the SRT into
     * @return path to the generated SRT file
     */
    public static Path generateTestSubtitle(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path output = outputDir.resolve("test-subtitle.srt");

        String srt = """
                1
                00:00:00,500 --> 00:00:02,500
                Hello from Timeline R2

                2
                00:00:03,000 --> 00:00:04,500
                FFmpeg/libass baseline
                """;

        Files.writeString(output, srt);
        return output;
    }

    /**
     * Generates a minimal ASS subtitle file for libass burn-in.
     *
     * @param outputDir directory to write the ASS into
     * @param width canvas width for ASS header
     * @param height canvas height for ASS header
     * @return path to the generated ASS file
     */
    public static Path generateTestAssSubtitle(Path outputDir, int width, int height) throws IOException {
        Files.createDirectories(outputDir);
        Path output = outputDir.resolve("test-subtitle.ass");

        String ass = """
                [Script Info]
                Title: R2 Smoke Test
                ScriptType: v4.00+
                PlayResX: %d
                PlayResY: %d

                [V4+ Styles]
                Format: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,Alignment,MarginL,MarginR,MarginV,Encoding
                Style: Default,DejaVu Sans,24,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2,0,2,10,10,30,1

                [Events]
                Format: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text
                Dialogue: 0,0:00:00.50,0:00:02.50,Default,,0,0,0,,Hello from Timeline R2
                Dialogue: 0,0:00:03.00,0:00:04.50,Default,,0,0,0,,FFmpeg/libass baseline
                """.formatted(width, height);

        Files.writeString(output, ass);
        return output;
    }

    /**
     * Executes a command and returns the result.
     */
    public static ProcessResult executeCommand(List<String> cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process p = pb.start();
            String stdout = new String(p.getInputStream().readAllBytes());
            String stderr = new String(p.getErrorStream().readAllBytes());
            boolean done = p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                return new ProcessResult(false, stdout, "timeout");
            }
            return new ProcessResult(p.exitValue() == 0, stdout, stderr);
        } catch (Exception e) {
            return new ProcessResult(false, "", e.getMessage());
        }
    }

    /**
     * Result of executing a process.
     */
    public record ProcessResult(boolean success, String stdout, String stderr) {}
}
