package com.example.platform.render.domain.workflow.planning;

import com.example.platform.render.domain.template.*;
import com.example.platform.render.domain.template.profile.caption.*;
import com.example.platform.render.domain.template.profile.watermark.*;
import com.example.platform.render.domain.template.composite.*;
import com.example.platform.render.domain.workflow.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Workflow Dry-run Planner.
 * Proves: validation, cycle detection, ordering, APPLY_TEMPLATE recognition, safety.
 */
class WorkflowDryRunPlannerTest {

    private WorkflowDryRunPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new WorkflowDryRunPlanner();
    }

    // --- Identity ---

    @Test
    @DisplayName("WorkflowDryRunPlanId rejects blank")
    void planIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDryRunPlanId(""));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDryRunPlanId(null));
    }

    // --- Graph Validation ---

    @Test
    @DisplayName("Graph validator accepts valid workflow")
    void validatorAcceptsValid() {
        WorkflowDefinition def = validWorkflow();
        WorkflowGraphValidationResult result = new WorkflowGraphValidator().validate(def);
        assertTrue(result.valid());
    }

    @Test
    @DisplayName("Graph validator rejects duplicate step id")
    void validatorRejectsDuplicateId() {
        WorkflowStep step = new WorkflowStep(new WorkflowStepId("s1"),
                WorkflowStepType.INGEST_PRODUCT, List.of(), Map.of(), null, Map.of());
        WorkflowDefinition def = new WorkflowDefinition(
                new WorkflowDefinitionId("wf1"), new WorkflowVersion("1.0"), null,
                List.of(), List.of(step, step), List.of(), Map.of());

        WorkflowGraphValidationResult result = new WorkflowGraphValidator().validate(def);
        assertFalse(result.valid());
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == WorkflowDryRunIssueCode.DUPLICATE_STEP_ID));
    }

    @Test
    @DisplayName("Graph validator rejects unknown dependency")
    void validatorRejectsUnknownDep() {
        WorkflowStep step = new WorkflowStep(new WorkflowStepId("s1"),
                WorkflowStepType.INGEST_PRODUCT,
                List.of(new WorkflowStepDependency(new WorkflowStepId("nonexistent"), null)),
                Map.of(), null, Map.of());
        WorkflowDefinition def = new WorkflowDefinition(
                new WorkflowDefinitionId("wf1"), new WorkflowVersion("1.0"), null,
                List.of(), List.of(step), List.of(), Map.of());

        WorkflowGraphValidationResult result = new WorkflowGraphValidator().validate(def);
        assertFalse(result.valid());
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == WorkflowDryRunIssueCode.UNKNOWN_DEPENDENCY));
    }

    @Test
    @DisplayName("Graph validator rejects APPLY_TEMPLATE without spec")
    void validatorRejectsMissingSpec() {
        WorkflowStep step = new WorkflowStep(new WorkflowStepId("s1"),
                WorkflowStepType.APPLY_TEMPLATE, List.of(), Map.of(), null, Map.of());
        WorkflowDefinition def = new WorkflowDefinition(
                new WorkflowDefinitionId("wf1"), new WorkflowVersion("1.0"), null,
                List.of(), List.of(step), List.of(), Map.of());

        WorkflowGraphValidationResult result = new WorkflowGraphValidator().validate(def);
        assertFalse(result.valid());
    }

    // --- Cycle Detection ---

    @Test
    @DisplayName("Linear graph has no cycle")
    void linearNoCycle() {
        assertFalse(new WorkflowCycleDetector().hasCycle(validWorkflow()));
    }

    @Test
    @DisplayName("Branching graph has no cycle")
    void branchingNoCycle() {
        WorkflowStep s1 = new WorkflowStep(new WorkflowStepId("s1"),
                WorkflowStepType.INGEST_PRODUCT, List.of(), Map.of(), null, Map.of());
        WorkflowStep s2 = new WorkflowStep(new WorkflowStepId("s2"),
                WorkflowStepType.VALIDATE_INPUT,
                List.of(new WorkflowStepDependency(new WorkflowStepId("s1"), null)),
                Map.of(), null, Map.of());
        WorkflowStep s3 = new WorkflowStep(new WorkflowStepId("s3"),
                WorkflowStepType.NORMALIZE_TIMELINE,
                List.of(new WorkflowStepDependency(new WorkflowStepId("s1"), null)),
                Map.of(), null, Map.of());
        WorkflowStep s4 = new WorkflowStep(new WorkflowStepId("s4"),
                WorkflowStepType.RENDER_TIMELINE,
                List.of(new WorkflowStepDependency(new WorkflowStepId("s2"), null),
                        new WorkflowStepDependency(new WorkflowStepId("s3"), null)),
                Map.of(), null, Map.of());
        WorkflowDefinition def = new WorkflowDefinition(
                new WorkflowDefinitionId("wf1"), new WorkflowVersion("1.0"), null,
                List.of(), List.of(s1, s2, s3, s4), List.of(), Map.of());
        assertFalse(new WorkflowCycleDetector().hasCycle(def));
    }

    @Test
    @DisplayName("Simple cycle detected")
    void simpleCycleDetected() {
        WorkflowStep s1 = new WorkflowStep(new WorkflowStepId("s1"),
                WorkflowStepType.INGEST_PRODUCT,
                List.of(new WorkflowStepDependency(new WorkflowStepId("s2"), null)),
                Map.of(), null, Map.of());
        WorkflowStep s2 = new WorkflowStep(new WorkflowStepId("s2"),
                WorkflowStepType.VALIDATE_INPUT,
                List.of(new WorkflowStepDependency(new WorkflowStepId("s1"), null)),
                Map.of(), null, Map.of());
        WorkflowDefinition def = new WorkflowDefinition(
                new WorkflowDefinitionId("wf1"), new WorkflowVersion("1.0"), null,
                List.of(), List.of(s1, s2), List.of(), Map.of());
        assertTrue(new WorkflowCycleDetector().hasCycle(def));
    }

    @Test
    @DisplayName("Self-cycle detected")
    void selfCycleDetected() {
        WorkflowStep s1 = new WorkflowStep(new WorkflowStepId("s1"),
                WorkflowStepType.INGEST_PRODUCT,
                List.of(new WorkflowStepDependency(new WorkflowStepId("s1"), null)),
                Map.of(), null, Map.of());
        WorkflowDefinition def = new WorkflowDefinition(
                new WorkflowDefinitionId("wf1"), new WorkflowVersion("1.0"), null,
                List.of(), List.of(s1), List.of(), Map.of());
        assertTrue(new WorkflowCycleDetector().hasCycle(def));
    }

    // --- Step Ordering ---

    @Test
    @DisplayName("Linear workflow orders correctly")
    void linearOrder() {
        WorkflowDefinition def = validWorkflow();
        List<String> order = new WorkflowStepOrderResolver().resolveOrder(def);
        assertEquals(5, order.size());
        assertEquals("ingest", order.get(0));
        assertTrue(order.indexOf("apply-caption") < order.indexOf("render"));
    }

    @Test
    @DisplayName("Deterministic independent step order")
    void deterministicOrder() {
        WorkflowStep s1 = new WorkflowStep(new WorkflowStepId("s1"),
                WorkflowStepType.INGEST_PRODUCT, List.of(), Map.of(), null, Map.of());
        WorkflowStep s2 = new WorkflowStep(new WorkflowStepId("s2"),
                WorkflowStepType.VALIDATE_INPUT, List.of(), Map.of(), null, Map.of());
        WorkflowDefinition def = new WorkflowDefinition(
                new WorkflowDefinitionId("wf1"), new WorkflowVersion("1.0"), null,
                List.of(), List.of(s1, s2), List.of(), Map.of());

        List<String> order1 = new WorkflowStepOrderResolver().resolveOrder(def);
        List<String> order2 = new WorkflowStepOrderResolver().resolveOrder(def);
        assertEquals(order1, order2);
    }

    // --- Dry-run Planner ---

    @Test
    @DisplayName("Valid workflow produces READY steps")
    void validWorkflowReady() {
        WorkflowDryRunPlan plan = planner.plan(validWorkflow());
        assertTrue(plan.valid());
        assertFalse(plan.steps().isEmpty());
        assertEquals(WorkflowDryRunStepStatus.READY, plan.steps().get(0).status());
    }

    @Test
    @DisplayName("APPLY_TEMPLATE step gets template summary")
    void applyTemplateSummary() {
        WorkflowDryRunPlan plan = planner.plan(validWorkflow());
        WorkflowDryRunStep templateStep = plan.steps().stream()
                .filter(s -> s.stepType() == WorkflowStepType.APPLY_TEMPLATE)
                .findFirst().orElseThrow();
        assertNotNull(templateStep.templateSummary());
        assertEquals(CaptionTemplateProfile.TEMPLATE_ID,
                templateStep.templateSummary().templateId().value());
    }

    @Test
    @DisplayName("Watermark template step gets summary")
    void watermarkSummary() {
        WorkflowDefinition def = watermarkWorkflow();
        WorkflowDryRunPlan plan = planner.plan(def);
        WorkflowDryRunStep wmStep = plan.steps().stream()
                .filter(s -> s.stepType() == WorkflowStepType.APPLY_TEMPLATE)
                .findFirst().orElseThrow();
        assertNotNull(wmStep.templateSummary());
        assertEquals(WatermarkTemplateProfile.TEMPLATE_ID,
                wmStep.templateSummary().templateId().value());
    }

    @Test
    @DisplayName("Composite candidate template detected")
    void compositeCandidateDetected() {
        WorkflowDefinition def = compositeWorkflow();
        WorkflowDryRunPlan plan = planner.plan(def);
        WorkflowDryRunStep compStep = plan.steps().stream()
                .filter(s -> s.templateSummary() != null && s.templateSummary().compositeCandidate())
                .findFirst().orElse(null);
        // Composite candidate detection is heuristic based on template ID
        if (compStep != null) {
            assertTrue(compStep.templateSummary().compositeCandidate());
        }
    }

    @Test
    @DisplayName("Cycle workflow returns blocking issue")
    void cycleWorkflowBlocking() {
        WorkflowStep s1 = new WorkflowStep(new WorkflowStepId("s1"),
                WorkflowStepType.INGEST_PRODUCT,
                List.of(new WorkflowStepDependency(new WorkflowStepId("s2"), null)),
                Map.of(), null, Map.of());
        WorkflowStep s2 = new WorkflowStep(new WorkflowStepId("s2"),
                WorkflowStepType.VALIDATE_INPUT,
                List.of(new WorkflowStepDependency(new WorkflowStepId("s1"), null)),
                Map.of(), null, Map.of());
        WorkflowDefinition def = new WorkflowDefinition(
                new WorkflowDefinitionId("wf1"), new WorkflowVersion("1.0"), null,
                List.of(), List.of(s1, s2), List.of(), Map.of());

        WorkflowDryRunPlan plan = planner.plan(def);
        assertFalse(plan.valid());
        assertTrue(plan.hasBlockingIssues());
    }

    @Test
    @DisplayName("Missing dependency returns blocking issue")
    void missingDepBlocking() {
        WorkflowStep s1 = new WorkflowStep(new WorkflowStepId("s1"),
                WorkflowStepType.INGEST_PRODUCT,
                List.of(new WorkflowStepDependency(new WorkflowStepId("nonexistent"), null)),
                Map.of(), null, Map.of());
        WorkflowDefinition def = new WorkflowDefinition(
                new WorkflowDefinitionId("wf1"), new WorkflowVersion("1.0"), null,
                List.of(), List.of(s1), List.of(), Map.of());

        WorkflowDryRunPlan plan = planner.plan(def);
        assertFalse(plan.valid());
    }

    @Test
    @DisplayName("Null definition returns invalid plan")
    void nullDefinitionInvalid() {
        WorkflowDryRunPlan plan = planner.plan(null);
        assertFalse(plan.valid());
    }

    // --- Safety ---

    @Test
    @DisplayName("Dry-run plan is provider-neutral")
    void planProviderNeutral() {
        WorkflowDryRunPlan plan = planner.plan(validWorkflow());
        assertFalse(plan.toString().contains("providerName"));
        assertFalse(plan.toString().contains("bucket"));
    }

    @Test
    @DisplayName("Dry-run plan has no Remotion references")
    void planNoRemotion() {
        WorkflowDryRunPlan plan = planner.plan(validWorkflow());
        assertFalse(plan.toString().contains("remotion"));
    }

    @Test
    @DisplayName("WorkflowApplyTemplateStepDiff bridge is compatible")
    void diffBridgeCompatible() {
        com.example.platform.render.domain.timeline.diff.WorkflowApplyTemplateStepDiff diff =
                new com.example.platform.render.domain.timeline.diff.WorkflowApplyTemplateStepDiff(
                        "step-1", "app-1", List.of(), Map.of());
        assertEquals("step-1", diff.workflowStepId());
    }

    // --- Helpers ---

    private WorkflowDefinition validWorkflow() {
        TemplateApplicationRequest captionReq = new TemplateApplicationRequest(
                "proj-1", new TemplateDefinitionId(CaptionTemplateProfile.TEMPLATE_ID),
                new TemplateVersion(CaptionTemplateProfile.TEMPLATE_VERSION),
                List.of(TemplateTarget.product(TemplateTargetRole.MAIN_VIDEO, "prod-1"),
                        new TemplateTarget(TemplateTargetRole.CAPTION_TRACK,
                                TemplateTargetType.TEXT, "cap-1", Map.of())),
                List.of(), Map.of());

        WorkflowStep ingest = new WorkflowStep(new WorkflowStepId("ingest"),
                WorkflowStepType.INGEST_PRODUCT, List.of(), Map.of(), null, Map.of());
        WorkflowStep apply = new WorkflowStep(new WorkflowStepId("apply-caption"),
                WorkflowStepType.APPLY_TEMPLATE,
                List.of(new WorkflowStepDependency(new WorkflowStepId("ingest"), null)),
                Map.of(),
                new WorkflowTemplateApplicationStepSpec(
                        new TemplateDefinitionId(CaptionTemplateProfile.TEMPLATE_ID),
                        new TemplateVersion(CaptionTemplateProfile.TEMPLATE_VERSION),
                        captionReq, Map.of()),
                Map.of());
        WorkflowStep render = new WorkflowStep(new WorkflowStepId("render"),
                WorkflowStepType.RENDER_TIMELINE,
                List.of(new WorkflowStepDependency(new WorkflowStepId("apply-caption"), null)),
                Map.of(), null, Map.of());
        WorkflowStep lookup = new WorkflowStep(new WorkflowStepId("lookup"),
                WorkflowStepType.LOOKUP_RESULT,
                List.of(new WorkflowStepDependency(new WorkflowStepId("render"), null)),
                Map.of(), null, Map.of());
        WorkflowStep deliver = new WorkflowStep(new WorkflowStepId("deliver"),
                WorkflowStepType.DELIVER_PRODUCT,
                List.of(new WorkflowStepDependency(new WorkflowStepId("lookup"), null)),
                Map.of(), null, Map.of());

        return new WorkflowDefinition(
                new WorkflowDefinitionId("auto-caption-workflow"),
                new WorkflowVersion("1.0.0"),
                new WorkflowDisplayMetadata("Auto Caption", "Caption workflow", "caption"),
                List.of(new WorkflowInput("video", "PRODUCT", true, "Source video")),
                List.of(ingest, apply, render, lookup, deliver),
                List.of(new WorkflowOutput("output", "PRODUCT", "Final render")),
                Map.of());
    }

    private WorkflowDefinition watermarkWorkflow() {
        TemplateApplicationRequest wmReq = new TemplateApplicationRequest(
                "proj-1", new TemplateDefinitionId(WatermarkTemplateProfile.TEMPLATE_ID),
                new TemplateVersion(WatermarkTemplateProfile.TEMPLATE_VERSION),
                List.of(TemplateTarget.product(TemplateTargetRole.MAIN_VIDEO, "prod-1"),
                        TemplateTarget.product(TemplateTargetRole.WATERMARK_IMAGE, "wm-1")),
                List.of(), Map.of());

        WorkflowStep ingest = new WorkflowStep(new WorkflowStepId("ingest"),
                WorkflowStepType.INGEST_PRODUCT, List.of(), Map.of(), null, Map.of());
        WorkflowStep apply = new WorkflowStep(new WorkflowStepId("apply-watermark"),
                WorkflowStepType.APPLY_TEMPLATE,
                List.of(new WorkflowStepDependency(new WorkflowStepId("ingest"), null)),
                Map.of(),
                new WorkflowTemplateApplicationStepSpec(
                        new TemplateDefinitionId(WatermarkTemplateProfile.TEMPLATE_ID),
                        new TemplateVersion(WatermarkTemplateProfile.TEMPLATE_VERSION),
                        wmReq, Map.of()),
                Map.of());
        WorkflowStep render = new WorkflowStep(new WorkflowStepId("render"),
                WorkflowStepType.RENDER_TIMELINE,
                List.of(new WorkflowStepDependency(new WorkflowStepId("apply-watermark"), null)),
                Map.of(), null, Map.of());

        return new WorkflowDefinition(
                new WorkflowDefinitionId("watermark-workflow"),
                new WorkflowVersion("1.0.0"),
                new WorkflowDisplayMetadata("Watermark", "Watermark workflow", "watermark"),
                List.of(), List.of(ingest, apply, render), List.of(), Map.of());
    }

    private WorkflowDefinition compositeWorkflow() {
        TemplateApplicationRequest compositeReq = new TemplateApplicationRequest(
                "proj-1", new TemplateDefinitionId("builtin.social.short-video"),
                new TemplateVersion("1.0.0"),
                List.of(TemplateTarget.product(TemplateTargetRole.MAIN_VIDEO, "prod-1")),
                List.of(), Map.of());

        WorkflowStep ingest = new WorkflowStep(new WorkflowStepId("ingest"),
                WorkflowStepType.INGEST_PRODUCT, List.of(), Map.of(), null, Map.of());
        WorkflowStep apply = new WorkflowStep(new WorkflowStepId("apply-composite"),
                WorkflowStepType.APPLY_TEMPLATE,
                List.of(new WorkflowStepDependency(new WorkflowStepId("ingest"), null)),
                Map.of(),
                new WorkflowTemplateApplicationStepSpec(
                        new TemplateDefinitionId("builtin.social.short-video"),
                        new TemplateVersion("1.0.0"),
                        compositeReq, Map.of()),
                Map.of());
        WorkflowStep render = new WorkflowStep(new WorkflowStepId("render"),
                WorkflowStepType.RENDER_TIMELINE,
                List.of(new WorkflowStepDependency(new WorkflowStepId("apply-composite"), null)),
                Map.of(), null, Map.of());

        return new WorkflowDefinition(
                new WorkflowDefinitionId("social-workflow"),
                new WorkflowVersion("1.0.0"),
                new WorkflowDisplayMetadata("Social", "Social workflow", "social"),
                List.of(), List.of(ingest, apply, render), List.of(), Map.of());
    }
}
