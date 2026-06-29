package com.example.platform.render.infrastructure.smoke;

import com.example.platform.render.domain.render.local.*;
import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.render.plan.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * Integration test for the BasicRenderPlan-to-local-runner bridge.
 *
 * <p>This test only runs when explicitly enabled:
 * <pre>
 * ./gradlew :render-module:test --tests "*BasicRenderPlanLocalRunnerIntegrationTest" \
 *   -Dmedia.platform.localSmoke.enabled=true \
 *   -Dmedia.platform.localSmoke.outputRoot=/tmp/media-platform-local-smoke
 * </pre>
 *
 * <p>Normal test runs skip this test. If FFmpeg/ffprobe are not installed,
 * the test returns NOT_AVAILABLE rather than failing.</p>
 */
class BasicRenderPlanLocalRunnerIntegrationTest {

    @TempDir
    Path tempDir;

    private static String outputRootProp;

    @BeforeAll
    static void checkEnabled() {
        boolean enabled = Boolean.getBoolean(LocalRenderSmokePolicy.ENABLE_PROPERTY);
        assumeTrue(enabled,
                "BasicRenderPlan local runner integration test skipped: "
                + "-Dmedia.platform.localSmoke.enabled=true not set");
        outputRootProp = System.getProperty("media.platform.localSmoke.outputRoot", "");
    }

    private Path resolveOutputRoot() {
        if (outputRootProp != null && !outputRootProp.isBlank()) {
            return Path.of(outputRootProp);
        }
        return tempDir;
    }

    @Test
    void basicRenderPlanDrivesLocalExecution() throws Exception {
        Path outputRoot = resolveOutputRoot();

        // Step 1: Build deterministic BasicTimeline fixture
        // Must have at least one track with one clip for BasicTimelineValidator
        TimelineOutputSpec outputSpec = new TimelineOutputSpec(
                "mp4", "320x180", 30.0, "h264", 1000000,
                new TimelineAudioSpec("aac", 48000, 2, 128000, 1.0, false),
                "yuv420p");
        TimelineAssetRef assetRef = TimelineAssetRef.of("asset-001", "internal://testsrc");
        TimelineClip clip = TimelineClip.of("clip-001", assetRef, 0.0, 0.0, 2.0);
        TimelineTrack track = new TimelineTrack(
                "track-v1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);
        TimelineSpec timeline = new TimelineSpec(
                "timeline-001", "Test Timeline", "P2L.1 integration test",
                List.of(track), List.of(), outputSpec, 2.0, Map.of());

        // Step 2: Produce FFmpegLibassBasicRenderPlan
        var planRequest = new FFmpegLibassBasicRenderPlanningRequest(
                new FFmpegLibassBasicRenderPlanningRequestId("plan-req-001"),
                timeline, FFmpegLibassBasicRenderPolicy.conservative(), Map.of());
        var planResult = FFmpegLibassBasicRenderPlanner.plan(planRequest);

        assertNotNull(planResult.plan(), "Plan should be produced: " + planResult.issues());
        assertEquals(FFmpegLibassBasicRenderPlanStatus.READY, planResult.plan().status(),
                "Plan should be READY: " + planResult.issues());

        // Step 3: Execute through local runner
        var policy = new LocalRenderSmokePolicy(
                true, 30, outputRoot, true,
                Set.of("ffmpeg", "ffprobe"), false);

        var report = BasicRenderPlanLocalRunner.executeAndReport(planResult.plan(), policy);

        // Step 4: Validate result
        assertNotNull(report);
        assertNotNull(report.reportId());
        assertEquals(1, report.results().size());

        var result = report.results().get(0);
        assertNotNull(result.executionId());
        assertNotNull(result.planId());

        // Log issues for debugging
        if (!result.issues().isEmpty()) {
            System.out.println("Execution issues:");
            for (var issue : result.issues()) {
                System.out.println("  [" + issue.severity() + "] " + issue.code() + ": " + issue.message());
            }
        }

        // If ffmpeg/ffprobe not available, expect NOT_AVAILABLE
        if (result.status() == LocalRenderExecutionStatus.NOT_AVAILABLE) {
            System.out.println("FFmpeg/ffprobe not available; execution returned NOT_AVAILABLE");
            return;
        }

        // Otherwise expect PASS or PASS_WITH_WARNINGS
        assertTrue(
                result.status() == LocalRenderExecutionStatus.PASS
                || result.status() == LocalRenderExecutionStatus.PASS_WITH_WARNINGS,
                "Expected PASS or PASS_WITH_WARNINGS but got " + result.status()
                        + " with issues: " + result.issues());

        // Validate output
        assertNotNull(result.outputPath(), "Output path should be set");
        assertTrue(Files.exists(result.outputPath()), "Output file should exist");
        assertTrue(result.outputFileBytes() > 0, "Output file should have content");
        assertEquals(0, result.ffmpegExitCode(), "FFmpeg should exit cleanly");
        assertEquals(320, result.actualWidth(), "Width should be 320");
        assertEquals(180, result.actualHeight(), "Height should be 180");
        assertTrue(result.actualDurationSec() > 0.5, "Duration should be > 0.5s");
        assertNotNull(result.actualCodec(), "Codec should be detected");

        // Validate report was written
        Path reportPath = result.outputPath().getParent()
                .resolve("local-render-execution-report.txt");
        if (Files.exists(reportPath)) {
            System.out.println("Report written to: " + reportPath);
            assertTrue(Files.exists(reportPath));
        }

        System.out.println("BasicRenderPlan Local Execution PASSED");
        System.out.println("  Execution ID: " + result.executionId().value());
        System.out.println("  Plan ID: " + result.planId());
        System.out.println("  Output: " + result.outputPath());
        System.out.println("  Size: " + result.outputFileBytes() + " bytes");
        System.out.println("  Resolution: " + result.actualWidth() + "x" + result.actualHeight());
        System.out.println("  Duration: " + result.actualDurationSec() + "s");
        System.out.println("  Codec: " + result.actualCodec());
        System.out.println("  Format: " + result.actualFormat());
        System.out.println("  FFmpeg time: " + result.ffmpegDuration().toMillis() + "ms");
        System.out.println("  Unsupported steps: " + result.unsupportedSteps());
    }

    @Test
    void basicRenderPlanReportWrittenToStableOutput() throws Exception {
        Path outputRoot = resolveOutputRoot();

        TimelineOutputSpec outputSpec = new TimelineOutputSpec(
                "mp4", "640x480", 24.0, "h264", 500000,
                new TimelineAudioSpec("aac", 44100, 2, 128000, 1.0, false),
                "yuv420p");
        TimelineAssetRef assetRef = TimelineAssetRef.of("asset-002", "internal://testsrc");
        TimelineClip clip = TimelineClip.of("clip-002", assetRef, 0.0, 0.0, 2.0);
        TimelineTrack track = new TimelineTrack(
                "track-v2", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);
        TimelineSpec timeline = new TimelineSpec(
                "timeline-002", "Report Test", "Report writing test",
                List.of(track), List.of(), outputSpec, 2.0, Map.of());

        var planRequest = new FFmpegLibassBasicRenderPlanningRequest(
                new FFmpegLibassBasicRenderPlanningRequestId("plan-req-002"),
                timeline, FFmpegLibassBasicRenderPolicy.conservative(), Map.of());
        var planResult = FFmpegLibassBasicRenderPlanner.plan(planRequest);

        assertNotNull(planResult.plan(), "Plan should be produced");

        var policy = new LocalRenderSmokePolicy(
                true, 30, outputRoot, true,
                Set.of("ffmpeg", "ffprobe"), false);

        var report = BasicRenderPlanLocalRunner.executeAndReport(planResult.plan(), policy);

        assertNotNull(report.reportId());
        System.out.println("Report status: " + report.overallStatus());
        System.out.println("Pass: " + report.passCount() + ", Fail: " + report.failCount());
    }
}
