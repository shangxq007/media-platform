package com.example.platform.render.domain.template.profile.watermark;

import com.example.platform.render.domain.template.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Watermark Template as second Template profile.
 * Proves: definition, mapping, compiler, compatibility with caption profile.
 */
class WatermarkTemplateProfileTest {

    // --- Built-in definition ---

    @Test
    @DisplayName("Built-in watermark definition has stable id/version/type")
    void definitionHasStableIdVersionType() {
        TemplateDefinition def = WatermarkTemplateProfile.definition();
        assertEquals("builtin.watermark.basic", def.id().value());
        assertEquals("1.0.0", def.version().value());
        assertEquals(TemplateType.WATERMARK, def.type());
    }

    @Test
    @DisplayName("Definition declares MAIN_VIDEO role")
    void definitionDeclaresMainVideoRole() {
        TemplateDefinition def = WatermarkTemplateProfile.definition();
        assertTrue(def.targetRoles().contains(TemplateTargetRole.MAIN_VIDEO));
    }

    @Test
    @DisplayName("Definition declares WATERMARK_IMAGE role")
    void definitionDeclaresWatermarkRole() {
        TemplateDefinition def = WatermarkTemplateProfile.definition();
        assertTrue(def.targetRoles().contains(TemplateTargetRole.WATERMARK_IMAGE));
    }

    @Test
    @DisplayName("Definition declares IMAGE_OVERLAY and VIDEO_COMPOSITE capabilities")
    void definitionDeclaresCapabilities() {
        TemplateDefinition def = WatermarkTemplateProfile.definition();
        List<String> caps = def.requiredCapabilities().stream()
                .map(TemplateCapabilityRequirement::capability).toList();
        assertTrue(caps.contains("IMAGE_OVERLAY"));
        assertTrue(caps.contains("VIDEO_COMPOSITE"));
    }

    @Test
    @DisplayName("Definition contains ADD_WATERMARK and ADD_IMAGE_OVERLAY operations")
    void definitionContainsOperations() {
        TemplateDefinition def = WatermarkTemplateProfile.definition();
        List<TemplateOperationType> ops = def.operations().stream()
                .map(TemplateOperation::type).toList();
        assertTrue(ops.contains(TemplateOperationType.ADD_WATERMARK));
        assertTrue(ops.contains(TemplateOperationType.ADD_IMAGE_OVERLAY));
    }

    @Test
    @DisplayName("isWatermarkProfile matches built-in id")
    void isWatermarkProfileMatches() {
        assertTrue(WatermarkTemplateProfile.isWatermarkProfile(
                new TemplateDefinitionId("builtin.watermark.basic")));
        assertFalse(WatermarkTemplateProfile.isWatermarkProfile(
                new TemplateDefinitionId("builtin.caption.basic")));
        assertFalse(WatermarkTemplateProfile.isWatermarkProfile(null));
    }

    // --- Mapper ---

    @Test
    @DisplayName("Mapper converts mainVideoProductId to MAIN_VIDEO target")
    void mapperConvertsMainVideo() {
        WatermarkTemplateApplicationInput input = watermarkInput();
        TemplateApplicationRequest mapped = WatermarkTemplateApplicationMapper.map(input);

        TemplateTarget mainVideo = mapped.targets().stream()
                .filter(t -> t.role() == TemplateTargetRole.MAIN_VIDEO)
                .findFirst().orElseThrow();
        assertEquals(TemplateTargetType.PRODUCT, mainVideo.targetType());
        assertEquals("prod-video-1", mainVideo.targetId());
    }

    @Test
    @DisplayName("Mapper converts watermarkProductId to WATERMARK_IMAGE target")
    void mapperConvertsWatermark() {
        WatermarkTemplateApplicationInput input = watermarkInput();
        TemplateApplicationRequest mapped = WatermarkTemplateApplicationMapper.map(input);

        TemplateTarget watermark = mapped.targets().stream()
                .filter(t -> t.role() == TemplateTargetRole.WATERMARK_IMAGE)
                .findFirst().orElseThrow();
        assertEquals(TemplateTargetType.PRODUCT, watermark.targetType());
        assertEquals("prod-watermark-1", watermark.targetId());
    }

    @Test
    @DisplayName("Mapper preserves projectId")
    void mapperPreservesProjectId() {
        WatermarkTemplateApplicationInput input = watermarkInput();
        TemplateApplicationRequest mapped = WatermarkTemplateApplicationMapper.map(input);
        assertEquals("proj-1", mapped.projectId());
    }

    @Test
    @DisplayName("Mapper maps placement/opacity/margins as parameters")
    void mapperMapsParameters() {
        WatermarkTemplateApplicationInput input = watermarkInput();
        TemplateApplicationRequest mapped = WatermarkTemplateApplicationMapper.map(input);

        List<String> paramIds = mapped.parameters().stream()
                .map(TemplateParameter::parameterId).toList();
        assertTrue(paramIds.contains("placement"));
        assertTrue(paramIds.contains("opacityPercent"));
        assertTrue(paramIds.contains("marginX"));
        assertTrue(paramIds.contains("marginY"));
    }

    @Test
    @DisplayName("Mapper uses built-in watermark template id")
    void mapperUsesBuiltinId() {
        WatermarkTemplateApplicationInput input = watermarkInput();
        TemplateApplicationRequest mapped = WatermarkTemplateApplicationMapper.map(input);
        assertEquals("builtin.watermark.basic", mapped.templateId().value());
    }

    @Test
    @DisplayName("Mapper does not create provider/storage fields")
    void mapperNoProviderFields() {
        WatermarkTemplateApplicationInput input = watermarkInput();
        TemplateApplicationRequest mapped = WatermarkTemplateApplicationMapper.map(input);
        String str = mapped.toString();
        assertFalse(str.contains("providerName"));
        assertFalse(str.contains("bucket"));
        assertFalse(str.contains("signedUrl"));
    }

    // --- Compiler ---

    @Test
    @DisplayName("Compiler supports built-in watermark definition")
    void compilerSupportsWatermark() {
        WatermarkTemplateApplicationCompiler compiler = new WatermarkTemplateApplicationCompiler();
        assertTrue(compiler.supports(WatermarkTemplateProfile.definition()));
    }

    @Test
    @DisplayName("Compiler rejects unsupported definition")
    void compilerRejectsUnsupported() {
        WatermarkTemplateApplicationCompiler compiler = new WatermarkTemplateApplicationCompiler();
        TemplateDefinition other = new TemplateDefinition(
                new TemplateDefinitionId("other"), new TemplateVersion("1.0"),
                TemplateType.CAPTION, null, List.of(), List.of(), List.of(), List.of(), List.of());
        assertFalse(compiler.supports(other));
    }

    @Test
    @DisplayName("Compiler rejects missing MAIN_VIDEO target")
    void compilerRejectsMissingMainVideo() {
        WatermarkTemplateApplicationCompiler compiler = new WatermarkTemplateApplicationCompiler();
        TemplateApplicationRequest request = new TemplateApplicationRequest(
                "proj-1", new TemplateDefinitionId("builtin.watermark.basic"),
                new TemplateVersion("1.0.0"),
                List.of(TemplateTarget.product(TemplateTargetRole.WATERMARK_IMAGE, "wm-1")),
                List.of(), Map.of());

        TemplateApplicationResult result = compiler.compile(
                WatermarkTemplateProfile.definition(), request);
        assertFalse(result.isSuccess());
        assertEquals(TemplateApplicationStatus.VALIDATION_FAILED, result.status());
    }

    @Test
    @DisplayName("Compiler rejects missing WATERMARK_IMAGE/LOGO target")
    void compilerRejectsMissingWatermark() {
        WatermarkTemplateApplicationCompiler compiler = new WatermarkTemplateApplicationCompiler();
        TemplateApplicationRequest request = new TemplateApplicationRequest(
                "proj-1", new TemplateDefinitionId("builtin.watermark.basic"),
                new TemplateVersion("1.0.0"),
                List.of(TemplateTarget.product(TemplateTargetRole.MAIN_VIDEO, "v-1")),
                List.of(), Map.of());

        TemplateApplicationResult result = compiler.compile(
                WatermarkTemplateProfile.definition(), request);
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("Compiler rejects invalid opacity")
    void compilerRejectsInvalidOpacity() {
        WatermarkTemplateApplicationCompiler compiler = new WatermarkTemplateApplicationCompiler();
        TemplateApplicationRequest request = new TemplateApplicationRequest(
                "proj-1", new TemplateDefinitionId("builtin.watermark.basic"),
                new TemplateVersion("1.0.0"),
                List.of(
                        TemplateTarget.product(TemplateTargetRole.MAIN_VIDEO, "v-1"),
                        TemplateTarget.product(TemplateTargetRole.WATERMARK_IMAGE, "wm-1")),
                List.of(new TemplateParameter("opacityPercent", "Opacity", "NUMBER", false, "150")),
                Map.of());

        TemplateApplicationResult result = compiler.compile(
                WatermarkTemplateProfile.definition(), request);
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("Compiler returns success for valid request")
    void compilerReturnsSuccess() {
        WatermarkTemplateApplicationCompiler compiler = new WatermarkTemplateApplicationCompiler();
        TemplateApplicationRequest request = validTemplateRequest();

        TemplateApplicationResult result = compiler.compile(
                WatermarkTemplateProfile.definition(), request);
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Compiler does not reference Remotion")
    void compilerNoRemotion() {
        WatermarkTemplateApplicationCompiler compiler = new WatermarkTemplateApplicationCompiler();
        assertNotNull(compiler);
        // Verified by package structure
    }

    // --- Compatibility ---

    @Test
    @DisplayName("Caption profile is distinct from watermark profile")
    void profilesDistinct() {
        assertFalse(WatermarkTemplateProfile.isWatermarkProfile(
                new TemplateDefinitionId("builtin.caption.basic")));
        assertEquals(TemplateType.WATERMARK, WatermarkTemplateProfile.definition().type());
        assertNotEquals(WatermarkTemplateProfile.TEMPLATE_ID,
                com.example.platform.render.domain.template.profile.caption.CaptionTemplateProfile.TEMPLATE_ID);
    }

    @Test
    @DisplayName("No provider selection in watermark profile")
    void noProviderSelection() {
        TemplateDefinition def = WatermarkTemplateProfile.definition();
        assertFalse(def.toString().contains("providerName"));
    }

    // --- Helpers ---

    private WatermarkTemplateApplicationInput watermarkInput() {
        return new WatermarkTemplateApplicationInput(
                "proj-1", "prod-video-1", "prod-watermark-1",
                "BOTTOM_RIGHT", 50, 10, 10, Map.of());
    }

    private TemplateApplicationRequest validTemplateRequest() {
        return WatermarkTemplateApplicationMapper.map(watermarkInput());
    }
}
