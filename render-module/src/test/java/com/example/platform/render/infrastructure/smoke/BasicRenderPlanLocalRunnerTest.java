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
                        0, 180, 2.0, 30, "h264", "mp4", tempDir, List.of(), List.of(), null, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new LocalRenderExecutionRequest(
                        LocalRenderExecutionId.generate(), "plan-1",
                        320, 0, 2.0, 30, "h264", "mp4", tempDir, List.of(), List.of(), null, Map.of()));
    }

    @Test
    void executionResultImmutability() {
        var result = new LocalRenderExecutionResult(
                LocalRenderExecutionId.generate(), "plan-1",
                LocalRenderExecutionStatus.PASS,
                null, null, null, 0, -1, 0, 0, 0, null, null,
                null, 0, 0, java.time.Duration.ZERO, 0,
                320, 180, 2.0, "h264", "mp4",
                List.of("step:EFFECT"), 0, List.of(), Map.of("key", "value"));

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

    // --- Caption overlay adapter tests ---

    @Test
    void adapterRecognizesCaptionOverlay() {
        var profileStep = buildOutputProfileStep(320, 180, 30, "h264", "mp4");
        var captionStep = buildCaptionOverlayStep("cap-1", "Hello World", 0, 2000);
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(
                        buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep)),
                        buildStage(FFmpegLibassBasicRenderStageType.PLAN_CAPTION_OVERLAYS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(captionStep))
                ));
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        assertFalse(result.blocked());
        assertNotNull(result.request());
        assertFalse(result.request().captionOverlaySpecs().isEmpty());
        assertEquals(1, result.request().captionOverlaySpecs().size());
        assertEquals("Hello World", result.request().captionOverlaySpecs().get(0).text());
    }

    @Test
    void captionMissingTextIsRejected() {
        var profileStep = buildOutputProfileStep(320, 180, 30, "h264", "mp4");
        var captionStep = buildCaptionOverlayStep("cap-1", "", 0, 2000);
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(
                        buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep)),
                        buildStage(FFmpegLibassBasicRenderStageType.PLAN_CAPTION_OVERLAYS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(captionStep))
                ));
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        assertFalse(result.blocked());
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == LocalRenderSmokeIssueCode.CAPTION_TEXT_MISSING));
    }

    @Test
    void captionInvalidTimeRangeIsRejected() {
        var profileStep = buildOutputProfileStep(320, 180, 30, "h264", "mp4");
        var captionStep = buildCaptionOverlayStep("cap-1", "Text", 5000, 2000); // end < start
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(
                        buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep)),
                        buildStage(FFmpegLibassBasicRenderStageType.PLAN_CAPTION_OVERLAYS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(captionStep))
                ));
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        assertFalse(result.blocked());
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == LocalRenderSmokeIssueCode.CAPTION_TIME_RANGE_INVALID));
    }

    @Test
    void captionRawFiltergraphIsRejected() {
        var profileStep = buildOutputProfileStep(320, 180, 30, "h264", "mp4");
        var captionStep = buildCaptionOverlayStepWithRawField("cap-1", "Text", 0, 2000, "rawFiltergraph", "drawtext=...");
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(
                        buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep)),
                        buildStage(FFmpegLibassBasicRenderStageType.PLAN_CAPTION_OVERLAYS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(captionStep))
                ));
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        assertFalse(result.blocked());
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == LocalRenderSmokeIssueCode.CAPTION_RAW_FILTERGRAPH_FORBIDDEN));
    }

    @Test
    void captionRawAssStyleIsRejected() {
        var profileStep = buildOutputProfileStep(320, 180, 30, "h264", "mp4");
        var captionStep = buildCaptionOverlayStepWithRawField("cap-1", "Text", 0, 2000, "rawAssStyle", "{\\pos(0,0)}");
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(
                        buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep)),
                        buildStage(FFmpegLibassBasicRenderStageType.PLAN_CAPTION_OVERLAYS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(captionStep))
                ));
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == LocalRenderSmokeIssueCode.CAPTION_RAW_ASS_STYLE_FORBIDDEN));
    }

    @Test
    void captionExternalSubtitleIsRejected() {
        var profileStep = buildOutputProfileStep(320, 180, 30, "h264", "mp4");
        var captionStep = buildCaptionOverlayStepWithRawField("cap-1", "Text", 0, 2000, "externalSubtitlePath", "/tmp/sub.srt");
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(
                        buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep)),
                        buildStage(FFmpegLibassBasicRenderStageType.PLAN_CAPTION_OVERLAYS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(captionStep))
                ));
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == LocalRenderSmokeIssueCode.CAPTION_EXTERNAL_SUBTITLE_FORBIDDEN));
    }

    @Test
    void captionFontPathIsRejected() {
        var profileStep = buildOutputProfileStep(320, 180, 30, "h264", "mp4");
        var captionStep = buildCaptionOverlayStepWithRawField("cap-1", "Text", 0, 2000, "fontPath", "/tmp/font.ttf");
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(
                        buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep)),
                        buildStage(FFmpegLibassBasicRenderStageType.PLAN_CAPTION_OVERLAYS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(captionStep))
                ));
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == LocalRenderSmokeIssueCode.CAPTION_FONT_PATH_FORBIDDEN));
    }

    @Test
    void captionOverlayIssueCodesExist() {
        assertNotNull(LocalRenderSmokeIssueCode.CAPTION_OVERLAY_MISSING);
        assertNotNull(LocalRenderSmokeIssueCode.CAPTION_OVERLAY_UNSUPPORTED);
        assertNotNull(LocalRenderSmokeIssueCode.CAPTION_TEXT_MISSING);
        assertNotNull(LocalRenderSmokeIssueCode.CAPTION_TEXT_TOO_LONG);
        assertNotNull(LocalRenderSmokeIssueCode.CAPTION_TIME_RANGE_INVALID);
        assertNotNull(LocalRenderSmokeIssueCode.CAPTION_PLACEMENT_UNSUPPORTED);
        assertNotNull(LocalRenderSmokeIssueCode.CAPTION_STYLE_UNSUPPORTED);
        assertNotNull(LocalRenderSmokeIssueCode.CAPTION_FONT_UNSUPPORTED);
        assertNotNull(LocalRenderSmokeIssueCode.CAPTION_RAW_FILTERGRAPH_FORBIDDEN);
        assertNotNull(LocalRenderSmokeIssueCode.CAPTION_RAW_ASS_STYLE_FORBIDDEN);
        assertNotNull(LocalRenderSmokeIssueCode.CAPTION_EXTERNAL_SUBTITLE_FORBIDDEN);
        assertNotNull(LocalRenderSmokeIssueCode.CAPTION_FONT_PATH_FORBIDDEN);
        assertNotNull(LocalRenderSmokeIssueCode.CAPTION_OVERLAY_RENDER_FAILED);
    }

    @Test
    void captionTextIsSafelyEscaped() {
        var assContent = LocalFfmpegSmokeCommandBuilder.buildAssContent(
                List.of(new LocalCaptionOverlaySpec(
                        "cap-1", "Hello {World} \\test", 0, 2000)),
                320, 180);

        // Should not contain raw braces or backslashes in dialogue line
        assertTrue(assContent.contains("Hello World test"));
        assertFalse(assContent.contains("{World}"));
    }

    @Test
    void captionOverlayCountAppearsInResult() {
        var profileStep = buildOutputProfileStep(320, 180, 30, "h264", "mp4");
        var captionStep = buildCaptionOverlayStep("cap-1", "Hello", 0, 2000);
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(
                        buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep)),
                        buildStage(FFmpegLibassBasicRenderStageType.PLAN_CAPTION_OVERLAYS,
                                FFmpegLibassBasicRenderStageStatus.VALID, List.of(captionStep))
                ));
        // Use enabled policy so the runner doesn't skip
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);

        var result = BasicRenderPlanLocalRunner.execute(plan, policy);

        assertNotNull(result.safeMetadata());
        // Caption count should be present (may be 0 if adaptation rejected, but metadata key should exist)
        assertTrue(result.safeMetadata().containsKey("captionOverlayCount"),
                "Result metadata should contain captionOverlayCount");
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

    // --- Media source tests (P2L.3) ---

    @Test
    void mediaSourceIssueCodesExist() {
        assertNotNull(LocalRenderSmokeIssueCode.MEDIA_SOURCE_MISSING);
        assertNotNull(LocalRenderSmokeIssueCode.MEDIA_SOURCE_UNSUPPORTED);
        assertNotNull(LocalRenderSmokeIssueCode.MEDIA_SOURCE_KIND_UNSUPPORTED);
        assertNotNull(LocalRenderSmokeIssueCode.MEDIA_SOURCE_ORIGIN_UNSUPPORTED);
        assertNotNull(LocalRenderSmokeIssueCode.MEDIA_SOURCE_PATH_FORBIDDEN);
        assertNotNull(LocalRenderSmokeIssueCode.MEDIA_SOURCE_REMOTE_URL_FORBIDDEN);
        assertNotNull(LocalRenderSmokeIssueCode.MEDIA_SOURCE_STORAGE_REFERENCE_FORBIDDEN);
        assertNotNull(LocalRenderSmokeIssueCode.MEDIA_SOURCE_PRODUCT_REFERENCE_FORBIDDEN);
        assertNotNull(LocalRenderSmokeIssueCode.MEDIA_SOURCE_MATERIALIZATION_FAILED);
        assertNotNull(LocalRenderSmokeIssueCode.MEDIA_SOURCE_FILE_MISSING);
        assertNotNull(LocalRenderSmokeIssueCode.MEDIA_SOURCE_VALIDATION_FAILED);
        assertNotNull(LocalRenderSmokeIssueCode.INPUT_FFMPEG_EXIT_NONZERO);
        assertNotNull(LocalRenderSmokeIssueCode.INPUT_FFPROBE_EXIT_NONZERO);
        assertNotNull(LocalRenderSmokeIssueCode.INPUT_OUTPUT_DIRECTORY_UNAVAILABLE);
    }

    @Test
    void mediaSourceKindEnumValues() {
        assertNotNull(LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE);
    }

    @Test
    void mediaSourceOriginEnumValues() {
        assertNotNull(LocalMediaSourceOrigin.PLATFORM_GENERATED);
        assertNotNull(LocalMediaSourceOrigin.CONTROLLED_TEST_FIXTURE);
    }

    @Test
    void mediaSourceSpecAcceptsControlledLocalFixture() {
        var spec = new LocalMediaSourceSpec(
                LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE,
                LocalMediaSourceOrigin.PLATFORM_GENERATED,
                tempDir.resolve("input-fixture.mp4"),
                "mp4", "h264");

        assertEquals(LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE, spec.kind());
        assertEquals(LocalMediaSourceOrigin.PLATFORM_GENERATED, spec.origin());
        assertEquals("mp4", spec.format());
        assertEquals("h264", spec.codec());
    }

    @Test
    void mediaSourceSpecRejectsNullKind() {
        assertThrows(NullPointerException.class,
                () -> new LocalMediaSourceSpec(
                        null, LocalMediaSourceOrigin.PLATFORM_GENERATED,
                        tempDir.resolve("test.mp4"), "mp4", "h264"));
    }

    @Test
    void mediaSourceSpecRejectsNullPath() {
        assertThrows(NullPointerException.class,
                () -> new LocalMediaSourceSpec(
                        LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE,
                        LocalMediaSourceOrigin.PLATFORM_GENERATED,
                        null, "mp4", "h264"));
    }

    @Test
    void mediaSourceSpecAcceptsPathUnderControlledRoot() {
        var spec = new LocalMediaSourceSpec(
                LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE,
                LocalMediaSourceOrigin.PLATFORM_GENERATED,
                tempDir.resolve("subdir").resolve("input.mp4"),
                "mp4", "h264");

        assertTrue(spec.isUnderControlledRoot(tempDir));
        assertFalse(spec.isUnderControlledRoot(Path.of("/other/root")));
    }

    @Test
    void mediaSourceSpecRejectsRemoteUrl() {
        // Path.of() normalizes double slashes, so we test with the normalized form
        var spec1 = new LocalMediaSourceSpec(
                LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE,
                LocalMediaSourceOrigin.PLATFORM_GENERATED,
                Path.of("http://example.com/video.mp4"),
                "mp4", "h264");
        assertFalse(spec1.isPathSafe(), "http URL should be rejected");

        var spec2 = new LocalMediaSourceSpec(
                LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE,
                LocalMediaSourceOrigin.PLATFORM_GENERATED,
                Path.of("ftp://example.com/video.mp4"),
                "mp4", "h264");
        assertFalse(spec2.isPathSafe(), "ftp URL should be rejected");
    }

    @Test
    void mediaSourceSpecRejectsPathTraversal() {
        var spec = new LocalMediaSourceSpec(
                LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE,
                LocalMediaSourceOrigin.PLATFORM_GENERATED,
                tempDir.resolve("../../../etc/passwd"),
                "mp4", "h264");

        assertFalse(spec.isPathSafe());
    }

    @Test
    void mediaSourceSpecRejectsStorageInternals() {
        var spec = new LocalMediaSourceSpec(
                LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE,
                LocalMediaSourceOrigin.PLATFORM_GENERATED,
                Path.of("/bucket/my-bucket/objectKey/my-key"),
                "mp4", "h264");

        assertFalse(spec.isPathSafe());
    }

    @Test
    void executionRequestHasRealMediaSourceReturnsFalseByDefault() {
        var profileStep = buildOutputProfileStep(320, 180, 30, "h264", "mp4");
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                        FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep))));
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        assertFalse(result.blocked());
        assertFalse(result.request().hasRealMediaSource());
        assertNull(result.request().mediaSourceSpec());
    }

    @Test
    void adapterAcceptsControlledMediaSource() {
        var profileStep = buildOutputProfileStep(320, 180, 30, "h264", "mp4");
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                        FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep))));
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);
        var mediaSource = new LocalMediaSourceSpec(
                LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE,
                LocalMediaSourceOrigin.PLATFORM_GENERATED,
                tempDir.resolve("input-fixture.mp4"),
                "mp4", "h264");

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy, mediaSource);

        assertFalse(result.blocked());
        assertNotNull(result.request());
        assertTrue(result.request().hasRealMediaSource());
        assertEquals(LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE, result.request().mediaSourceSpec().kind());
    }

    @Test
    void adapterRejectsMediaSourceOutsideControlledRoot() {
        var profileStep = buildOutputProfileStep(320, 180, 30, "h264", "mp4");
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                        FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep))));
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);
        var mediaSource = new LocalMediaSourceSpec(
                LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE,
                LocalMediaSourceOrigin.PLATFORM_GENERATED,
                Path.of("/other/root/input.mp4"),
                "mp4", "h264");

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy, mediaSource);

        assertTrue(result.blocked());
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == LocalRenderSmokeIssueCode.MEDIA_SOURCE_PATH_FORBIDDEN));
    }

    @Test
    void adapterRejectsRemoteUrlMediaSource() {
        var profileStep = buildOutputProfileStep(320, 180, 30, "h264", "mp4");
        var plan = buildPlan(FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(buildStage(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                        FFmpegLibassBasicRenderStageStatus.VALID, List.of(profileStep))));
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);
        var mediaSource = new LocalMediaSourceSpec(
                LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE,
                LocalMediaSourceOrigin.PLATFORM_GENERATED,
                Path.of("https://example.com/video.mp4"),
                "mp4", "h264");

        var result = BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy, mediaSource);

        assertTrue(result.blocked());
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == LocalRenderSmokeIssueCode.MEDIA_SOURCE_PATH_FORBIDDEN));
    }

    @Test
    void fixtureGeneratorBuildsValidConfig() {
        var config = LocalMediaSourceFixtureGenerator.FixtureConfig.defaults(tempDir);

        assertEquals(320, config.width());
        assertEquals(180, config.height());
        assertEquals(3.0, config.durationSec());
        assertEquals(30, config.fps());
        assertEquals("h264", config.codec());
        assertEquals("mp4", config.container());
    }

    @Test
    void fixtureGeneratorRejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> new LocalMediaSourceFixtureGenerator.FixtureConfig(
                        tempDir, 0, 180, 3.0, 30, "h264", "mp4"));
        assertThrows(IllegalArgumentException.class,
                () -> new LocalMediaSourceFixtureGenerator.FixtureConfig(
                        tempDir, 320, 0, 3.0, 30, "h264", "mp4"));
    }

    @Test
    void realMediaSourceCommandBuilderRejectsNullInputPath() {
        var policy = new LocalRenderSmokePolicy(
                true, 20, tempDir, true, Set.of("ffmpeg", "ffprobe"), false);

        assertThrows(NullPointerException.class,
                () -> LocalFfmpegSmokeCommandBuilder.buildPlanDrivenRealMediaWithCaptions(
                        null, 320, 180, 30, "h264", "mp4", tempDir, List.of(), policy));
    }

    @Test
    void resultHasInputSourceReturnsFalseForSyntheticInput() {
        var result = new LocalRenderExecutionResult(
                LocalRenderExecutionId.generate(), "plan-1",
                LocalRenderExecutionStatus.PASS,
                null, null, null, 0, -1, 0, 0, 0, null, null,
                null, 0, 0, java.time.Duration.ZERO, 0,
                320, 180, 2.0, "h264", "mp4",
                List.of(), 0, List.of(), Map.of());

        assertFalse(result.hasInputSource());
    }

    @Test
    void resultHasInputSourceReturnsTrueForRealMedia() {
        var result = new LocalRenderExecutionResult(
                LocalRenderExecutionId.generate(), "plan-1",
                LocalRenderExecutionStatus.PASS,
                LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE,
                LocalMediaSourceOrigin.PLATFORM_GENERATED,
                tempDir.resolve("input.mp4"), 1000, 0, 320, 180, 3.0, "h264", "mp4",
                null, 0, 0, java.time.Duration.ZERO, 0,
                320, 180, 3.0, "h264", "mp4",
                List.of(), 0, List.of(), Map.of());

        assertTrue(result.hasInputSource());
        assertEquals(LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE, result.inputSourceKind());
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

    private FFmpegLibassBasicRenderStep buildCaptionOverlayStep(
            String captionId, String text, double startMs, double endMs) {
        return new FFmpegLibassBasicRenderStep(
                new FFmpegLibassBasicRenderStepId("step-caption-" + captionId),
                FFmpegLibassBasicRenderStepType.APPLY_CAPTION_OVERLAY,
                new FFmpegLibassBasicRenderStepTarget(
                        FFmpegLibassBasicRenderStepTargetType.CAPTION,
                        captionId, Map.of()),
                List.of(
                        new FFmpegLibassBasicRenderStepParameter(
                                "captionId", FFmpegLibassBasicRenderStepParameterType.STRING, captionId, Map.of()),
                        new FFmpegLibassBasicRenderStepParameter(
                                "startMs", FFmpegLibassBasicRenderStepParameterType.DECIMAL, startMs, Map.of()),
                        new FFmpegLibassBasicRenderStepParameter(
                                "endMs", FFmpegLibassBasicRenderStepParameterType.DECIMAL, endMs, Map.of()),
                        new FFmpegLibassBasicRenderStepParameter(
                                "textRef", FFmpegLibassBasicRenderStepParameterType.STRING, text, Map.of())
                ),
                FFmpegLibassBasicRenderStepSource.CAPTION_OVERLAY, Map.of());
    }

    private FFmpegLibassBasicRenderStep buildCaptionOverlayStepWithRawField(
            String captionId, String text, double startMs, double endMs,
            String rawFieldName, String rawFieldValue) {
        var params = new java.util.ArrayList<>(List.of(
                new FFmpegLibassBasicRenderStepParameter(
                        "captionId", FFmpegLibassBasicRenderStepParameterType.STRING, captionId, Map.of()),
                new FFmpegLibassBasicRenderStepParameter(
                        "startMs", FFmpegLibassBasicRenderStepParameterType.DECIMAL, startMs, Map.of()),
                new FFmpegLibassBasicRenderStepParameter(
                        "endMs", FFmpegLibassBasicRenderStepParameterType.DECIMAL, endMs, Map.of()),
                new FFmpegLibassBasicRenderStepParameter(
                        "textRef", FFmpegLibassBasicRenderStepParameterType.STRING, text, Map.of())
        ));
        params.add(new FFmpegLibassBasicRenderStepParameter(
                rawFieldName, FFmpegLibassBasicRenderStepParameterType.STRING, rawFieldValue, Map.of()));
        return new FFmpegLibassBasicRenderStep(
                new FFmpegLibassBasicRenderStepId("step-caption-" + captionId),
                FFmpegLibassBasicRenderStepType.APPLY_CAPTION_OVERLAY,
                new FFmpegLibassBasicRenderStepTarget(
                        FFmpegLibassBasicRenderStepTargetType.CAPTION,
                        captionId, Map.of()),
                params,
                FFmpegLibassBasicRenderStepSource.CAPTION_OVERLAY, Map.of());
    }
}
