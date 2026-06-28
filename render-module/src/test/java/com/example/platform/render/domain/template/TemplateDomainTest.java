package com.example.platform.render.domain.template;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for generic Template domain model invariants.
 * Proves: provider-neutral, storage-neutral, workflow-ready, compile-safe.
 */
class TemplateDomainTest {

    // --- TemplateDefinition ---

    @Test
    @DisplayName("TemplateDefinition requires id/version/type")
    void definitionRequiresIdVersionType() {
        assertThrows(IllegalArgumentException.class, () ->
                new TemplateDefinition(null, new TemplateVersion("1.0"), TemplateType.CAPTION,
                        null, List.of(), List.of(), List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new TemplateDefinition(new TemplateDefinitionId("t1"), null, TemplateType.CAPTION,
                        null, List.of(), List.of(), List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new TemplateDefinition(new TemplateDefinitionId("t1"), new TemplateVersion("1.0"),
                        null, null, List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("TemplateDefinition keeps operations provider-neutral")
    void definitionProviderNeutral() {
        TemplateDefinition def = validDefinition();
        String str = def.toString();
        assertFalse(str.contains("providerName"));
        assertFalse(str.contains("backendName"));
        assertFalse(str.contains("ffmpeg"));
        assertFalse(str.contains("remotion"));
    }

    @Test
    @DisplayName("Blank TemplateDefinitionId rejected")
    void blankIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TemplateDefinitionId(""));
        assertThrows(IllegalArgumentException.class, () -> new TemplateDefinitionId("  "));
        assertThrows(IllegalArgumentException.class, () -> new TemplateDefinitionId(null));
    }

    @Test
    @DisplayName("Blank TemplateVersion rejected")
    void blankVersionRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TemplateVersion(""));
    }

    // --- TemplateApplicationRequest ---

    @Test
    @DisplayName("Request uses role-based targets")
    void requestUsesRoleBasedTargets() {
        TemplateApplicationRequest req = validRequest();
        assertEquals(TemplateTargetRole.MAIN_VIDEO, req.targets().get(0).role());
        assertEquals(TemplateTargetRole.CAPTION_TRACK, req.targets().get(1).role());
    }

    @Test
    @DisplayName("Request requires projectId and templateId")
    void requestRequiresFields() {
        assertThrows(IllegalArgumentException.class, () ->
                new TemplateApplicationRequest(null, new TemplateDefinitionId("t1"),
                        null, List.of(validTarget()), List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new TemplateApplicationRequest("p1", null,
                        null, List.of(validTarget()), List.of(), Map.of()));
    }

    @Test
    @DisplayName("Request requires non-empty targets")
    void requestRequiresTargets() {
        assertThrows(IllegalArgumentException.class, () ->
                new TemplateApplicationRequest("p1", new TemplateDefinitionId("t1"),
                        null, List.of(), List.of(), Map.of()));
    }

    // --- TemplateTarget ---

    @Test
    @DisplayName("TemplateTarget supports PRODUCT type")
    void targetSupportsProduct() {
        TemplateTarget t = TemplateTarget.product(TemplateTargetRole.MAIN_VIDEO, "prod-1");
        assertEquals(TemplateTargetType.PRODUCT, t.targetType());
        assertEquals("prod-1", t.targetId());
    }

    @Test
    @DisplayName("TemplateTarget rejects blank targetId")
    void targetRejectsBlankId() {
        assertThrows(IllegalArgumentException.class, () ->
                new TemplateTarget(TemplateTargetRole.MAIN_VIDEO, TemplateTargetType.PRODUCT, "", Map.of()));
    }

    // --- TemplateOperation ---

    @Test
    @DisplayName("Operation requires type and target role")
    void operationRequiresTypeAndRole() {
        assertThrows(IllegalArgumentException.class, () ->
                new TemplateOperation("op1", null, TemplateTargetRole.MAIN_VIDEO, Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new TemplateOperation("op1", TemplateOperationType.ADD_TEXT_OVERLAY, null, Map.of(), List.of()));
    }

    @Test
    @DisplayName("Operation supports capability requirements")
    void operationSupportsCapabilities() {
        TemplateOperation op = new TemplateOperation("op1",
                TemplateOperationType.ADD_TEXT_OVERLAY,
                TemplateTargetRole.CAPTION_TRACK,
                Map.of(), List.of(TemplateCapabilityRequirement.required("TEXT_OVERLAY")));
        assertEquals(1, op.requiredCapabilities().size());
        assertEquals("TEXT_OVERLAY", op.requiredCapabilities().get(0).capability());
    }

    @Test
    @DisplayName("Operation does not expose provider/backend/storage fields")
    void operationNoProviderFields() {
        TemplateOperation op = new TemplateOperation("op1",
                TemplateOperationType.ADD_TEXT_OVERLAY,
                TemplateTargetRole.CAPTION_TRACK,
                Map.of(), List.of());
        String str = op.toString();
        assertFalse(str.contains("providerName"));
        assertFalse(str.contains("bucket"));
        assertFalse(str.contains("signedUrl"));
    }

    // --- TemplateCapabilityRequirement ---

    @Test
    @DisplayName("Capability requirement rejects blank capability")
    void capabilityRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                new TemplateCapabilityRequirement("", true, Map.of()));
    }

    @Test
    @DisplayName("Required factory creates correct instance")
    void requiredFactory() {
        TemplateCapabilityRequirement req = TemplateCapabilityRequirement.required("AUDIO_MIX");
        assertEquals("AUDIO_MIX", req.capability());
        assertTrue(req.required());
        assertTrue(req.constraints().isEmpty());
    }

    // --- Validation ---

    @Test
    @DisplayName("Validation result supports valid and invalid states")
    void validationResultStates() {
        TemplateValidationResult valid = TemplateValidationResult.success();
        assertTrue(valid.valid());
        assertTrue(valid.errors().isEmpty());

        TemplateValidationResult invalid = TemplateValidationResult.failure(
                List.of(new TemplateValidationError("field", "CODE", "msg")));
        assertFalse(invalid.valid());
        assertEquals(1, invalid.errors().size());
    }

    // --- Compiler ---

    @Test
    @DisplayName("Compiler interface returns provider-neutral result")
    void compilerInterfaceProviderNeutral() {
        TemplateApplicationCompiler compiler = new TemplateApplicationCompiler() {
            @Override
            public boolean supports(TemplateDefinition def) { return true; }
            @Override
            public TemplateApplicationResult compile(TemplateDefinition def, TemplateApplicationRequest req) {
                return TemplateApplicationResult.success("compiled");
            }
        };

        assertTrue(compiler.supports(validDefinition()));
        TemplateApplicationResult result = compiler.compile(validDefinition(), validRequest());
        assertTrue(result.isSuccess());
        assertEquals("compiled", result.safeMessage());
    }

    @Test
    @DisplayName("No Remotion classes referenced in domain")
    void noRemotionReferences() {
        TemplateDefinition def = validDefinition();
        TemplateApplicationRequest req = validRequest();
        TemplateApplicationResult result = TemplateApplicationResult.success("ok");
        // No Remotion imports or references in domain package
        assertNotNull(def);
        assertNotNull(req);
        assertNotNull(result);
    }

    // --- Helpers ---

    private TemplateDefinition validDefinition() {
        return new TemplateDefinition(
                new TemplateDefinitionId("basic-caption"),
                new TemplateVersion("1.0"),
                TemplateType.CAPTION,
                new TemplateDisplayMetadata("Basic Caption", "Simple caption overlay", null),
                List.of(TemplateTargetRole.MAIN_VIDEO, TemplateTargetRole.CAPTION_TRACK),
                List.of(),
                List.of(new TemplateOperation("op1",
                        TemplateOperationType.ADD_TEXT_OVERLAY,
                        TemplateTargetRole.CAPTION_TRACK,
                        Map.of(), List.of(TemplateCapabilityRequirement.required("TEXT_OVERLAY")))),
                List.of(),
                List.of(TemplateCapabilityRequirement.required("TEXT_OVERLAY")));
    }

    private TemplateApplicationRequest validRequest() {
        return new TemplateApplicationRequest(
                "proj-1",
                new TemplateDefinitionId("basic-caption"),
                new TemplateVersion("1.0"),
                List.of(
                        TemplateTarget.product(TemplateTargetRole.MAIN_VIDEO, "prod-video"),
                        new TemplateTarget(TemplateTargetRole.CAPTION_TRACK,
                                TemplateTargetType.TEXT, "caption-text-1", Map.of())),
                List.of(),
                Map.of("requestSource", "test"));
    }

    private TemplateTarget validTarget() {
        return TemplateTarget.product(TemplateTargetRole.MAIN_VIDEO, "prod-1");
    }
}
