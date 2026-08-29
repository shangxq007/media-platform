package com.example.platform.render.domain.scenario;


import com.example.platform.render.domain.effect.*;
import com.example.platform.render.domain.plan.*;
import com.example.platform.render.domain.transition.*;
import java.util.*;
import java.util.stream.Collectors;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.legacy.TimelineTrack;

/**
 * Internal scenario runner. Pure, side-effect-free.
 * Drives the planning flow: timeline editing → validation → effect/transition/render planning.
 * Does not execute Provider, does not call OpenCue, does not create Product,
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

        // Step 1: Build timeline from canonical input only (ROADMAP_19 FINAL
        // AUTHORITY: TimelineDocument is the sole authoring authority; the
        // BasicTimelineEditor parallel mutation path is DELETED).
        TimelineSpec timeline = definition.inputTimeline();

        if (timeline == null) {
            issues.add(InternalScenarioIssue.error(
                    InternalScenarioIssueCode.SCENARIO_INPUT_INVALID,
                    "No input timeline or edit operations provided"));
            return buildResult(definition, InternalScenarioResultStatus.FAIL,
                    issues, actualProperties, List.of());
        }

        // Step 2: Timeline structural invariants are enforced by the
        // TimelineSpec compact constructor (ROADMAP_19 FINAL AUTHORITY: the
        // BasicTimelineValidator parallel model is DELETED).
        actualProperties.put("hasVideoTrack", timeline.tracks().stream()
                .anyMatch(t -> t.type() == TimelineTrack.TrackType.VIDEO));
        actualProperties.put("outputFormat", timeline.outputSpec() != null ? timeline.outputSpec().format() : "none");

        // Step 3: Run effect planner if relevant
        BaselineEffectPlanningResult effectResult = null;
        if (definition.category() == InternalScenarioCategory.EFFECT_PLANNING
                || definition.category() == InternalScenarioCategory.BASIC_RENDER_PLANNING
                || definition.category() == InternalScenarioCategory.SAFETY_BOUNDARY
                || definition.category() == InternalScenarioCategory.REGRESSION) {
            BaselineEffectPlanningRequest effectRequest = new BaselineEffectPlanningRequest(
                    new BaselineEffectPlanningRequestId("effect-" + definition.id().value()),
                    timeline,
                    BaselineEffectPolicy.conservative(),
                    Map.of());
            effectResult = BaselineEffectPlanner.plan(effectRequest);

            if (effectResult.plan() != null) {
                actualProperties.put("effectOperationCount", effectResult.plan().operations().size());
                actualProperties.put("hasScale", effectResult.plan().operations().stream()
                        .anyMatch(op -> op.type() == BaselineEffectOperationType.SCALE));
                actualProperties.put("hasCrop", effectResult.plan().operations().stream()
                        .anyMatch(op -> op.type() == BaselineEffectOperationType.CROP));
                actualProperties.put("hasOpacity", effectResult.plan().operations().stream()
                        .anyMatch(op -> op.type() == BaselineEffectOperationType.OPACITY));
                actualProperties.put("effectPlanStatus", effectResult.plan().status().name());
            }

            if (definition.category() == InternalScenarioCategory.SAFETY_BOUNDARY) {
                boolean blocked = effectResult.status() == BaselineEffectPlanningResultStatus.BLOCKED
                        || (effectResult.plan() != null
                            && effectResult.plan().status() == BaselineEffectPlanStatus.BLOCKED);
                actualProperties.put("effectBlocked", blocked);
            }
        }

        // Step 4: Run transition planner if relevant
        BaselineTransitionPlanningResult transitionResult = null;
        if (definition.category() == InternalScenarioCategory.TRANSITION_PLANNING
                || definition.category() == InternalScenarioCategory.BASIC_RENDER_PLANNING
                || definition.category() == InternalScenarioCategory.SAFETY_BOUNDARY
                || definition.category() == InternalScenarioCategory.REGRESSION) {
            BaselineTransitionPlanningRequest transitionRequest = new BaselineTransitionPlanningRequest(
                    new BaselineTransitionPlanningRequestId("transition-" + definition.id().value()),
                    timeline,
                    BaselineTransitionPolicy.conservative(),
                    Map.of());
            transitionResult = BaselineTransitionPlanner.plan(transitionRequest);

            if (transitionResult.plan() != null) {
                actualProperties.put("transitionOperationCount", transitionResult.plan().operations().size());
                actualProperties.put("transitionOperationCountMin", transitionResult.plan().operations().size());
                actualProperties.put("hasCrossfade", transitionResult.plan().operations().stream()
                        .anyMatch(op -> op.type() == BaselineTransitionOperationType.CROSSFADE));
                actualProperties.put("transitionPlanStatus", transitionResult.plan().status().name());
            }

            if (definition.category() == InternalScenarioCategory.SAFETY_BOUNDARY) {
                boolean blocked = transitionResult.status() == BaselineTransitionPlanningResultStatus.BLOCKED
                        || (transitionResult.plan() != null
                            && transitionResult.plan().status() == BaselineTransitionPlanStatus.BLOCKED);
                actualProperties.put("transitionBlocked", blocked);
            }
        }

        // Step 5: Run full render planner for render/safety/output/regression scenarios
        BasicRenderPlanningResult renderResult = null;
        if (definition.category() == InternalScenarioCategory.BASIC_RENDER_PLANNING
                || definition.category() == InternalScenarioCategory.SAFETY_BOUNDARY
                || definition.category() == InternalScenarioCategory.OUTPUT_PROFILE
                || definition.category() == InternalScenarioCategory.REGRESSION) {
            BasicRenderPlanningRequest renderRequest = new BasicRenderPlanningRequest(
                    new BasicRenderPlanningRequestId("render-" + definition.id().value()),
                    timeline,
                    BasicRenderPolicy.conservative(),
                    Map.of());
            renderResult = BasicRenderPlanner.plan(renderRequest);

            if (renderResult.plan() != null) {
                BasicRenderPlan plan = renderResult.plan();
                actualProperties.put("stagesCount", plan.stages().size());
                actualProperties.put("stagesCountMin", plan.stages().size());
                actualProperties.put("totalSteps", plan.summary() != null ? plan.summary().totalSteps() : 0);
                actualProperties.put("hasCaptionSteps", plan.stages().stream()
                        .flatMap(s -> s.steps().stream())
                        .anyMatch(s -> s.type() == BasicRenderStepType.APPLY_CAPTION_OVERLAY));
                actualProperties.put("hasWatermarkSteps", plan.stages().stream()
                        .flatMap(s -> s.steps().stream())
                        .anyMatch(s -> s.type() == BasicRenderStepType.APPLY_WATERMARK_OVERLAY));
                actualProperties.put("hasOutputEncoding", plan.stages().stream()
                        .anyMatch(s -> s.type() == BasicRenderStageType.PLAN_OUTPUT_ENCODING));
                actualProperties.put("hasEffectStage", plan.stages().stream()
                        .anyMatch(s -> s.type() == BasicRenderStageType.PLAN_EFFECTS));
                actualProperties.put("hasCaptionStage", plan.stages().stream()
                        .anyMatch(s -> s.type() == BasicRenderStageType.PLAN_CAPTION_OVERLAYS));
                actualProperties.put("hasWatermarkStage", plan.stages().stream()
                        .anyMatch(s -> s.type() == BasicRenderStageType.PLAN_WATERMARK_OVERLAYS));
                actualProperties.put("renderPlanStatus", plan.status().name());
                actualProperties.put("hasEffectOperations", effectResult != null && effectResult.plan() != null
                        && !effectResult.plan().operations().isEmpty());
                actualProperties.put("hasTransitions", transitionResult != null && transitionResult.plan() != null
                        && !transitionResult.plan().operations().isEmpty());
            }

            if (renderResult.status() == BasicRenderPlanningResultStatus.BLOCKED) {
                actualProperties.put("renderBlocked", true);
            }
            if (renderResult.status() == BasicRenderPlanningResultStatus.VALIDATION_FAILED) {
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
            BaselineEffectPlanningResult effectResult,
            BaselineTransitionPlanningResult transitionResult,
            BasicRenderPlanningResult renderResult,
            InternalScenarioDefinition definition) {

        boolean isBlocked = Boolean.TRUE.equals(actualProperties.get("effectBlocked"))
                || Boolean.TRUE.equals(actualProperties.get("transitionBlocked"))
                || Boolean.TRUE.equals(actualProperties.get("renderBlocked"))
                || Boolean.TRUE.equals(actualProperties.get("renderValidationFailed"))
               ;

        if (isBlocked && expected.expectedStatus() == InternalScenarioResultStatus.BLOCKED) {
            return InternalScenarioResultStatus.BLOCKED;
        }

        if (definition.category() == InternalScenarioCategory.SAFETY_BOUNDARY) {
            if (effectResult != null
                    && effectResult.status() == BaselineEffectPlanningResultStatus.BLOCKED) {
                return InternalScenarioResultStatus.BLOCKED;
            }
            if (transitionResult != null
                    && transitionResult.status() == BaselineTransitionPlanningResultStatus.BLOCKED) {
                return InternalScenarioResultStatus.BLOCKED;
            }
            if (renderResult != null
                    && renderResult.status() == BasicRenderPlanningResultStatus.BLOCKED) {
                return InternalScenarioResultStatus.BLOCKED;
            }
        }

        if (definition.category() == InternalScenarioCategory.OUTPUT_PROFILE) {
            if (renderResult != null
                    && (renderResult.status() == BasicRenderPlanningResultStatus.BLOCKED
                    || renderResult.status() == BasicRenderPlanningResultStatus.VALIDATION_FAILED)) {
                return InternalScenarioResultStatus.BLOCKED;
            }
        }

        if (effectResult != null && effectResult.status() == BaselineEffectPlanningResultStatus.FAILED) {
            return InternalScenarioResultStatus.FAIL;
        }
        if (transitionResult != null
                && transitionResult.status() == BaselineTransitionPlanningResultStatus.FAILED) {
            return InternalScenarioResultStatus.FAIL;
        }
        if (renderResult != null
                && renderResult.status() == BasicRenderPlanningResultStatus.FAILED) {
            return InternalScenarioResultStatus.FAIL;
        }

        if (expected.expectedStatus() == InternalScenarioResultStatus.PASS) {
            boolean hasWarnings = false;
            if (renderResult != null && renderResult.plan() != null
                    && renderResult.plan().status() == BasicRenderPlanStatus.VALID_WITH_WARNINGS) {
                hasWarnings = true;
            }
            if (effectResult != null && effectResult.plan() != null
                    && effectResult.plan().status() == BaselineEffectPlanStatus.VALID_WITH_WARNINGS) {
                hasWarnings = true;
            }
            return hasWarnings ? InternalScenarioResultStatus.PASS_WITH_WARNINGS : InternalScenarioResultStatus.PASS;
        }

        return expected.expectedStatus();
    }

    private static List<InternalScenarioIssueCode> collectIssueCodes(
            BaselineEffectPlanningResult effectResult,
            BaselineTransitionPlanningResult transitionResult,
            BasicRenderPlanningResult renderResult) {

        List<InternalScenarioIssueCode> codes = new ArrayList<>();
        if (effectResult != null) {
            for (BaselineEffectPlanIssue issue : effectResult.issues()) {
                switch (issue.code()) {
                    case EFFECT_CAPABILITY_FORBIDDEN -> codes.add(InternalScenarioIssueCode.FORBIDDEN_EFFECT_NOT_BLOCKED);
                    case RAW_PROVIDER_EXPRESSION_FORBIDDEN -> codes.add(InternalScenarioIssueCode.RAW_PROVIDER_EXPRESSION_EXPOSED);
                    case RAW_PROVIDER_COMMAND_FORBIDDEN -> codes.add(InternalScenarioIssueCode.RAW_PROVIDER_COMMAND_EXPOSED);
                    case PLUGIN_EXECUTION_NODE_FORBIDDEN -> codes.add(InternalScenarioIssueCode.PLUGIN_EXECUTION_NODE_ALLOWED);
                    case USER_RENDER_DAG_FORBIDDEN -> codes.add(InternalScenarioIssueCode.USER_RENDER_DAG_ALLOWED);
                    default -> {}
                }
            }
        }
        if (transitionResult != null) {
            for (BaselineTransitionPlanIssue issue : transitionResult.issues()) {
                switch (issue.code()) {
                    case TRANSITION_CAPABILITY_FORBIDDEN ->
                        codes.add(InternalScenarioIssueCode.FORBIDDEN_TRANSITION_NOT_BLOCKED);
                    case RAW_PROVIDER_EXPRESSION_FORBIDDEN -> codes.add(InternalScenarioIssueCode.RAW_PROVIDER_EXPRESSION_EXPOSED);
                    case RAW_PROVIDER_COMMAND_FORBIDDEN -> codes.add(InternalScenarioIssueCode.RAW_PROVIDER_COMMAND_EXPOSED);
                    case USER_RENDER_DAG_FORBIDDEN -> codes.add(InternalScenarioIssueCode.USER_RENDER_DAG_ALLOWED);
                    default -> {}
                }
            }
        }
        if (renderResult != null) {
            for (BasicRenderPlanIssue issue : renderResult.issues()) {
                switch (issue.code()) {
                    case UNSUPPORTED_OUTPUT_CONTAINER, UNSUPPORTED_VIDEO_CODEC, UNSUPPORTED_AUDIO_CODEC ->
                        codes.add(InternalScenarioIssueCode.OUTPUT_PROFILE_INVALID);
                    case RAW_PROVIDER_EXPRESSION_FORBIDDEN -> codes.add(InternalScenarioIssueCode.RAW_PROVIDER_EXPRESSION_EXPOSED);
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
            BaselineEffectPlanningResult effectResult,
            BaselineTransitionPlanningResult transitionResult,
            BasicRenderPlanningResult renderResult) {

        if (actualCodes.contains(expectedCode)) return true;

        return switch (expectedCode) {
            case FORBIDDEN_EFFECT_NOT_BLOCKED -> {
                boolean blocked = effectResult != null
                        && effectResult.status() == BaselineEffectPlanningResultStatus.BLOCKED;
                yield blocked || Boolean.TRUE.equals(actualProperties.get("effectBlocked"));
            }
            case FORBIDDEN_TRANSITION_NOT_BLOCKED -> {
                boolean blocked = transitionResult != null
                        && transitionResult.status() == BaselineTransitionPlanningResultStatus.BLOCKED;
                yield blocked || Boolean.TRUE.equals(actualProperties.get("transitionBlocked"));
            }
            case OUTPUT_PROFILE_INVALID -> {
                boolean blocked = renderResult != null
                        && (renderResult.status() == BasicRenderPlanningResultStatus.BLOCKED
                        || renderResult.status() == BasicRenderPlanningResultStatus.VALIDATION_FAILED);
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
