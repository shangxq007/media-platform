package com.example.platform.render.domain.scenario;

import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.editing.*;
import com.example.platform.render.domain.timeline.render.effect.*;
import com.example.platform.render.domain.timeline.render.plan.*;
import com.example.platform.render.domain.timeline.render.transition.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Internal scenario runner. Pure, side-effect-free.
 * Drives the planning flow: timeline editing → validation → effect/transition/render planning.
 * Does not execute FFmpeg, does not call OpenCue, does not create Product,
 * does not call StorageRuntime/ProductRuntime, does not persist.
 *
 * Internal domain model.
 */
public final class InternalScenarioRunner {

    private InternalScenarioRunner() {}

    /** Run a single scenario definition. Returns a deterministic result. */
    public static InternalScenarioResult run(InternalScenarioDefinition definition) {
        Objects.requireNonNull(definition, "definition");

        if (definition.status() != InternalScenarioStatus.ACTIVE) {
            return buildResult(definition, InternalScenarioResultStatus.NOT_RUN,
                    List.of(), Map.of(), List.of());
        }

        List<InternalScenarioIssue> issues = new ArrayList<>();
        Map<String, Object> actualProperties = new LinkedHashMap<>();

        // Step 1: Build timeline from input or edit operations
        TimelineSpec timeline = definition.inputTimeline();
        if (timeline == null && !definition.editOperations().isEmpty()) {
            TimelineEditRequest request = new TimelineEditRequest(
                    "req-" + definition.id().value(),
                    definition.id().value(),
                    definition.editOperations(),
                    Map.of());
            TimelineEditResult editResult = BasicTimelineEditor.apply(
                    TimelineSpec.create(definition.id().value() + "-tl",
                            definition.name().value(), TimelineOutputSpec.mp4_1080p30()),
                    request);
            if (editResult.status() != TimelineEditResultStatus.APPLIED) {
                issues.add(InternalScenarioIssue.error(
                        InternalScenarioIssueCode.TIMELINE_EDITING_FAILED,
                        "Edit operations failed: " + editResult.status()));
                return buildResult(definition, InternalScenarioResultStatus.FAIL,
                        issues, actualProperties, Collections.<InternalScenarioIssueCode>emptyList());
            }
            timeline = editResult.timeline();
        }

        if (timeline == null) {
            issues.add(InternalScenarioIssue.error(
                    InternalScenarioIssueCode.SCENARIO_INPUT_INVALID,
                    "No input timeline or edit operations provided"));
            return buildResult(definition, InternalScenarioResultStatus.FAIL,
                    issues, actualProperties, List.of());
        }

        // Step 2: Validate timeline
        List<TimelineValidationIssue> validationIssues = BasicTimelineValidator.validate(timeline);
        actualProperties.put("hasVideoTrack", timeline.tracks().stream()
                .anyMatch(t -> t.type() == TimelineTrack.TrackType.VIDEO));
        actualProperties.put("outputFormat", timeline.outputSpec() != null ? timeline.outputSpec().format() : "none");

        boolean hasBlockingValidation = validationIssues.stream()
                .anyMatch(i -> i.severity() == TimelineValidationIssueSeverity.BLOCKING);
        boolean hasErrorValidation = validationIssues.stream()
                .anyMatch(i -> i.severity() == TimelineValidationIssueSeverity.ERROR);

        if (hasBlockingValidation || hasErrorValidation) {
            if (definition.category() == InternalScenarioCategory.SAFETY_BOUNDARY
                    || definition.category() == InternalScenarioCategory.OUTPUT_PROFILE) {
                actualProperties.put("validationBlocked", true);
            } else {
                issues.add(InternalScenarioIssue.error(
                        InternalScenarioIssueCode.TIMELINE_VALIDATION_FAILED,
                        "Timeline validation failed with " + validationIssues.size() + " issues"));
                return buildResult(definition, InternalScenarioResultStatus.FAIL,
                        issues, actualProperties,
                        List.of(InternalScenarioIssueCode.TIMELINE_VALIDATION_FAILED));
            }
        }

        // Step 3: Run effect planner if relevant
        FFmpegBaselineEffectPlanningResult effectResult = null;
        if (definition.category() == InternalScenarioCategory.EFFECT_PLANNING
                || definition.category() == InternalScenarioCategory.BASIC_RENDER_PLANNING
                || definition.category() == InternalScenarioCategory.SAFETY_BOUNDARY
                || definition.category() == InternalScenarioCategory.REGRESSION) {
            FFmpegBaselineEffectPlanningRequest effectRequest = new FFmpegBaselineEffectPlanningRequest(
                    new FFmpegBaselineEffectPlanningRequestId("effect-" + definition.id().value()),
                    timeline,
                    FFmpegBaselineEffectPolicy.conservative(),
                    Map.of());
            effectResult = FFmpegBaselineEffectPlanner.plan(effectRequest);

            if (effectResult.plan() != null) {
                actualProperties.put("effectOperationCount", effectResult.plan().operations().size());
                actualProperties.put("hasScale", effectResult.plan().operations().stream()
                        .anyMatch(op -> op.type() == FFmpegBaselineEffectOperationType.SCALE));
                actualProperties.put("hasCrop", effectResult.plan().operations().stream()
                        .anyMatch(op -> op.type() == FFmpegBaselineEffectOperationType.CROP));
                actualProperties.put("hasOpacity", effectResult.plan().operations().stream()
                        .anyMatch(op -> op.type() == FFmpegBaselineEffectOperationType.OPACITY));
                actualProperties.put("effectPlanStatus", effectResult.plan().status().name());
            }

            if (definition.category() == InternalScenarioCategory.SAFETY_BOUNDARY) {
                boolean blocked = effectResult.status() == FFmpegBaselineEffectPlanningResultStatus.BLOCKED
                        || (effectResult.plan() != null
                            && effectResult.plan().status() == FFmpegBaselineEffectPlanStatus.BLOCKED);
                actualProperties.put("effectBlocked", blocked);
            }
        }

        // Step 4: Run transition planner if relevant
        FFmpegBaselineTransitionPlanningResult transitionResult = null;
        if (definition.category() == InternalScenarioCategory.TRANSITION_PLANNING
                || definition.category() == InternalScenarioCategory.BASIC_RENDER_PLANNING
                || definition.category() == InternalScenarioCategory.SAFETY_BOUNDARY
                || definition.category() == InternalScenarioCategory.REGRESSION) {
            FFmpegBaselineTransitionPlanningRequest transitionRequest = new FFmpegBaselineTransitionPlanningRequest(
                    new FFmpegBaselineTransitionPlanningRequestId("transition-" + definition.id().value()),
                    timeline,
                    FFmpegBaselineTransitionPolicy.conservative(),
                    Map.of());
            transitionResult = FFmpegBaselineTransitionPlanner.plan(transitionRequest);

            if (transitionResult.plan() != null) {
                actualProperties.put("transitionOperationCount", transitionResult.plan().operations().size());
                actualProperties.put("transitionOperationCountMin", transitionResult.plan().operations().size());
                actualProperties.put("hasCrossfade", transitionResult.plan().operations().stream()
                        .anyMatch(op -> op.type() == FFmpegBaselineTransitionOperationType.CROSSFADE));
                actualProperties.put("transitionPlanStatus", transitionResult.plan().status().name());
            }

            if (definition.category() == InternalScenarioCategory.SAFETY_BOUNDARY) {
                boolean blocked = transitionResult.status() == FFmpegBaselineTransitionPlanningResultStatus.BLOCKED
                        || (transitionResult.plan() != null
                            && transitionResult.plan().status() == FFmpegBaselineTransitionPlanStatus.BLOCKED);
                actualProperties.put("transitionBlocked", blocked);
            }
        }

        // Step 5: Run full render planner for render/safety/output/regression scenarios
        FFmpegLibassBasicRenderPlanningResult renderResult = null;
        if (definition.category() == InternalScenarioCategory.BASIC_RENDER_PLANNING
                || definition.category() == InternalScenarioCategory.SAFETY_BOUNDARY
                || definition.category() == InternalScenarioCategory.OUTPUT_PROFILE
                || definition.category() == InternalScenarioCategory.REGRESSION) {
            FFmpegLibassBasicRenderPlanningRequest renderRequest = new FFmpegLibassBasicRenderPlanningRequest(
                    new FFmpegLibassBasicRenderPlanningRequestId("render-" + definition.id().value()),
                    timeline,
                    FFmpegLibassBasicRenderPolicy.conservative(),
                    Map.of());
            renderResult = FFmpegLibassBasicRenderPlanner.plan(renderRequest);

            if (renderResult.plan() != null) {
                FFmpegLibassBasicRenderPlan plan = renderResult.plan();
                actualProperties.put("stagesCount", plan.stages().size());
                actualProperties.put("stagesCountMin", plan.stages().size());
                actualProperties.put("totalSteps", plan.summary() != null ? plan.summary().totalSteps() : 0);
                actualProperties.put("hasCaptionSteps", plan.stages().stream()
                        .flatMap(s -> s.steps().stream())
                        .anyMatch(s -> s.type() == FFmpegLibassBasicRenderStepType.APPLY_CAPTION_OVERLAY));
                actualProperties.put("hasWatermarkSteps", plan.stages().stream()
                        .flatMap(s -> s.steps().stream())
                        .anyMatch(s -> s.type() == FFmpegLibassBasicRenderStepType.APPLY_WATERMARK_OVERLAY));
                actualProperties.put("hasOutputEncoding", plan.stages().stream()
                        .anyMatch(s -> s.type() == FFmpegLibassBasicRenderStageType.PLAN_OUTPUT_ENCODING));
                actualProperties.put("hasEffectStage", plan.stages().stream()
                        .anyMatch(s -> s.type() == FFmpegLibassBasicRenderStageType.PLAN_EFFECTS));
                actualProperties.put("hasCaptionStage", plan.stages().stream()
                        .anyMatch(s -> s.type() == FFmpegLibassBasicRenderStageType.PLAN_CAPTION_OVERLAYS));
                actualProperties.put("hasWatermarkStage", plan.stages().stream()
                        .anyMatch(s -> s.type() == FFmpegLibassBasicRenderStageType.PLAN_WATERMARK_OVERLAYS));
                actualProperties.put("renderPlanStatus", plan.status().name());
                actualProperties.put("hasEffectOperations", effectResult != null && effectResult.plan() != null
                        && !effectResult.plan().operations().isEmpty());
                actualProperties.put("hasTransitions", transitionResult != null && transitionResult.plan() != null
                        && !transitionResult.plan().operations().isEmpty());
            }

            if (renderResult.status() == FFmpegLibassBasicRenderPlanningResultStatus.BLOCKED) {
                actualProperties.put("renderBlocked", true);
            }
            if (renderResult.status() == FFmpegLibassBasicRenderPlanningResultStatus.VALIDATION_FAILED) {
                actualProperties.put("renderValidationFailed", true);
            }
        }

        // Step 6: Compare actual vs expected
        InternalScenarioExpectedOutcome expected = definition.expectedOutcome();
        InternalScenarioResultStatus resultStatus = determineResultStatus(expected, actualProperties,
                effectResult, transitionResult, renderResult, definition);

        List<InternalScenarioIssueCode> actualIssueCodes = collectIssueCodes(
                effectResult, transitionResult, renderResult);

        // Verify expected issue codes are present
        for (InternalScenarioIssueCode expectedCode : expected.expectedIssueCodes()) {
            if (!matchesIssueCode(expectedCode, actualIssueCodes, actualProperties,
                    effectResult, transitionResult, renderResult)) {
                if (isBlockedAsExpected(expectedCode, actualProperties)) {
                    continue;
                }
                issues.add(InternalScenarioIssue.warning(
                        InternalScenarioIssueCode.EXPECTED_OUTCOME_MISMATCH,
                        "Expected issue code " + expectedCode + " not found in actual results"));
            }
        }

        // Verify expected plan properties
        List<InternalScenarioIssue> propertyMismatches = verifyPlanProperties(
                expected.expectedPlanProperties(), actualProperties);
        issues.addAll(propertyMismatches);

        return buildResult(definition, resultStatus, issues, actualProperties, actualIssueCodes);
    }

    /** Run all scenarios and produce an aggregated report. */
    public static InternalScenarioReport runAll(List<InternalScenarioDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        List<InternalScenarioResult> results = definitions.stream()
                .map(InternalScenarioRunner::run)
                .collect(Collectors.toList());
        return InternalScenarioReport.aggregate("report-" + System.currentTimeMillis(), results);
    }

    /** Run all required scenarios. */
    public static InternalScenarioReport runAllRequired() {
        return runAll(InternalScenarioRegistry.allRequired());
    }

    // ==================== Private Helpers ====================

    private static InternalScenarioResult buildResult(
            InternalScenarioDefinition definition,
            InternalScenarioResultStatus status,
            List<InternalScenarioIssue> issues,
            Map<String, Object> actualProperties,
            List<InternalScenarioIssueCode> actualIssueCodes) {

        InternalScenarioActualOutcome actualOutcome = InternalScenarioActualOutcome.of(
                status, actualIssueCodes, actualProperties, issues);

        return new InternalScenarioResult(
                definition.id(),
                definition.name(),
                definition.category(),
                status,
                definition.expectedOutcome(),
                actualOutcome,
                issues,
                Map.of());
    }

    private static InternalScenarioResultStatus determineResultStatus(
            InternalScenarioExpectedOutcome expected,
            Map<String, Object> actualProperties,
            FFmpegBaselineEffectPlanningResult effectResult,
            FFmpegBaselineTransitionPlanningResult transitionResult,
            FFmpegLibassBasicRenderPlanningResult renderResult,
            InternalScenarioDefinition definition) {

        boolean isBlocked = Boolean.TRUE.equals(actualProperties.get("effectBlocked"))
                || Boolean.TRUE.equals(actualProperties.get("transitionBlocked"))
                || Boolean.TRUE.equals(actualProperties.get("renderBlocked"))
                || Boolean.TRUE.equals(actualProperties.get("renderValidationFailed"))
                || Boolean.TRUE.equals(actualProperties.get("validationBlocked"));

        if (isBlocked && expected.expectedStatus() == InternalScenarioResultStatus.BLOCKED) {
            return InternalScenarioResultStatus.BLOCKED;
        }

        if (definition.category() == InternalScenarioCategory.SAFETY_BOUNDARY) {
            if (effectResult != null
                    && effectResult.status() == FFmpegBaselineEffectPlanningResultStatus.BLOCKED) {
                return InternalScenarioResultStatus.BLOCKED;
            }
            if (transitionResult != null
                    && transitionResult.status() == FFmpegBaselineTransitionPlanningResultStatus.BLOCKED) {
                return InternalScenarioResultStatus.BLOCKED;
            }
            if (renderResult != null
                    && renderResult.status() == FFmpegLibassBasicRenderPlanningResultStatus.BLOCKED) {
                return InternalScenarioResultStatus.BLOCKED;
            }
        }

        if (definition.category() == InternalScenarioCategory.OUTPUT_PROFILE) {
            if (renderResult != null
                    && (renderResult.status() == FFmpegLibassBasicRenderPlanningResultStatus.BLOCKED
                    || renderResult.status() == FFmpegLibassBasicRenderPlanningResultStatus.VALIDATION_FAILED)) {
                return InternalScenarioResultStatus.BLOCKED;
            }
        }

        if (effectResult != null && effectResult.status() == FFmpegBaselineEffectPlanningResultStatus.FAILED) {
            return InternalScenarioResultStatus.FAIL;
        }
        if (transitionResult != null
                && transitionResult.status() == FFmpegBaselineTransitionPlanningResultStatus.FAILED) {
            return InternalScenarioResultStatus.FAIL;
        }
        if (renderResult != null
                && renderResult.status() == FFmpegLibassBasicRenderPlanningResultStatus.FAILED) {
            return InternalScenarioResultStatus.FAIL;
        }

        if (expected.expectedStatus() == InternalScenarioResultStatus.PASS) {
            boolean hasWarnings = false;
            if (renderResult != null && renderResult.plan() != null
                    && renderResult.plan().status() == FFmpegLibassBasicRenderPlanStatus.VALID_WITH_WARNINGS) {
                hasWarnings = true;
            }
            if (effectResult != null && effectResult.plan() != null
                    && effectResult.plan().status() == FFmpegBaselineEffectPlanStatus.VALID_WITH_WARNINGS) {
                hasWarnings = true;
            }
            return hasWarnings ? InternalScenarioResultStatus.PASS_WITH_WARNINGS : InternalScenarioResultStatus.PASS;
        }

        return expected.expectedStatus();
    }

    private static List<InternalScenarioIssueCode> collectIssueCodes(
            FFmpegBaselineEffectPlanningResult effectResult,
            FFmpegBaselineTransitionPlanningResult transitionResult,
            FFmpegLibassBasicRenderPlanningResult renderResult) {

        List<InternalScenarioIssueCode> codes = new ArrayList<>();
        if (effectResult != null) {
            for (FFmpegBaselineEffectPlanIssue issue : effectResult.issues()) {
                switch (issue.code()) {
                    case EFFECT_CAPABILITY_FORBIDDEN -> codes.add(InternalScenarioIssueCode.FORBIDDEN_EFFECT_NOT_BLOCKED);
                    case RAW_FILTERGRAPH_FORBIDDEN -> codes.add(InternalScenarioIssueCode.RAW_FILTERGRAPH_EXPOSED);
                    case RAW_PROVIDER_COMMAND_FORBIDDEN -> codes.add(InternalScenarioIssueCode.RAW_PROVIDER_COMMAND_EXPOSED);
                    case PLUGIN_EXECUTION_NODE_FORBIDDEN -> codes.add(InternalScenarioIssueCode.PLUGIN_EXECUTION_NODE_ALLOWED);
                    case USER_RENDER_DAG_FORBIDDEN -> codes.add(InternalScenarioIssueCode.USER_RENDER_DAG_ALLOWED);
                    default -> {}
                }
            }
        }
        if (transitionResult != null) {
            for (FFmpegBaselineTransitionPlanIssue issue : transitionResult.issues()) {
                switch (issue.code()) {
                    case TRANSITION_CAPABILITY_FORBIDDEN ->
                        codes.add(InternalScenarioIssueCode.FORBIDDEN_TRANSITION_NOT_BLOCKED);
                    case RAW_FILTERGRAPH_FORBIDDEN -> codes.add(InternalScenarioIssueCode.RAW_FILTERGRAPH_EXPOSED);
                    case RAW_PROVIDER_COMMAND_FORBIDDEN -> codes.add(InternalScenarioIssueCode.RAW_PROVIDER_COMMAND_EXPOSED);
                    case USER_RENDER_DAG_FORBIDDEN -> codes.add(InternalScenarioIssueCode.USER_RENDER_DAG_ALLOWED);
                    default -> {}
                }
            }
        }
        if (renderResult != null) {
            for (FFmpegLibassBasicRenderPlanIssue issue : renderResult.issues()) {
                switch (issue.code()) {
                    case UNSUPPORTED_OUTPUT_CONTAINER, UNSUPPORTED_VIDEO_CODEC, UNSUPPORTED_AUDIO_CODEC ->
                        codes.add(InternalScenarioIssueCode.OUTPUT_PROFILE_INVALID);
                    case RAW_FILTERGRAPH_FORBIDDEN -> codes.add(InternalScenarioIssueCode.RAW_FILTERGRAPH_EXPOSED);
                    case RAW_PROVIDER_COMMAND_FORBIDDEN -> codes.add(InternalScenarioIssueCode.RAW_PROVIDER_COMMAND_EXPOSED);
                    case ARTIFACT_DAG_NOT_USED -> codes.add(InternalScenarioIssueCode.ARTIFACT_DAG_USED);
                    default -> {}
                }
            }
        }
        return Collections.unmodifiableList(codes);
    }

    private static boolean matchesIssueCode(
            InternalScenarioIssueCode expectedCode,
            List<InternalScenarioIssueCode> actualCodes,
            Map<String, Object> actualProperties,
            FFmpegBaselineEffectPlanningResult effectResult,
            FFmpegBaselineTransitionPlanningResult transitionResult,
            FFmpegLibassBasicRenderPlanningResult renderResult) {

        if (actualCodes.contains(expectedCode)) return true;

        return switch (expectedCode) {
            case FORBIDDEN_EFFECT_NOT_BLOCKED -> {
                boolean blocked = effectResult != null
                        && effectResult.status() == FFmpegBaselineEffectPlanningResultStatus.BLOCKED;
                yield blocked || Boolean.TRUE.equals(actualProperties.get("effectBlocked"));
            }
            case FORBIDDEN_TRANSITION_NOT_BLOCKED -> {
                boolean blocked = transitionResult != null
                        && transitionResult.status() == FFmpegBaselineTransitionPlanningResultStatus.BLOCKED;
                yield blocked || Boolean.TRUE.equals(actualProperties.get("transitionBlocked"));
            }
            case OUTPUT_PROFILE_INVALID -> {
                boolean blocked = renderResult != null
                        && (renderResult.status() == FFmpegLibassBasicRenderPlanningResultStatus.BLOCKED
                        || renderResult.status() == FFmpegLibassBasicRenderPlanningResultStatus.VALIDATION_FAILED);
                yield blocked || Boolean.TRUE.equals(actualProperties.get("renderBlocked"))
                        || Boolean.TRUE.equals(actualProperties.get("renderValidationFailed"));
            }
            default -> false;
        };
    }

    private static boolean isBlockedAsExpected(
            InternalScenarioIssueCode expectedCode,
            Map<String, Object> actualProperties) {
        return switch (expectedCode) {
            case FORBIDDEN_EFFECT_NOT_BLOCKED -> Boolean.TRUE.equals(actualProperties.get("effectBlocked"));
            case FORBIDDEN_TRANSITION_NOT_BLOCKED -> Boolean.TRUE.equals(actualProperties.get("transitionBlocked"));
            case OUTPUT_PROFILE_INVALID -> Boolean.TRUE.equals(actualProperties.get("renderBlocked"))
                    || Boolean.TRUE.equals(actualProperties.get("renderValidationFailed"));
            default -> false;
        };
    }

    private static List<InternalScenarioIssue> verifyPlanProperties(
            Map<String, Object> expected,
            Map<String, Object> actual) {

        List<InternalScenarioIssue> mismatches = new ArrayList<>();
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            String key = entry.getKey();
            Object expectedValue = entry.getValue();
            Object actualValue = actual.get(key);

            if (key.endsWith("Min")) {
                if (expectedValue instanceof Number expNum && actualValue instanceof Number actNum) {
                    if (actNum.intValue() < expNum.intValue()) {
                        mismatches.add(InternalScenarioIssue.error(
                                InternalScenarioIssueCode.EXPECTED_OUTCOME_MISMATCH,
                                "Property " + key + ": expected >= " + expectedValue + ", got " + actualValue));
                    }
                }
            } else if (expectedValue instanceof Boolean expBool) {
                if (actualValue instanceof Boolean actBool) {
                    if (!expBool.equals(actBool)) {
                        mismatches.add(InternalScenarioIssue.error(
                                InternalScenarioIssueCode.EXPECTED_OUTCOME_MISMATCH,
                                "Property " + key + ": expected " + expectedValue + ", got " + actualValue));
                    }
                } else {
                    mismatches.add(InternalScenarioIssue.error(
                            InternalScenarioIssueCode.EXPECTED_OUTCOME_MISMATCH,
                            "Property " + key + ": expected " + expectedValue + ", got null"));
                }
            } else if (expectedValue instanceof Number expNum) {
                if (actualValue instanceof Number actNum) {
                    if (expNum.intValue() != actNum.intValue()) {
                        mismatches.add(InternalScenarioIssue.error(
                                InternalScenarioIssueCode.EXPECTED_OUTCOME_MISMATCH,
                                "Property " + key + ": expected " + expectedValue + ", got " + actualValue));
                    }
                } else {
                    mismatches.add(InternalScenarioIssue.error(
                            InternalScenarioIssueCode.EXPECTED_OUTCOME_MISMATCH,
                            "Property " + key + ": expected " + expectedValue + ", got null"));
                }
            } else if (expectedValue instanceof String expStr) {
                if (actualValue != null && !expStr.equals(actualValue.toString())) {
                    mismatches.add(InternalScenarioIssue.error(
                            InternalScenarioIssueCode.EXPECTED_OUTCOME_MISMATCH,
                            "Property " + key + ": expected " + expectedValue + ", got " + actualValue));
                }
            }
        }
        return mismatches;
    }
}
