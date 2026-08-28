package com.example.platform.render.infrastructure.libass;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.extension.domain.ToolExecutionSafetyPolicy;
import com.example.platform.render.domain.interchange.TimelineOutputSpec;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.interchange.TimelineTextOverlay;
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
TimelineTextOverlay.of("1", "Hello",
                new com.example.platform.fonttext.typography.FontFamilyName("DejaVu Sans"), 1.0, 3.0));
        TimelineSpec spec = new TimelineSpec(null, null, null, List.of(), overlays, null, 0, Map.of());

        var result = compositor.applyTextOverlays(
                tempDir.resolve("nonexistent.mp4"),
                tempDir.resolve("output.mp4"),
                spec);

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("Input video missing"));
    }

    @Test
    void overlaysFailClosedWithoutWritingAssOrOutput(@TempDir Path tempDir) throws Exception {
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
            public ToolExecutionResult execute(ToolExecutionRequest request, ToolExecutionSafetyPolicy policy) {
                return execute(request);
            }
        };

        var compositor = new LibassSubtitleCompositor(mockRunner);
        List<TimelineTextOverlay> overlays = List.of(
TimelineTextOverlay.of("1", "Hello",
                new com.example.platform.fonttext.typography.FontFamilyName("DejaVu Sans"), 1.0, 3.0));
        TimelineSpec spec = new TimelineSpec(null, null, null, List.of(), overlays, null, 0, Map.of());

        Path outputVideo = tempDir.resolve("output.mp4");
        var result = compositor.applyTextOverlays(inputVideo, outputVideo, spec);

        Path assPath = tempDir.resolve("burn-in.ass");
        assertFalse(result.success());
        assertEquals("TYPED_PROVIDER_PLUGIN_EXECUTION_REQUIRED", result.errorMessage());
        assertFalse(Files.exists(assPath), "Fail-closed path must not stage an ASS file");
        assertFalse(Files.exists(outputVideo), "Fail-closed path must not manufacture output");
        assertNull(capturedArgs.get(), "Fail-closed path must not invoke a process command");
    }

    @Test
    void removedCommandAuthorityInvokesNoProcessOrShell(@TempDir Path tempDir) throws Exception {
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
            public ToolExecutionResult execute(ToolExecutionRequest request, ToolExecutionSafetyPolicy policy) {
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
TimelineTextOverlay.of("1", "Test",
                new com.example.platform.fonttext.typography.FontFamilyName("DejaVu Sans"), 1.0, 2.0));
        TimelineSpec spec = new TimelineSpec(null, null, null, List.of(), overlays, null, 0, Map.of());

        Path output = tempDir.resolve("output.mp4");
        var result = compositor.applyTextOverlays(inputVideo, output, spec);

        assertFalse(result.success());
        assertEquals("TYPED_PROVIDER_PLUGIN_EXECUTION_REQUIRED", result.errorMessage());
        assertNull(capturedArgs.get(), "Removed FFmpeg authority must invoke no process command");
        assertFalse(Files.exists(output));
    }

    @Test
    void runnerIsNotReachedAndTypedProviderRequirementIsReturned(@TempDir Path tempDir) throws Exception {
        Path inputVideo = tempDir.resolve("input.mp4");
        Files.write(inputVideo, "fake video content".getBytes());

        var mockRunner = new ProcessToolRunner() {
            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request) {
                return ToolExecutionResult.failed(1, "", "FFmpeg error", Instant.now(), Instant.now());
            }

            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request, ToolExecutionSafetyPolicy policy) {
                return execute(request);
            }
        };

        var compositor = new LibassSubtitleCompositor(mockRunner);
        List<TimelineTextOverlay> overlays = List.of(
TimelineTextOverlay.of("1", "Test",
                new com.example.platform.fonttext.typography.FontFamilyName("DejaVu Sans"), 1.0, 2.0));
        TimelineSpec spec = new TimelineSpec(null, null, null, List.of(), overlays, null, 0, Map.of());

        var result = compositor.applyTextOverlays(inputVideo, tempDir.resolve("output.mp4"), spec);

        assertFalse(result.success());
        assertEquals("TYPED_PROVIDER_PLUGIN_EXECUTION_REQUIRED", result.errorMessage());
        assertFalse(Files.exists(tempDir.resolve("output.mp4")));
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
            public ToolExecutionResult execute(ToolExecutionRequest request, ToolExecutionSafetyPolicy policy) {
                return execute(request);
            }
        };

        var compositor = new LibassSubtitleCompositor(mockRunner);
        // Malicious text with ASS override injection
        List<TimelineTextOverlay> overlays = List.of(
TimelineTextOverlay.of("1", "{\\pos(0,0)}{\\fnEvilFont}Hello",
                new com.example.platform.fonttext.typography.FontFamilyName("DejaVu Sans"), 1.0, 3.0));
        TimelineSpec spec = new TimelineSpec(null, null, null, List.of(), overlays, null, 0, Map.of());

        var result = compositor.applyTextOverlays(inputVideo, tempDir.resolve("output.mp4"), spec);
        Path assPath = tempDir.resolve("burn-in.ass");
        assertFalse(result.success());
        assertEquals("TYPED_PROVIDER_PLUGIN_EXECUTION_REQUIRED", result.errorMessage());
        assertFalse(Files.exists(assPath), "Compositor must not stage an ASS file while fail-closed");

        // Sanitization remains a pure writer responsibility and is still preserved.
        new LibassAssFileWriter().write(assPath, overlays, 1920, 1080);
        String assContent = Files.readString(assPath);

        // Verify override tags are neutralized
        assertFalse(assContent.contains("{\\pos"), "Override tag should be neutralized");
        assertFalse(assContent.contains("{\\fn"), "Font override should be neutralized");
        assertTrue(assContent.contains("Hello"), "Text content should be preserved");
    }
}
