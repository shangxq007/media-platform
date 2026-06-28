package com.example.platform.render.domain.workflow;

import com.example.platform.render.domain.template.*;
import com.example.platform.render.domain.template.profile.caption.*;
import com.example.platform.render.domain.template.profile.watermark.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Workflow semantic model domain invariants.
 * Proves: provider-neutral, composition with templates, safe validation.
 */
class WorkflowDomainTest {

    // --- WorkflowDefinition ---

    @Test
    @DisplayName("WorkflowDefinition requires id/version/non-empty steps")
    void definitionRequiresIdVersionSteps() {
        assertThrows(IllegalArgumentException.class, () ->
                new WorkflowDefinition(null, new WorkflowVersion("1.0"), null,
                        List.of(), List.of(validStep()), List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new WorkflowDefinition(new WorkflowDefinitionId("wf1"), null, null,
                        List.of(), List.of(validStep()), List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new WorkflowDefinition(new WorkflowDefinitionId("wf1"), new WorkflowVersion("1.0"),
                        null, List.of(), List.of(), List.of(), Map.of()));
    }

    @Test
    @DisplayName("WorkflowDefinition is provider-neutral")
    void definitionProviderNeutral() {
        WorkflowDefinition def = validDefinition();
        String str = def.toString();
        assertFalse(str.contains("providerName"));
        assertFalse(str.contains("backendName"));
        assertFalse(str.contains("ffmpeg"));
        assertFalse(str.contains("remotion"));
    }

    @Test
    @DisplayName("hasApplyTemplateStep returns true for template workflow")
    void hasApplyTemplateStep() {
        WorkflowDefinition def = validDefinition();
        assertTrue(def.hasApplyTemplateStep());
    }

    @Test
    @DisplayName("Blank WorkflowDefinitionId rejected")
    void blankIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinitionId(""));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinitionId(null));
    }

    // --- WorkflowStep ---

    @Test
    @DisplayName("WorkflowStep supports APPLY_TEMPLATE type")
    void stepSupportsApplyTemplate() {
        WorkflowStep step = validStep();
        assertTrue(step.isApplyTemplate());
        assertEquals(WorkflowStepType.APPLY_TEMPLATE, step.type());
    }

    @Test
    @DisplayName("WorkflowStepType contains all expected types")
    void stepTypeContainsExpected() {
        assertTrue(List.of(WorkflowStepType.values()).contains(WorkflowStepType.INGEST_PRODUCT));
        assertTrue(List.of(WorkflowStepType.values()).contains(WorkflowStepType.ANALYZE_ASR));
        assertTrue(List.of(WorkflowStepType.values()).contains(WorkflowStepType.APPLY_TEMPLATE));
        assertTrue(List.of(WorkflowStepType.values()).contains(WorkflowStepType.COMPILE_TIMELINE));
        assertTrue(List.of(WorkflowStepType.values()).contains(WorkflowStepType.RENDER_TIMELINE));
        assertTrue(List.of(WorkflowStepType.values()).contains(WorkflowStepType.LOOKUP_RESULT));
        assertTrue(List.of(WorkflowStepType.values()).contains(WorkflowStepType.DELIVER_PRODUCT));
    }

    @Test
    @DisplayName("APPLY_TEMPLATE step requires template application spec")
    void applyTemplateRequiresSpec() {
        WorkflowStep step = new WorkflowStep(
                new WorkflowStepId("s1"), WorkflowStepType.APPLY_TEMPLATE,
                List.of(), Map.of(), validTemplateSpec(), Map.of());
        assertNotNull(step.templateApplicationSpec());
    }

    @Test
    @DisplayName("Non-template step can be created without template spec")
    void nonTemplateStepNoSpec() {
        WorkflowStep step = new WorkflowStep(
                new WorkflowStepId("s1"), WorkflowStepType.INGEST_PRODUCT,
                List.of(), Map.of(), null, Map.of());
        assertNull(step.templateApplicationSpec());
        assertFalse(step.isApplyTemplate());
    }

    @Test
    @DisplayName("Step requires id and type")
    void stepRequiresIdAndType() {
        assertThrows(IllegalArgumentException.class, () ->
                new WorkflowStep(null, WorkflowStepType.APPLY_TEMPLATE,
                        List.of(), Map.of(), null, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new WorkflowStep(new WorkflowStepId("s1"), null,
                        List.of(), Map.of(), null, Map.of()));
    }

    // --- WorkflowTemplateApplicationStepSpec ---

    @Test
    @DisplayName("TemplateApplicationStepSpec references TemplateApplicationRequest")
    void specReferencesTemplateRequest() {
        TemplateApplicationRequest templateReq = new TemplateApplicationRequest(
                "proj-1", new TemplateDefinitionId("builtin.caption.basic"),
                new TemplateVersion("1.0.0"),
                List.of(TemplateTarget.product(TemplateTargetRole.MAIN_VIDEO, "prod-1"),
                        new TemplateTarget(TemplateTargetRole.CAPTION_TRACK,
                                TemplateTargetType.TEXT, "cap-1", Map.of())),
                List.of(), Map.of());

        WorkflowTemplateApplicationStepSpec spec = new WorkflowTemplateApplicationStepSpec(
                new TemplateDefinitionId("builtin.caption.basic"),
                new TemplateVersion("1.0.0"),
                templateReq, Map.of());

        assertEquals("builtin.caption.basic", spec.templateId().value());
        assertNotNull(spec.templateApplicationRequest());
        assertEquals("proj-1", spec.templateApplicationRequest().projectId());
    }

    @Test
    @DisplayName("Can reference CaptionTemplate profile request")
    void specCanReferenceCaptionProfile() {
        TemplateApplicationRequest captionReq = CaptionTemplateApplicationMapper.map(
                new com.example.platform.render.domain.caption.CaptionTemplateRenderRequest(
                        "proj-1", "prod-video",
                        List.of(new com.example.platform.render.domain.caption.CaptionSegmentSpec(0, 3000, "Hello")),
                        null, null, Map.of()));

        WorkflowTemplateApplicationStepSpec spec = new WorkflowTemplateApplicationStepSpec(
                new TemplateDefinitionId(CaptionTemplateProfile.TEMPLATE_ID),
                new TemplateVersion(CaptionTemplateProfile.TEMPLATE_VERSION),
                captionReq, Map.of());

        assertEquals(CaptionTemplateProfile.TEMPLATE_ID, spec.templateId().value());
    }

    @Test
    @DisplayName("Can reference WatermarkTemplate profile request")
    void specCanReferenceWatermarkProfile() {
        TemplateApplicationRequest watermarkReq = WatermarkTemplateApplicationMapper.map(
                new WatermarkTemplateApplicationInput(
                        "proj-1", "prod-video", "prod-watermark",
                        "BOTTOM_RIGHT", 50, 10, 10, Map.of()));

        WorkflowTemplateApplicationStepSpec spec = new WorkflowTemplateApplicationStepSpec(
                new TemplateDefinitionId(WatermarkTemplateProfile.TEMPLATE_ID),
                new TemplateVersion(WatermarkTemplateProfile.TEMPLATE_VERSION),
                watermarkReq, Map.of());

        assertEquals(WatermarkTemplateProfile.TEMPLATE_ID, spec.templateId().value());
    }

    @Test
    @DisplayName("TemplateApplicationStepSpec requires templateId")
    void specRequiresTemplateId() {
        assertThrows(IllegalArgumentException.class, () ->
                new WorkflowTemplateApplicationStepSpec(null, null, null, Map.of()));
    }

    // --- Validation ---

    @Test
    @DisplayName("WorkflowValidationResult supports valid/invalid states")
    void validationResult() {
        WorkflowValidationResult valid = WorkflowValidationResult.success();
        assertTrue(valid.valid());
        assertTrue(valid.errors().isEmpty());

        WorkflowValidationResult invalid = WorkflowValidationResult.failure(
                List.of(new WorkflowValidationError("field", "CODE", "msg")));
        assertFalse(invalid.valid());
        assertEquals(1, invalid.errors().size());
    }

    // --- Application Result ---

    @Test
    @DisplayName("WorkflowApplicationResult can represent NOT_IMPLEMENTED")
    void resultNotImplemented() {
        WorkflowApplicationResult result = WorkflowApplicationResult.notImplemented("Not yet");
        assertEquals(WorkflowApplicationStatus.NOT_IMPLEMENTED, result.status());
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("WorkflowApplicationResult can represent ACCEPTED")
    void resultAccepted() {
        WorkflowApplicationResult result = WorkflowApplicationResult.accepted("OK");
        assertEquals(WorkflowApplicationStatus.ACCEPTED, result.status());
        assertTrue(result.isSuccess());
    }

    // --- Step Result ---

    @Test
    @DisplayName("WorkflowStepResult can represent pending/not-implemented")
    void stepResultStates() {
        WorkflowStepResult pending = WorkflowStepResult.pending(
                new WorkflowStepId("s1"), WorkflowStepType.APPLY_TEMPLATE);
        assertEquals(WorkflowStepStatus.PENDING, pending.status());

        WorkflowStepResult notImpl = WorkflowStepResult.notImplemented(
                new WorkflowStepId("s1"), WorkflowStepType.RENDER_TIMELINE, "not yet");
        assertEquals(WorkflowStepStatus.NOT_IMPLEMENTED, notImpl.status());
    }

    // --- Safety ---

    @Test
    @DisplayName("No providerName/providerType/backendName fields exist")
    void noProviderFields() {
        WorkflowDefinition def = validDefinition();
        WorkflowStep step = validStep();
        WorkflowApplicationResult result = WorkflowApplicationResult.accepted("ok");
        assertFalse(def.toString().contains("providerName"));
        assertFalse(step.toString().contains("providerName"));
    }

    @Test
    @DisplayName("No storage path/bucket/objectKey/signedUrl fields exist")
    void noStorageFields() {
        WorkflowDefinition def = validDefinition();
        assertFalse(def.toString().contains("bucket"));
        assertFalse(def.toString().contains("signedUrl"));
    }

    @Test
    @DisplayName("No Remotion references exist")
    void noRemotionReferences() {
        WorkflowDefinition def = validDefinition();
        assertFalse(def.toString().contains("remotion"));
    }

    @Test
    @DisplayName("CaptionTemplate profile tests still pass")
    void captionProfileStillPasses() {
        TemplateDefinition def = CaptionTemplateProfile.definition();
        assertEquals("builtin.caption.basic", def.id().value());
    }

    @Test
    @DisplayName("WatermarkTemplate profile tests still pass")
    void watermarkProfileStillPasses() {
        TemplateDefinition def = WatermarkTemplateProfile.definition();
        assertEquals("builtin.watermark.basic", def.id().value());
    }

    // --- Helpers ---

    private WorkflowStep validStep() {
        return new WorkflowStep(
                new WorkflowStepId("apply-caption"),
                WorkflowStepType.APPLY_TEMPLATE,
                List.of(),
                Map.of(),
                validTemplateSpec(),
                Map.of("description", "Apply caption template"));
    }

    private WorkflowTemplateApplicationStepSpec validTemplateSpec() {
        TemplateApplicationRequest templateReq = new TemplateApplicationRequest(
                "proj-1", new TemplateDefinitionId("builtin.caption.basic"),
                new TemplateVersion("1.0.0"),
                List.of(TemplateTarget.product(TemplateTargetRole.MAIN_VIDEO, "prod-1"),
                        new TemplateTarget(TemplateTargetRole.CAPTION_TRACK,
                                TemplateTargetType.TEXT, "cap-1", Map.of())),
                List.of(), Map.of());
        return new WorkflowTemplateApplicationStepSpec(
                new TemplateDefinitionId("builtin.caption.basic"),
                new TemplateVersion("1.0.0"),
                templateReq, Map.of());
    }

    private WorkflowDefinition validDefinition() {
        return new WorkflowDefinition(
                new WorkflowDefinitionId("auto-caption-workflow"),
                new WorkflowVersion("1.0.0"),
                new WorkflowDisplayMetadata("Auto Caption", "Caption workflow", "caption"),
                List.of(new WorkflowInput("video", "PRODUCT", true, "Source video")),
                List.of(
                        new WorkflowStep(new WorkflowStepId("ingest"),
                                WorkflowStepType.INGEST_PRODUCT, List.of(), Map.of(), null, Map.of()),
                        validStep(),
                        new WorkflowStep(new WorkflowStepId("render"),
                                WorkflowStepType.RENDER_TIMELINE,
                                List.of(new WorkflowStepDependency(
                                        new WorkflowStepId("apply-caption"), null)),
                                Map.of(), null, Map.of())),
                List.of(new WorkflowOutput("output", "PRODUCT", "Final render")),
                Map.of("source", "test"));
    }
}
