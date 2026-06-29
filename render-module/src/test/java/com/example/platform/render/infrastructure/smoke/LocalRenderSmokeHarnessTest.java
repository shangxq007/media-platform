package com.example.platform.render.infrastructure.smoke;

import com.example.platform.render.domain.render.local.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the local render smoke harness.
 * Does not require FFmpeg — tests policy gating and command building.
 */
class LocalRenderSmokeHarnessTest {

    @TempDir
    Path tempDir;

    @Test
    void disabledPolicyReturnsSkipped() {
        var policy = LocalRenderSmokePolicy.defaultDisabled();
        var request = LocalRenderSmokeRequest.testsrcH264Mp4(tempDir);

        var result = LocalRenderSmokeHarness.execute(request, policy);

        assertEquals(LocalRenderSmokeStatus.SKIPPED, result.status());
        assertFalse(result.issues().isEmpty());
        assertEquals("policy-disabled", result.safeMetadata().get("reason"));
    }

    @Test
    void smokeReportDeterministic() {
        var policy = LocalRenderSmokePolicy.defaultDisabled();
        var report = LocalRenderSmokeHarness.executeDefaultSmoke(policy);

        assertNotNull(report.reportId());
        assertTrue(report.reportId().startsWith("smoke-report-"));
        assertNotNull(report.executedAt());
        assertEquals(1, report.results().size());
    }

    @Test
    void commandBuilderProducesArgs() {
        var policy = LocalRenderSmokePolicy.defaultEnabled();
        var request = LocalRenderSmokeRequest.testsrcH264Mp4(tempDir);

        var buildResult = LocalFfmpegSmokeCommandBuilder.buildTestsrcH264Mp4(request, policy);

        assertFalse(buildResult.args().isEmpty());
        assertEquals("ffmpeg", buildResult.args().get(0));
        assertTrue(buildResult.args().contains("-y"));
        assertTrue(buildResult.args().contains("-c:v"));
        assertTrue(buildResult.args().contains("libx264"));
        assertTrue(buildResult.outputPath().toString().endsWith("output.mp4"));
        assertTrue(buildResult.issues().isEmpty());
    }

    @Test
    void commandBuilderRejectsDisallowedBinary() {
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true,
                java.util.Set.of("notffmpeg"), false);
        var request = LocalRenderSmokeRequest.testsrcH264Mp4(tempDir);

        var buildResult = LocalFfmpegSmokeCommandBuilder.buildTestsrcH264Mp4(request, policy);

        assertFalse(buildResult.args().isEmpty() ? false : true);
        // Should have blocking issue
        assertTrue(buildResult.issues().stream()
                .anyMatch(i -> i.severity() == LocalRenderSmokeIssueSeverity.BLOCKING));
    }

    @Test
    void commandBuilderNoShellInvocation() {
        var policy = LocalRenderSmokePolicy.defaultEnabled();
        var request = LocalRenderSmokeRequest.testsrcH264Mp4(tempDir);

        var buildResult = LocalFfmpegSmokeCommandBuilder.buildTestsrcH264Mp4(request, policy);

        // Args should never contain shell invocation
        String allArgs = String.join(" ", buildResult.args());
        assertFalse(allArgs.contains("sh -c"));
        assertFalse(allArgs.contains("bash -c"));
    }

    @Test
    void commandBuilderArgsAreList() {
        var policy = LocalRenderSmokePolicy.defaultEnabled();
        var request = LocalRenderSmokeRequest.testsrcH264Mp4(tempDir);

        var buildResult = LocalFfmpegSmokeCommandBuilder.buildTestsrcH264Mp4(request, policy);

        // Must return List<String>, not a single shell command string
        assertTrue(buildResult.args().size() > 1, "Args should be a list, not a single shell string");
        assertFalse(buildResult.args().get(0).contains(" "), "Binary should be separate from args");
    }

    @Test
    void commandBuilderOutputUnderControlledRoot() {
        var policy = LocalRenderSmokePolicy.defaultEnabled();
        var request = LocalRenderSmokeRequest.testsrcH264Mp4(tempDir);

        var buildResult = LocalFfmpegSmokeCommandBuilder.buildTestsrcH264Mp4(request, policy);

        assertTrue(buildResult.outputPath().startsWith(tempDir),
                "Output should be under controlled root");
    }

    @Test
    void ffprobeParserHandlesKeyValues() {
        String output = """
                [STREAM]
                codec_name=h264
                width=320
                height=180
                duration=2.000000
                [/STREAM]
                [FORMAT]
                format_name=mov,mp4,m4a,3gp,3g2,mj2
                duration=2.004000
                [/FORMAT]
                """;

        var parsed = LocalFfprobeValidator.parseFfprobeOutput(output);

        assertEquals("h264", parsed.get("codec_name"));
        assertEquals("320", parsed.get("width"));
        assertEquals("180", parsed.get("height"));
        // FORMAT section duration overrides STREAM section duration (last value wins)
        assertEquals("2.004000", parsed.get("duration"));
        assertEquals("mov,mp4,m4a,3gp,3g2,mj2", parsed.get("format_name"));
    }

    @Test
    void ffprobeParserHandlesEmpty() {
        var parsed = LocalFfprobeValidator.parseFfprobeOutput("");
        assertTrue(parsed.isEmpty());
    }

    @Test
    void ffprobeParserHandlesNull() {
        var parsed = LocalFfprobeValidator.parseFfprobeOutput(null);
        assertTrue(parsed.isEmpty());
    }

    @Test
    void smokeResultImmutability() {
        var result = new LocalRenderSmokeResult(
                LocalRenderSmokeId.generate(),
                new LocalRenderSmokeName("test"),
                LocalRenderSmokeStatus.PASS,
                null, 0, 0, java.time.Duration.ZERO, 0,
                320, 180, 2.0, "h264", "mp4",
                List.of(), Map.of("key", "value"));

        assertNotNull(result.issues());
        assertNotNull(result.safeMetadata());
        assertEquals(320, result.actualWidth());
        assertEquals("h264", result.actualCodec());
    }
}
