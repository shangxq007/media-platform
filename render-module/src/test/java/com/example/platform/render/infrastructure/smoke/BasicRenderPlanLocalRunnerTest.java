package com.example.platform.render.infrastructure.smoke;

import com.example.platform.render.domain.render.local.*;
import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.render.plan.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the BasicRenderPlan-to-local-runner bridge.
 * Does not require FFmpeg.
 */
class BasicRenderPlanLocalRunnerTest {

    @TempDir
    Path tempDir;

    // --- Adapter tests ---

    @Test
    void adapterRejectsBlockedPlan() {
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.BLOCKED, List.of());
        var policy = LocalRenderSmokePolicy.defaultDisabled();

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        assertTrue(result.blocked());
        assertNull(result.request());
        assertFalse(result.issues().isEmpty());
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == LocalRenderSmokeIssueCode.BASIC_RENDER_PLAN_BLOCKED));
    }

    @Test
    void adapterRejectsUnsupportedPlan() {
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.UNSUPPORTED, List.of());
        var policy = LocalRenderSmokePolicy.defaultDisabled();

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        assertTrue(result.blocked());
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == LocalRenderSmokeIssueCode.BASIC_RENDER_PLAN_UNSUPPORTED));
    }

    @Test
    void adapterRejectsInvalidPlan() {
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.INVALID, List.of());
        var policy = LocalRenderSmokePolicy.defaultDisabled();

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        assertTrue(result.blocked());
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == LocalRenderSmokeIssueCode.BASIC_RENDER_PLAN_INVALID));
    }

    @Test
    void adapterRejectsPlanWithoutOutputProfile() {
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY, List.of());
        var policy = LocalRenderSmokePolicy.defaultDisabled();

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        assertTrue(result.blocked());
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == LocalRenderSmokeIssueCode.OUTPUT_PROFILE_MISSING));
    }

    @Test
    void adapterMapsOutputProfileToRequest() {
        var profileStep = buildOutputProfileStep(1920, 1080, 30, "h264", "mp4");
        var encodeStep = buildEncodeOutputStep("h264", "mp4");
        var verifyStep = buildVerifyOutputStep();
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(
                        buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep)),
                        buildStage(FFmpegLibassBasicRenderStageType.PLAN_OUTPUT_ENCODING,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(encodeStep)),
                        buildStage(FFmpegLibassBasicRenderStageType.PLAN_OUTPUT_VERIFICATION,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(verifyStep))
                ));
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        assertFalse(result.blocked());
        assertNotNull(result.request());
        assertEquals(1920, result.request().width());
        assertEquals(1080, result.request().height());
        assertEquals(30, result.request().fps());
        assertEquals("h264", result.request().videoCodec());
        assertEquals("mp4", result.request().container());
        assertTrue(result.request().planId().contains("plan-test"));
    }

    @Test
    void adapterReportsUnsupportedSteps() {
        var profileStep = buildOutputProfileStep(640, 480, 24, "h264", "mp4");
        var effectStep = new FFmpegLibassBasicRenderStep(
                new FFmpegLibassBasicRenderStepId("step-effect"),
                FFmpegLibassBasicRenderStepType.APPLY_EFFECT_OPERATION,
                null, List.of(),
                FFmpegLibassBasicRenderStepSource.EFFECT_PLAN, Map.of());
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(
                        buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep)),
                        buildStage(FFmpegLibassBasicRenderStageType.PLAN_EFFECTS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(effectStep))
                ));
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        assertFalse(result.blocked());
        assertNotNull(result.request());
        assertFalse(result.request().unsupportedSteps().isEmpty());
        assertTrue(result.request().unsupportedSteps().contains("step:APPLY_EFFECT_OPERATION"));
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == LocalRenderSmokeIssueCode.UNSUPPORTED_RENDER_STEP));
    }

    @Test
    void adapterReportsSyntheticInputRequired() {
        var profileStep = buildOutputProfileStep(320, 180, 30, "h264", "mp4");
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                        FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep))));
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        assertFalse(result.blocked());
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == LocalRenderSmokeIssueCode.SYNTHETIC_INPUT_REQUIRED));
    }

    // --- Runner tests ---

    @Test
    void runnerDisabledPolicyReturnsSkipped() {
        var profileStep = buildOutputProfileStep(320, 180, 30, "h264", "mp4");
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                        FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep))));
        var policy = LocalRenderSmokePolicy.defaultDisabled();

        var result = BasicRenderPlanLocalRunner.execute(plan, policy);

        assertEquals(LocalRenderExecutionStatus.SKIPPED, result.status());
        assertFalse(result.issues().isEmpty());
    }

    @Test
    void runnerBlockedPlanReturnsBlocked() {
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.BLOCKED, List.of());
        var policy = LocalRenderSmokePolicy.defaultEnabled();

        var result = BasicRenderPlanLocalRunner.execute(plan, policy);

        assertEquals(LocalRenderExecutionStatus.BLOCKED, result.status());
    }

    @Test
    void runnerReportContainsPlanId() {
        var profileStep = buildOutputProfileStep(320, 180, 30, "h264", "mp4");
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                        FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep))));
        var policy = LocalRenderSmokePolicy.defaultDisabled();

        var result = BasicRenderPlanLocalRunner.execute(plan, policy);

        assertNotNull(result.planId());
        assertTrue(result.planId().contains("plan-test"));
    }

    @Test
    void runnerUnsupportedPlanReturnsUnsupported() {
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.UNSUPPORTED, List.of());
        var policy = LocalRenderSmokePolicy.defaultEnabled();

        var result = BasicRenderPlanLocalRunner.execute(plan, policy);

        assertEquals(LocalRenderExecutionStatus.UNSUPPORTED, result.status());
    }

    // --- Domain type tests ---

    @Test
    void executionIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new LocalRenderExecutionId(""));
    }

    @Test
    void executionIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> new LocalRenderExecutionId(null));
    }

    @Test
    void executionIdGenerateProducesUnique() {
        var a = LocalRenderExecutionId.generate();
        var b = LocalRenderExecutionId.generate();
        assertNotEquals(a.value(), b.value());
        assertTrue(a.value().startsWith("exec-"));
    }

    @Test
    void executionStatusContainsRequired() {
        assertNotNull(LocalRenderExecutionStatus.PASS);
        assertNotNull(LocalRenderExecutionStatus.PASS_WITH_WARNINGS);
        assertNotNull(LocalRenderExecutionStatus.FAIL);
        assertNotNull(LocalRenderExecutionStatus.BLOCKED);
        assertNotNull(LocalRenderExecutionStatus.SKIPPED);
        assertNotNull(LocalRenderExecutionStatus.NOT_AVAILABLE);
        assertNotNull(LocalRenderExecutionStatus.UNSUPPORTED);
    }

    @Test
    void executionRequestRejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> new LocalRenderExecutionRequest(
                        LocalRenderExecutionId.generate(), "plan-1",
                        0, 180, 2.0, 30, "h264", "mp4", tempDir, List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new LocalRenderExecutionRequest(
                        LocalRenderExecutionId.generate(), "plan-1",
                        320, 0, 2.0, 30, "h264", "mp4", tempDir, List.of(), Map.of()));
    }

    @Test
    void executionResultImmutability() {
        var result = new LocalRenderExecutionResult(
                LocalRenderExecutionId.generate(), "plan-1",
                LocalRenderExecutionStatus.PASS,
                null, 0, 0, java.time.Duration.ZERO, 0,
                320, 180, 2.0, "h264", "mp4",
                List.of("step:EFFECT"), List.of(), Map.of("key", "value"));

        assertNotNull(result.issues());
        assertNotNull(result.safeMetadata());
        assertNotNull(result.unsupportedSteps());
        assertEquals(320, result.actualWidth());
    }

    @Test
    void smokeIssueCodeContainsBridgeCodes() {
        assertNotNull(LocalRenderSmokeIssueCode.BASIC_RENDER_PLAN_MISSING);
        assertNotNull(LocalRenderSmokeIssueCode.BASIC_RENDER_PLAN_INVALID);
        assertNotNull(LocalRenderSmokeIssueCode.BASIC_RENDER_PLAN_BLOCKED);
        assertNotNull(LocalRenderSmokeIssueCode.BASIC_RENDER_PLAN_UNSUPPORTED);
        assertNotNull(LocalRenderSmokeIssueCode.UNSUPPORTED_RENDER_STAGE);
        assertNotNull(LocalRenderSmokeIssueCode.UNSUPPORTED_RENDER_STEP);
        assertNotNull(LocalRenderSmokeIssueCode.OUTPUT_PROFILE_MISSING);
        assertNotNull(LocalRenderSmokeIssueCode.OUTPUT_PROFILE_UNSUPPORTED);
        assertNotNull(LocalRenderSmokeIssueCode.SYNTHETIC_INPUT_REQUIRED);
    }

    @Test
    void commandBuilderMapsCodecCorrectly() {
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);

        var result = LocalFfmpegSmokeCommandBuilder.buildPlanDrivenTestsrc(
                640, 480, 2.0, 30, "h264", "mp4", tempDir.resolve("test"), policy);

        assertFalse(result.args().isEmpty());
        assertTrue(result.args().contains("-c:v"));
        int codecIdx = result.args().indexOf("-c:v");
        assertEquals("libx264", result.args().get(codecIdx + 1));
    }

    @Test
    void stableOutputDirectoryPathIsDeterministic() {
        var profileStep = buildOutputProfileStep(320, 180, 30, "h264", "mp4");
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                        FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep))));
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);

        var result1 = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);
        var result2 = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        assertFalse(result1.blocked());
        assertFalse(result2.blocked());
        assertEquals(result1.request().outputRoot(), result2.request().outputRoot());
        assertTrue(result1.request().outputRoot().toString()
                .contains("local-plan-smoke-001-basic-render-plan-testsrc-h264-mp4"));
    }

    // --- Helpers ---

    private FFmpegLibassBasicRenderPlan buildPlan(
            FFmpegLibassBasicRenderPlanStatus status,
            List<FFmpegLibassBasicRenderStage> stages) {
        return new FFmpegLibassBasicRenderPlan(
                new FFmpegLibassBasicRenderPlanId("plan-test-001"),
                status, stages,
                new FFmpegLibassBasicRenderPlanSummary(
                        stages.size(),
                        stages.stream().mapToInt(s -> s.steps().size()).sum(),
                        0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of()),
                List.of(), Map.of());
    }

    private FFmpegLibassBasicRenderStage buildStage(
            FFmpegLibassBasicRenderStageType type,
            FFmpegLibassBasicRenderStageStatus status,
            List<FFmpegLibassBasicRenderStep> steps) {
        return new FFmpegLibassBasicRenderStage(
                new FFmpegLibassBasicRenderStageId("stage-" + type.name().toLowerCase()),
                type, status, steps, Map.of());
    }

    private FFmpegLibassBasicRenderStep buildOutputProfileStep(
            int width, int height, int fps, String videoCodec, String container) {
        return new FFmpegLibassBasicRenderStep(
                new FFmpegLibassBasicRenderStepId("step-output-profile"),
                FFmpegLibassBasicRenderStepType.DECLARE_OUTPUT_PROFILE,
                new FFmpegLibassBasicRenderStepTarget(
                        FFmpegLibassBasicRenderStepTargetType.OUTPUT_PROFILE,
                        "test-output", Map.of()),
                List.of(
                        new FFmpegLibassBasicRenderStepParameter(
                                "width", FFmpegLibassBasicRenderStepParameterType.INTEGER, width, Map.of()),
                        new FFmpegLibassBasicRenderStepParameter(
                                "height", FFmpegLibassBasicRenderStepParameterType.INTEGER, height, Map.of()),
                        new FFmpegLibassBasicRenderStepParameter(
                                "fps", FFmpegLibassBasicRenderStepParameterType.DECIMAL, fps, Map.of()),
                        new FFmpegLibassBasicRenderStepParameter(
                                "videoCodec", FFmpegLibassBasicRenderStepParameterType.STRING, videoCodec, Map.of()),
                        new FFmpegLibassBasicRenderStepParameter(
                                "container", FFmpegLibassBasicRenderStepParameterType.STRING, container, Map.of())
                ),
                FFmpegLibassBasicRenderStepSource.OUTPUT_PROFILE, Map.of());
    }

    private FFmpegLibassBasicRenderStep buildEncodeOutputStep(String videoCodec, String container) {
        return new FFmpegLibassBasicRenderStep(
                new FFmpegLibassBasicRenderStepId("step-encode-output"),
                FFmpegLibassBasicRenderStepType.ENCODE_OUTPUT,
                new FFmpegLibassBasicRenderStepTarget(
                        FFmpegLibassBasicRenderStepTargetType.FINAL_OUTPUT,
                        "test-output", Map.of()),
                List.of(
                        new FFmpegLibassBasicRenderStepParameter(
                                "container", FFmpegLibassBasicRenderStepParameterType.STRING, container, Map.of()),
                        new FFmpegLibassBasicRenderStepParameter(
                                "videoCodec", FFmpegLibassBasicRenderStepParameterType.STRING, videoCodec, Map.of())
                ),
                FFmpegLibassBasicRenderStepSource.OUTPUT_PROFILE, Map.of());
    }

    private FFmpegLibassBasicRenderStep buildVerifyOutputStep() {
        return new FFmpegLibassBasicRenderStep(
                new FFmpegLibassBasicRenderStepId("step-verify-output"),
                FFmpegLibassBasicRenderStepType.VERIFY_OUTPUT,
                new FFmpegLibassBasicRenderStepTarget(
                        FFmpegLibassBasicRenderStepTargetType.FINAL_OUTPUT,
                        "test-output", Map.of()),
                List.of(),
                FFmpegLibassBasicRenderStepSource.VERIFICATION, Map.of());
    }
}
