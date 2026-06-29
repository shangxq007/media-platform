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
 * Integration test for real media source local runner.
 *
 * <p>This test only runs when explicitly enabled:
 * <pre>
 * ./gradlew :render-module:test --tests "*RealMediaSourceLocalRunnerIntegrationTest" \
 *   -Dmedia.platform.localSmoke.enabled=true \
 *   -Dmedia.platform.localSmoke.outputRoot=/tmp/media-platform-local-smoke
 * </pre>
 *
 * <p>Normal test runs skip this test. If FFmpeg/ffprobe are not installed,
 * the test returns NOT_AVAILABLE rather than failing.</p>
 */
class RealMediaSourceLocalRunnerIntegrationTest {

    @TempDir
    Path tempDir;

    private static boolean enabled;
    private static String outputRootProp;

    @BeforeAll
    static void checkEnabled() {
        enabled = Boolean.getBoolean(LocalRenderSmokePolicy.ENABLE_PROPERTY);
        outputRootProp = System.getProperty("media.platform.localSmoke.outputRoot", "");
    }

    private Path resolveOutputRoot() {
        if (outputRootProp != null && !outputRootProp.isBlank()) {
            return Path.of(outputRootProp);
        }
        return tempDir;
    }

    @Test
    void realMediaSourceSmokeGeneratesInputAndOutput() throws Exception {
        assumeTrue(enabled, "Real media source test skipped: -Dmedia.platform.localSmoke.enabled=true not set");

        Path outputRoot = resolveOutputRoot();
        var policy = new LocalRenderSmokePolicy(
                true, 60, outputRoot, true,
                Set.of("ffmpeg", "ffprobe"), false);

        // Check FFmpeg availability
        assumeTrue(isBinaryAvailable("ffmpeg"), "FFmpeg not available");
        assumeTrue(isBinaryAvailable("ffprobe"), "ffprobe not available");

        // Step 1: Generate deterministic input fixture
        var fixtureConfig = LocalMediaSourceFixtureGenerator.FixtureConfig.defaults(outputRoot);
        var fixtureResult = LocalMediaSourceFixtureGenerator.generate(fixtureConfig, policy);

        assertFalse(fixtureResult.blocked(), "Fixture generation should not be blocked: " + fixtureResult.issues());
        assertNotNull(fixtureResult.spec(), "Fixture spec should be generated");
        assertTrue(Files.exists(fixtureResult.spec().path()), "Input fixture should exist");

        // Step 2: Validate input fixture with ffprobe
        var inputValidation = LocalFfprobeValidator.validate(
                fixtureResult.spec().path(), 320, 180, 0.5, 10.0, 30);

        assertTrue(inputValidation.valid(), "Input fixture should be valid: " + inputValidation.issues());
        assertEquals(320, inputValidation.width());
        assertEquals(180, inputValidation.height());

        // Step 3: Build BasicTimeline fixture referencing controlled media source
        TimelineOutputSpec outputSpec = new TimelineOutputSpec(
                "mp4", "640x360", 30.0, "h264", 1000000,
                new TimelineAudioSpec("aac", 48000, 2, 128000, 1.0, false),
                "yuv420p");
        TimelineAssetRef assetRef = TimelineAssetRef.of("asset-001", "internal://testsrc");
        TimelineClip clip = TimelineClip.of("clip-001", assetRef, 0.0, 0.0, 2.0);
        TimelineTrack track = new TimelineTrack(
                "track-v1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);
        TimelineTextOverlay caption = new TimelineTextOverlay(
                "caption-001", "Hello Real Media", "DejaVu Sans", 24,
                "#FFFFFF", "center", "bottom",
                0.5, 2.0, null);
        TimelineSpec timeline = new TimelineSpec(
                "timeline-001", "Real Media Test", "P2L.3 real media integration test",
                List.of(track), List.of(caption), outputSpec, 3.0, Map.of());

        // Step 4: Produce FFmpegLibassBasicRenderPlan
        var planRequest = new FFmpegLibassBasicRenderPlanningRequest(
                new FFmpegLibassBasicRenderPlanningRequestId("plan-req-real-media-001"),
                timeline, FFmpegLibassBasicRenderPolicy.conservative(), Map.of());
        var planResult = FFmpegLibassBasicRenderPlanner.plan(planRequest);

        assertNotNull(planResult.plan(), "Plan should be produced: " + planResult.issues());
        assertEquals(FFmpegLibassBasicRenderPlanStatus.READY, planResult.plan().status(),
                "Plan should be READY: " + planResult.issues());

        // Step 5: Execute through local runner with real media source
        var report = BasicRenderPlanLocalRunner.executeAndReport(
                planResult.plan(), policy, fixtureResult.spec());

        // Step 6: Validate result
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

        // Expect PASS or PASS_WITH_WARNINGS
        assertTrue(
                result.status() == LocalRenderExecutionStatus.PASS
                || result.status() == LocalRenderExecutionStatus.PASS_WITH_WARNINGS,
                "Expected PASS or PASS_WITH_WARNINGS but got " + result.status()
                        + " with issues: " + result.issues());

        // Validate input source metadata
        assertTrue(result.hasInputSource(), "Result should have input source");
        assertEquals(LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE, result.inputSourceKind());
        assertEquals(LocalMediaSourceOrigin.PLATFORM_GENERATED, result.inputSourceOrigin());
        assertNotNull(result.inputPath());
        assertTrue(result.inputFileBytes() > 0, "Input file should have content");

        // Validate output
        assertNotNull(result.outputPath(), "Output path should be set");
        assertTrue(Files.exists(result.outputPath()), "Output file should exist");
        assertTrue(result.outputFileBytes() > 0, "Output file should have content");
        assertEquals(0, result.ffmpegExitCode(), "FFmpeg should exit cleanly");

        // Validate report was written
        Path reportPath = result.outputPath().getParent()
                .resolve("local-render-execution-report.txt");
        assertTrue(Files.exists(reportPath), "Report file should exist");

        System.out.println("Real Media Source Smoke PASSED");
        System.out.println("  Execution ID: " + result.executionId().value());
        System.out.println("  Plan ID: " + result.planId());
        System.out.println("  Input: " + result.inputPath());
        System.out.println("  Input Size: " + result.inputFileBytes() + " bytes");
        System.out.println("  Input Resolution: " + result.inputWidth() + "x" + result.inputHeight());
        System.out.println("  Output: " + result.outputPath());
        System.out.println("  Output Size: " + result.outputFileBytes() + " bytes");
        System.out.println("  Output Resolution: " + result.actualWidth() + "x" + result.actualHeight());
        System.out.println("  Output Duration: " + result.actualDurationSec() + "s");
        System.out.println("  Output Codec: " + result.actualCodec());
        System.out.println("  Caption Overlay Count: " + result.captionOverlayCount());
    }

    private static boolean isBinaryAvailable(String binary) {
        try {
            ProcessBuilder pb = new ProcessBuilder(binary, "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return done && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
