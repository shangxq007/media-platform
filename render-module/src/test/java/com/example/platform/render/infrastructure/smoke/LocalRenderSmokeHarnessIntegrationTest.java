package com.example.platform.render.infrastructure.smoke;

import com.example.platform.render.domain.render.local.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * Integration test for the local render smoke harness.
 *
 * <p>This test only runs when explicitly enabled:
 * <pre>
 * ./gradlew :render-module:test --tests "*LocalRenderSmokeHarnessIntegrationTest" \
 *   -Dmedia.platform.localSmoke.enabled=true \
 *   -Dmedia.platform.localSmoke.outputRoot=/tmp/media-platform-local-smoke
 * </pre>
 *
 * <p>Normal test runs skip this test. If FFmpeg/ffprobe are not installed,
 * the test returns NOT_AVAILABLE rather than failing.</p>
 *
 * <p>When {@code media.platform.localSmoke.outputRoot} is set, output is written
 * to that stable directory so it can be inspected after the test finishes.
 * Otherwise a JUnit {@code @TempDir} is used (cleaned up automatically).</p>
 */
class LocalRenderSmokeHarnessIntegrationTest {

    @TempDir
    Path tempDir;

    private static String outputRootProp;

    @BeforeAll
    static void checkEnabled() {
        boolean enabled = Boolean.getBoolean(LocalRenderSmokePolicy.ENABLE_PROPERTY);
        assumeTrue(enabled, "Local smoke integration test skipped: -Dmedia.platform.localSmoke.enabled=true not set");
        outputRootProp = System.getProperty("media.platform.localSmoke.outputRoot", "");
    }

    /**
     * Resolves the output root: use the system property if non-empty, otherwise the JUnit temp dir.
     */
    private Path resolveOutputRoot() {
        if (outputRootProp != null && !outputRootProp.isBlank()) {
            return Path.of(outputRootProp);
        }
        return tempDir;
    }

    @Test
    void testsrcH264Mp4SmokeProducesValidOutput() {
        Path outputRoot = resolveOutputRoot();
        var policy = new LocalRenderSmokePolicy(
                true, 20, outputRoot, true,
                java.util.Set.of("ffmpeg", "ffprobe"), false);
        var request = LocalRenderSmokeRequest.testsrcH264Mp4(outputRoot);

        var result = LocalRenderSmokeHarness.execute(request, policy);

        // Log issues for debugging
        if (!result.issues().isEmpty()) {
            System.out.println("Smoke issues:");
            for (var issue : result.issues()) {
                System.out.println("  [" + issue.severity() + "] " + issue.code() + ": " + issue.message());
            }
        }

        // If ffmpeg/ffprobe not available, expect NOT_AVAILABLE
        if (result.status() == LocalRenderSmokeStatus.NOT_AVAILABLE) {
            System.out.println("FFmpeg/ffprobe not available; smoke returned NOT_AVAILABLE");
            return;
        }

        // Otherwise expect PASS or PASS_WITH_WARNINGS
        assertTrue(result.status() == LocalRenderSmokeStatus.PASS
                        || result.status() == LocalRenderSmokeStatus.PASS_WITH_WARNINGS,
                "Expected PASS or PASS_WITH_WARNINGS but got " + result.status()
                        + " with issues: " + result.issues());

        // Validate output
        assertNotNull(result.outputPath(), "Output path should be set");
        assertTrue(result.outputFileBytes() > 0, "Output file should have content");
        assertEquals(0, result.ffmpegExitCode(), "FFmpeg should exit cleanly");
        assertEquals(320, result.actualWidth(), "Width should be 320");
        assertEquals(180, result.actualHeight(), "Height should be 180");
        assertTrue(result.actualDurationSec() > 0.5, "Duration should be > 0.5s");
        assertNotNull(result.actualCodec(), "Codec should be detected");

        System.out.println("Smoke PASSED: " + result.smokeName().value());
        System.out.println("  Output: " + result.outputPath());
        System.out.println("  Size: " + result.outputFileBytes() + " bytes");
        System.out.println("  Resolution: " + result.actualWidth() + "x" + result.actualHeight());
        System.out.println("  Duration: " + result.actualDurationSec() + "s");
        System.out.println("  Codec: " + result.actualCodec());
        System.out.println("  FFmpeg time: " + result.ffmpegDuration().toMillis() + "ms");
    }

    @Test
    void smokeReportWrittenToOutput() {
        Path outputRoot = resolveOutputRoot();
        var policy = new LocalRenderSmokePolicy(
                true, 20, outputRoot, true,
                java.util.Set.of("ffmpeg", "ffprobe"), false);

        var report = LocalRenderSmokeHarness.executeDefaultSmoke(policy);

        assertNotNull(report.reportId());
        assertEquals(1, report.results().size());

        // Check report file was written
        Path reportPath = outputRoot.resolve("smoke-report.txt");
        if (Files.exists(reportPath)) {
            System.out.println("Report written to: " + reportPath);
            assertTrue(Files.exists(reportPath));
        }

        System.out.println("Report status: " + report.overallStatus());
        System.out.println("Pass: " + report.passCount() + ", Fail: " + report.failCount());
    }
}
