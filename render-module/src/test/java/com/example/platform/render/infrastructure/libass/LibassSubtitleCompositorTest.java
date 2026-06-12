package com.example.platform.render.infrastructure.libass;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.extension.domain.ToolSandboxPolicy;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTextOverlay;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibassSubtitleCompositorTest {

    @TempDir
    Path tempDir;

    @Test
    void noOverlaysReturnsSkipped() {
        var compositor = new LibassSubtitleCompositor(null);
        TimelineSpec spec = new TimelineSpec(null, null, null, List.of(), List.of(), null, 0, Map.of());

        var result = compositor.applyTextOverlays(
                tempDir.resolve("input.mp4"),
                tempDir.resolve("output.mp4"),
                spec);

        assertTrue(result.wasSkipped());
        assertTrue(result.success());
    }

    @Test
    void missingInputVideoReturnsFailed() {
        var compositor = new LibassSubtitleCompositor(null);
        List<TimelineTextOverlay> overlays = List.of(
                TimelineTextOverlay.of("1", "Hello", 1.0, 3.0));
        TimelineSpec spec = new TimelineSpec(null, null, null, List.of(), overlays, null, 0, Map.of());

        var result = compositor.applyTextOverlays(
                tempDir.resolve("nonexistent.mp4"),
                tempDir.resolve("output.mp4"),
                spec);

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("Input video missing"));
    }

    @Test
    void assFileWrittenToExpectedLocation(@TempDir Path tempDir) throws Exception {
        // Create a dummy input video file
        Path inputVideo = tempDir.resolve("input.mp4");
        Files.write(inputVideo, "fake video content".getBytes());

        // Create a mock ProcessToolRunner that records the command
        var capturedArgs = new java.util.concurrent.atomic.AtomicReference<List<String>>();
        var mockRunner = new ProcessToolRunner() {
            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request) {
                capturedArgs.set(request.args());
                return ToolExecutionResult.success(0, "", "", Instant.now(), Instant.now());
            }

            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request, ToolSandboxPolicy policy) {
                return execute(request);
            }
        };

        var compositor = new LibassSubtitleCompositor(mockRunner);
        List<TimelineTextOverlay> overlays = List.of(
                TimelineTextOverlay.of("1", "Hello", 1.0, 3.0));
        TimelineSpec spec = new TimelineSpec(null, null, null, List.of(), overlays, null, 0, Map.of());

        Path outputVideo = tempDir.resolve("output.mp4");
        compositor.applyTextOverlays(inputVideo, outputVideo, spec);

        // Verify ASS file was created
        Path assPath = tempDir.resolve("burn-in.ass");
        assertTrue(Files.exists(assPath), "ASS file should be created alongside output");

        // Verify ASS file contains sanitized text
        String assContent = Files.readString(assPath);
        assertTrue(assContent.contains("Hello"), "ASS file should contain the subtitle text");
        assertTrue(assContent.contains("[Script Info]"), "ASS file should have proper header");
    }

    @Test
    void commandUsesProcessBuilderNotShell(@TempDir Path tempDir) throws Exception {
        Path inputVideo = tempDir.resolve("input.mp4");
        Files.write(inputVideo, "fake video content".getBytes());

        var capturedArgs = new java.util.concurrent.atomic.AtomicReference<List<String>>();
        var mockRunner = new ProcessToolRunner() {
            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request) {
                capturedArgs.set(request.args());
                return ToolExecutionResult.success(0, "", "", Instant.now(), Instant.now());
            }

            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request, ToolSandboxPolicy policy) {
                return execute(request);
            }
        };

        var compositor = new LibassSubtitleCompositor(mockRunner);
        // Set ffmpegBinary via reflection since @Value doesn't work in unit tests
        try {
            var field = LibassSubtitleCompositor.class.getDeclaredField("ffmpegBinary");
            field.setAccessible(true);
            field.set(compositor, "ffmpeg");
        } catch (Exception e) {
            fail("Could not set ffmpegBinary: " + e.getMessage());
        }
        List<TimelineTextOverlay> overlays = List.of(
                TimelineTextOverlay.of("1", "Test", 1.0, 2.0));
        TimelineSpec spec = new TimelineSpec(null, null, null, List.of(), overlays, null, 0, Map.of());

        compositor.applyTextOverlays(inputVideo, tempDir.resolve("output.mp4"), spec);

        // Verify command is a list (not shell string)
        assertNotNull(capturedArgs.get(), "Command should be captured");
        List<String> args = capturedArgs.get();
        assertTrue(args.size() >= 2, "Command should have multiple args");
        assertEquals("ffmpeg", args.get(0), "First arg should be ffmpeg binary");

        // Verify no shell invocation
        assertFalse(args.contains("sh"), "Should not invoke shell");
        assertFalse(args.contains("bash"), "Should not invoke bash");
        assertFalse(args.contains("-c"), "Should not use shell -c");

        // Verify subtitles filter uses ass= with a path
        String vfArg = args.stream().filter(a -> a.startsWith("ass=")).findFirst().orElse(null);
        assertNotNull(vfArg, "Should have ass= filter");
    }

    @Test
    void nonZeroExitReturnsFailed(@TempDir Path tempDir) throws Exception {
        Path inputVideo = tempDir.resolve("input.mp4");
        Files.write(inputVideo, "fake video content".getBytes());

        var mockRunner = new ProcessToolRunner() {
            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request) {
                return ToolExecutionResult.failed(1, "", "FFmpeg error", Instant.now(), Instant.now());
            }

            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request, ToolSandboxPolicy policy) {
                return execute(request);
            }
        };

        var compositor = new LibassSubtitleCompositor(mockRunner);
        List<TimelineTextOverlay> overlays = List.of(
                TimelineTextOverlay.of("1", "Test", 1.0, 2.0));
        TimelineSpec spec = new TimelineSpec(null, null, null, List.of(), overlays, null, 0, Map.of());

        var result = compositor.applyTextOverlays(inputVideo, tempDir.resolve("output.mp4"), spec);

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("libass burn-in failed"));
    }

    @Test
    void maliciousTextSanitizedInAssFile(@TempDir Path tempDir) throws Exception {
        Path inputVideo = tempDir.resolve("input.mp4");
        Files.write(inputVideo, "fake video content".getBytes());

        var mockRunner = new ProcessToolRunner() {
            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request) {
                return ToolExecutionResult.success(0, "", "", Instant.now(), Instant.now());
            }

            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request, ToolSandboxPolicy policy) {
                return execute(request);
            }
        };

        var compositor = new LibassSubtitleCompositor(mockRunner);
        // Malicious text with ASS override injection
        List<TimelineTextOverlay> overlays = List.of(
                TimelineTextOverlay.of("1", "{\\pos(0,0)}{\\fnEvilFont}Hello", 1.0, 3.0));
        TimelineSpec spec = new TimelineSpec(null, null, null, List.of(), overlays, null, 0, Map.of());

        compositor.applyTextOverlays(inputVideo, tempDir.resolve("output.mp4"), spec);

        Path assPath = tempDir.resolve("burn-in.ass");
        String assContent = Files.readString(assPath);

        // Verify override tags are neutralized
        assertFalse(assContent.contains("{\\pos"), "Override tag should be neutralized");
        assertFalse(assContent.contains("{\\fn"), "Font override should be neutralized");
        assertTrue(assContent.contains("Hello"), "Text content should be preserved");
    }
}
