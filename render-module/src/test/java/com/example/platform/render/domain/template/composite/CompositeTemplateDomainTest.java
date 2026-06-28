package com.example.platform.render.domain.template.composite;

import com.example.platform.render.domain.template.*;
import com.example.platform.render.domain.template.profile.caption.*;
import com.example.platform.render.domain.template.profile.watermark.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Composite Template semantics.
 * Proves: composition model, binding, expansion, compiler, compatibility.
 */
class CompositeTemplateDomainTest {

    // --- Identity ---

    @Test
    @DisplayName("Identity types reject blank values")
    void identityRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new CompositeTemplateDefinitionId(""));
        assertThrows(IllegalArgumentException.class, () -> new CompositeTemplateChildId(null));
        assertThrows(IllegalArgumentException.class, () -> new CompositeTemplateExpansionPlanId("  "));
    }

    @Test
    @DisplayName("CompositeTemplateChildOrder rejects negative")
    void childOrderRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new CompositeTemplateChildOrder(-1));
    }

    // --- CompositeTemplateDefinition ---

    @Test
    @DisplayName("Definition requires id/templateId/version/children")
    void definitionRequiresFields() {
        TemplateDefinitionId tid = new TemplateDefinitionId("builtin.social.short");
        TemplateVersion ver = new TemplateVersion("1.0.0");
        CompositeTemplateChild child = validChild("child-1", CaptionTemplateProfile.TEMPLATE_ID);
        assertThrows(IllegalArgumentException.class, () ->
                new CompositeTemplateDefinition(null, tid, ver, null,
                        List.of(child), List.of(), List.of(),
                        CompositeTemplateMergePolicy.ORDERED,
                        CompositeTemplateConflictPolicy.FAIL_FAST, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new CompositeTemplateDefinition(new CompositeTemplateDefinitionId("c1"), null, ver, null,
                        List.of(child), List.of(), List.of(),
                        CompositeTemplateMergePolicy.ORDERED,
                        CompositeTemplateConflictPolicy.FAIL_FAST, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new CompositeTemplateDefinition(new CompositeTemplateDefinitionId("c1"), tid, ver, null,
                        List.of(), List.of(), List.of(),
                        CompositeTemplateMergePolicy.ORDERED,
                        CompositeTemplateConflictPolicy.FAIL_FAST, Map.of()));
    }

    @Test
    @DisplayName("hasChild finds child by template id")
    void hasChildFinds() {
        CompositeTemplateDefinition def = socialShortVideoDefinition();
        assertTrue(def.hasChild(new TemplateDefinitionId(CaptionTemplateProfile.TEMPLATE_ID)));
        assertTrue(def.hasChild(new TemplateDefinitionId(WatermarkTemplateProfile.TEMPLATE_ID)));
        assertFalse(def.hasChild(new TemplateDefinitionId("nonexistent")));
    }

    // --- CompositeTemplateChild ---

    @Test
    @DisplayName("Child requires childTemplateId/version/order")
    void childRequiresFields() {
        TemplateDefinitionId tid = new TemplateDefinitionId("builtin.caption.basic");
        TemplateVersion ver = new TemplateVersion("1.0.0");
        assertThrows(IllegalArgumentException.class, () ->
                new CompositeTemplateChild(new CompositeTemplateChildId("c1"), null, ver,
                        new CompositeTemplateChildOrder(0), true, Map.of()));
    }

    // --- TemplateTargetBinding ---

    @Test
    @DisplayName("Target binding maps parent MAIN_VIDEO to child MAIN_VIDEO")
    void targetBindingMaps() {
        TemplateTargetBinding binding = new TemplateTargetBinding(
                TemplateTargetRole.MAIN_VIDEO,
                new CompositeTemplateChildId("caption-child"),
                TemplateTargetRole.MAIN_VIDEO,
                true, Map.of());
        assertEquals(TemplateTargetRole.MAIN_VIDEO, binding.parentRole());
        assertEquals(TemplateTargetRole.MAIN_VIDEO, binding.childRole());
    }

    @Test
    @DisplayName("Target binding requires all roles and childId")
    void targetBindingRequires() {
        assertThrows(IllegalArgumentException.class, () ->
                new TemplateTargetBinding(null, new CompositeTemplateChildId("c1"),
                        TemplateTargetRole.MAIN_VIDEO, true, Map.of()));
    }

    // --- TemplateParameterBinding ---

    @Test
    @DisplayName("Parameter binding maps parent to child parameter")
    void parameterBindingMaps() {
        TemplateParameterBinding binding = new TemplateParameterBinding(
                "captionStyle",
                new CompositeTemplateChildId("caption-child"),
                "fontSize",
                new TemplateBindingExpression("parent.fontSize"),
                false, Map.of());
        assertEquals("captionStyle", binding.parentParameterName());
        assertEquals("fontSize", binding.childParameterName());
    }

    @Test
    @DisplayName("TemplateBindingExpression is inert string")
    void bindingExpressionInert() {
        TemplateBindingExpression expr = new TemplateBindingExpression("parent.fontSize");
        assertEquals("parent.fontSize", expr.expression());
        assertThrows(IllegalArgumentException.class, () -> new TemplateBindingExpression(""));
    }

    // --- Policies ---

    @Test
    @DisplayName("Merge policies include ORDERED, BY_LAYER, BY_Z_INDEX")
    void mergePoliciesExist() {
        assertTrue(List.of(CompositeTemplateMergePolicy.values()).contains(CompositeTemplateMergePolicy.ORDERED));
        assertTrue(List.of(CompositeTemplateMergePolicy.values()).contains(CompositeTemplateMergePolicy.BY_LAYER));
        assertTrue(List.of(CompositeTemplateMergePolicy.values()).contains(CompositeTemplateMergePolicy.BY_Z_INDEX));
    }

    @Test
    @DisplayName("Conflict policies include FAIL_FAST, PARENT_OVERRIDES, MANUAL_REVIEW_REQUIRED")
    void conflictPoliciesExist() {
        assertTrue(List.of(CompositeTemplateConflictPolicy.values()).contains(CompositeTemplateConflictPolicy.FAIL_FAST));
        assertTrue(List.of(CompositeTemplateConflictPolicy.values()).contains(CompositeTemplateConflictPolicy.PARENT_OVERRIDES));
        assertTrue(List.of(CompositeTemplateConflictPolicy.values()).contains(CompositeTemplateConflictPolicy.MANUAL_REVIEW_REQUIRED));
    }

    // --- Expansion Plan ---

    @Test
    @DisplayName("Expansion plan can hold caption and watermark steps")
    void expansionPlanHoldsSteps() {
        CompositeTemplateExpansionStep captionStep = new CompositeTemplateExpansionStep(
                new CompositeTemplateChildId("caption-child"),
                new TemplateDefinitionId(CaptionTemplateProfile.TEMPLATE_ID),
                dummyRequest(), CompositeTemplateExpansionStepStatus.READY, Map.of());
        CompositeTemplateExpansionStep watermarkStep = new CompositeTemplateExpansionStep(
                new CompositeTemplateChildId("watermark-child"),
                new TemplateDefinitionId(WatermarkTemplateProfile.TEMPLATE_ID),
                dummyRequest(), CompositeTemplateExpansionStepStatus.READY, Map.of());

        CompositeTemplateExpansionPlan plan = new CompositeTemplateExpansionPlan(
                new CompositeTemplateExpansionPlanId("plan-1"),
                new CompositeTemplateDefinitionId("social-short"),
                List.of(captionStep, watermarkStep),
                CompositeTemplateValidationResult.success(), Map.of());

        assertEquals(2, plan.steps().size());
        assertTrue(plan.isValid());
        assertEquals(2, plan.readyStepCount());
    }

    // --- Validation ---

    @Test
    @DisplayName("Validation result supports valid/invalid")
    void validationResult() {
        assertTrue(CompositeTemplateValidationResult.success().valid());
        assertFalse(CompositeTemplateValidationResult.failure(
                List.of(new CompositeTemplateValidationError("f", "c", "m"))).valid());
    }

    // --- Compiler ---

    @Test
    @DisplayName("Compiler expands SocialShortVideo into two child requests")
    void compilerExpandsSocialShort() {
        CompositeTemplateApplicationCompiler compiler = new CompositeTemplateApplicationCompiler();
        CompositeTemplateDefinition def = socialShortVideoDefinition();
        TemplateApplicationRequest parentRequest = parentRequest();

        CompositeTemplateExpansionPlan plan = compiler.expand(def, parentRequest);

        assertTrue(plan.isValid());
        assertEquals(2, plan.steps().size());

        // Caption step
        CompositeTemplateExpansionStep captionStep = plan.steps().stream()
                .filter(s -> s.childTemplateId().value().equals(CaptionTemplateProfile.TEMPLATE_ID))
                .findFirst().orElseThrow();
        assertEquals(CompositeTemplateExpansionStepStatus.READY, captionStep.status());
        assertEquals(CaptionTemplateProfile.TEMPLATE_ID,
                captionStep.templateApplicationRequest().templateId().value());

        // Watermark step
        CompositeTemplateExpansionStep watermarkStep = plan.steps().stream()
                .filter(s -> s.childTemplateId().value().equals(WatermarkTemplateProfile.TEMPLATE_ID))
                .findFirst().orElseThrow();
        assertEquals(CompositeTemplateExpansionStepStatus.READY, watermarkStep.status());
    }

    @Test
    @DisplayName("Compiler maps MAIN_VIDEO to both child requests")
    void compilerMapsMainVideo() {
        CompositeTemplateApplicationCompiler compiler = new CompositeTemplateApplicationCompiler();
        CompositeTemplateExpansionPlan plan = compiler.expand(socialShortVideoDefinition(), parentRequest());

        plan.steps().forEach(step -> {
            boolean hasMainVideo = step.templateApplicationRequest().targets().stream()
                    .anyMatch(t -> t.role() == TemplateTargetRole.MAIN_VIDEO);
            assertTrue(hasMainVideo, "Child " + step.childTemplateId() + " should have MAIN_VIDEO");
        });
    }

    @Test
    @DisplayName("Compiler maps CAPTION_TRACK to Caption child")
    void compilerMapsCaptionTrack() {
        CompositeTemplateApplicationCompiler compiler = new CompositeTemplateApplicationCompiler();
        CompositeTemplateExpansionPlan plan = compiler.expand(socialShortVideoDefinition(), parentRequest());

        CompositeTemplateExpansionStep captionStep = plan.steps().stream()
                .filter(s -> s.childTemplateId().value().equals(CaptionTemplateProfile.TEMPLATE_ID))
                .findFirst().orElseThrow();

        boolean hasCaptionTrack = captionStep.templateApplicationRequest().targets().stream()
                .anyMatch(t -> t.role() == TemplateTargetRole.CAPTION_TRACK);
        assertTrue(hasCaptionTrack);
    }

    @Test
    @DisplayName("Compiler maps WATERMARK_IMAGE to Watermark child")
    void compilerMapsWatermarkImage() {
        CompositeTemplateApplicationCompiler compiler = new CompositeTemplateApplicationCompiler();
        CompositeTemplateExpansionPlan plan = compiler.expand(socialShortVideoDefinition(), parentRequest());

        CompositeTemplateExpansionStep watermarkStep = plan.steps().stream()
                .filter(s -> s.childTemplateId().value().equals(WatermarkTemplateProfile.TEMPLATE_ID))
                .findFirst().orElseThrow();

        boolean hasWatermark = watermarkStep.templateApplicationRequest().targets().stream()
                .anyMatch(t -> t.role() == TemplateTargetRole.WATERMARK_IMAGE);
        assertTrue(hasWatermark);
    }

    @Test
    @DisplayName("Compiler maps safe parameters to child requests")
    void compilerMapsParameters() {
        CompositeTemplateApplicationCompiler compiler = new CompositeTemplateApplicationCompiler();
        CompositeTemplateExpansionPlan plan = compiler.expand(socialShortVideoDefinition(), parentRequest());

        CompositeTemplateExpansionStep captionStep = plan.steps().stream()
                .filter(s -> s.childTemplateId().value().equals(CaptionTemplateProfile.TEMPLATE_ID))
                .findFirst().orElseThrow();

        List<String> paramIds = captionStep.templateApplicationRequest().parameters().stream()
                .map(TemplateParameter::parameterId).toList();
        assertTrue(paramIds.contains("fontSize"),
                "Caption child should have fontSize parameter mapped from parent");
    }

    @Test
    @DisplayName("Compiler rejects missing required target binding")
    void compilerRejectsMissingTarget() {
        CompositeTemplateApplicationCompiler compiler = new CompositeTemplateApplicationCompiler();
        // Parent request has MAIN_VIDEO but no WATERMARK_IMAGE
        TemplateApplicationRequest parentReq = new TemplateApplicationRequest(
                "proj-1", new TemplateDefinitionId("social-short"), new TemplateVersion("1.0"),
                List.of(TemplateTarget.product(TemplateTargetRole.MAIN_VIDEO, "prod-1")),
                List.of(), Map.of());

        CompositeTemplateExpansionPlan plan = compiler.expand(socialShortVideoDefinition(), parentReq);
        assertFalse(plan.isValid());
    }

    @Test
    @DisplayName("Compiler is provider-neutral")
    void compilerProviderNeutral() {
        CompositeTemplateApplicationCompiler compiler = new CompositeTemplateApplicationCompiler();
        CompositeTemplateExpansionPlan plan = compiler.expand(socialShortVideoDefinition(), parentRequest());
        assertFalse(plan.toString().contains("providerName"));
        assertFalse(plan.toString().contains("bucket"));
    }

    // --- Compatibility ---

    @Test
    @DisplayName("CompositeTemplate is not WorkflowDefinition")
    void compositeIsNotWorkflow() {
        CompositeTemplateDefinition def = socialShortVideoDefinition();
        // CompositeTemplate has children + targetBindings + mergePolicy
        // WorkflowDefinition has steps + inputs + outputs
        assertFalse(def.toString().contains("WorkflowStep"));
    }

    @Test
    @DisplayName("CompositeTemplateDiff can represent child template change")
    void compositeDiffRepresentsChange() {
        com.example.platform.render.domain.timeline.diff.CompositeTemplateDiff diff =
                new com.example.platform.render.domain.timeline.diff.CompositeTemplateDiff(
                        "comp-1",
                        List.of(CaptionTemplateProfile.TEMPLATE_ID, WatermarkTemplateProfile.TEMPLATE_ID),
                        List.of(), Map.of());
        assertEquals(2, diff.childTemplateIds().size());
    }

    @Test
    @DisplayName("No providerName/providerType/backendName fields")
    void noProviderFields() {
        CompositeTemplateDefinition def = socialShortVideoDefinition();
        assertFalse(def.toString().contains("providerName"));
        assertFalse(def.toString().contains("backendName"));
    }

    @Test
    @DisplayName("No bucket/objectKey/signedUrl fields")
    void noStorageFields() {
        CompositeTemplateDefinition def = socialShortVideoDefinition();
        assertFalse(def.toString().contains("bucket"));
        assertFalse(def.toString().contains("signedUrl"));
    }

    @Test
    @DisplayName("No Remotion references")
    void noRemotionReferences() {
        CompositeTemplateDefinition def = socialShortVideoDefinition();
        assertFalse(def.toString().contains("remotion"));
    }

    @Test
    @DisplayName("Existing profiles still pass")
    void existingProfilesPass() {
        assertEquals("builtin.caption.basic", CaptionTemplateProfile.definition().id().value());
        assertEquals("builtin.watermark.basic", WatermarkTemplateProfile.definition().id().value());
    }

    // --- Helpers ---

    private CompositeTemplateDefinition socialShortVideoDefinition() {
        CompositeTemplateChildId captionChildId = new CompositeTemplateChildId("caption-child");
        CompositeTemplateChildId watermarkChildId = new CompositeTemplateChildId("watermark-child");

        return new CompositeTemplateDefinition(
                new CompositeTemplateDefinitionId("builtin.social.short-video"),
                new TemplateDefinitionId("builtin.social.short-video"),
                new TemplateVersion("1.0.0"),
                new TemplateDisplayMetadata("Social Short Video",
                        "Caption + watermark composite template", "social"),
                List.of(
                        new CompositeTemplateChild(captionChildId,
                                new TemplateDefinitionId(CaptionTemplateProfile.TEMPLATE_ID),
                                new TemplateVersion(CaptionTemplateProfile.TEMPLATE_VERSION),
                                new CompositeTemplateChildOrder(0), true, Map.of()),
                        new CompositeTemplateChild(watermarkChildId,
                                new TemplateDefinitionId(WatermarkTemplateProfile.TEMPLATE_ID),
                                new TemplateVersion(WatermarkTemplateProfile.TEMPLATE_VERSION),
                                new CompositeTemplateChildOrder(1), true, Map.of())),
                List.of(
                        new TemplateTargetBinding(TemplateTargetRole.MAIN_VIDEO,
                                captionChildId, TemplateTargetRole.MAIN_VIDEO, true, Map.of()),
                        new TemplateTargetBinding(TemplateTargetRole.MAIN_VIDEO,
                                watermarkChildId, TemplateTargetRole.MAIN_VIDEO, true, Map.of()),
                        new TemplateTargetBinding(TemplateTargetRole.CAPTION_TRACK,
                                captionChildId, TemplateTargetRole.CAPTION_TRACK, true, Map.of()),
                        new TemplateTargetBinding(TemplateTargetRole.WATERMARK_IMAGE,
                                watermarkChildId, TemplateTargetRole.WATERMARK_IMAGE, true, Map.of())),
                List.of(
                        new TemplateParameterBinding("Font Size",
                                captionChildId, "fontSize",
                                new TemplateBindingExpression("parent.fontSize"),
                                false, Map.of())),
                CompositeTemplateMergePolicy.ORDERED,
                CompositeTemplateConflictPolicy.FAIL_FAST,
                Map.of("source", "test"));
    }

    private CompositeTemplateChild validChild(String id, String templateId) {
        return new CompositeTemplateChild(
                new CompositeTemplateChildId(id),
                new TemplateDefinitionId(templateId),
                new TemplateVersion("1.0.0"),
                new CompositeTemplateChildOrder(0), true, Map.of());
    }

    private TemplateApplicationRequest parentRequest() {
        return new TemplateApplicationRequest(
                "proj-1",
                new TemplateDefinitionId("builtin.social.short-video"),
                new TemplateVersion("1.0.0"),
                List.of(
                        TemplateTarget.product(TemplateTargetRole.MAIN_VIDEO, "prod-video-1"),
                        new TemplateTarget(TemplateTargetRole.CAPTION_TRACK,
                                TemplateTargetType.TEXT, "caption-1", Map.of()),
                        TemplateTarget.product(TemplateTargetRole.WATERMARK_IMAGE, "prod-watermark-1")),
                List.of(
                        new TemplateParameter("fontSize", "Font Size", "NUMBER", false, "48")),
                Map.of("requestSource", "test"));
    }

    private TemplateApplicationRequest dummyRequest() {
        return new TemplateApplicationRequest(
                "proj-1", new TemplateDefinitionId("t1"), new TemplateVersion("1.0"),
                List.of(TemplateTarget.product(TemplateTargetRole.MAIN_VIDEO, "p1")),
                List.of(), Map.of());
    }
}
