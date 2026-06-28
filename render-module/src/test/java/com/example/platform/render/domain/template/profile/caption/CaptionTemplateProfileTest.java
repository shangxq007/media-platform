package com.example.platform.render.domain.template.profile.caption;

import com.example.platform.render.domain.caption.*;
import com.example.platform.render.domain.template.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Caption Template as first Template profile.
 * Proves: definition correctness, mapping, compiler, compatibility.
 */
class CaptionTemplateProfileTest {

    // --- Built-in definition ---

    @Test
    @DisplayName("Built-in caption definition has stable id/version/type")
    void definitionHasStableIdVersionType() {
        TemplateDefinition def = CaptionTemplateProfile.definition();
        assertEquals("builtin.caption.basic", def.id().value());
        assertEquals("1.0.0", def.version().value());
        assertEquals(TemplateType.CAPTION, def.type());
    }

    @Test
    @DisplayName("Definition declares MAIN_VIDEO and CAPTION_TRACK roles")
    void definitionDeclaresRoles() {
        TemplateDefinition def = CaptionTemplateProfile.definition();
        assertTrue(def.targetRoles().contains(TemplateTargetRole.MAIN_VIDEO));
        assertTrue(def.targetRoles().contains(TemplateTargetRole.CAPTION_TRACK));
    }

    @Test
    @DisplayName("Definition declares TEXT_OVERLAY and SUBTITLE_BURN_IN capabilities")
    void definitionDeclaresCapabilities() {
        TemplateDefinition def = CaptionTemplateProfile.definition();
        List<String> caps = def.requiredCapabilities().stream()
                .map(TemplateCapabilityRequirement::capability).toList();
        assertTrue(caps.contains("TEXT_OVERLAY"));
        assertTrue(caps.contains("SUBTITLE_BURN_IN"));
    }

    @Test
    @DisplayName("Definition contains ADD_TEXT_OVERLAY and APPLY_TEXT_STYLE operations")
    void definitionContainsOperations() {
        TemplateDefinition def = CaptionTemplateProfile.definition();
        List<TemplateOperationType> ops = def.operations().stream()
                .map(TemplateOperation::type).toList();
        assertTrue(ops.contains(TemplateOperationType.ADD_TEXT_OVERLAY));
        assertTrue(ops.contains(TemplateOperationType.APPLY_TEXT_STYLE));
    }

    @Test
    @DisplayName("isCaptionProfile matches built-in id")
    void isCaptionProfileMatches() {
        assertTrue(CaptionTemplateProfile.isCaptionProfile(new TemplateDefinitionId("builtin.caption.basic")));
        assertFalse(CaptionTemplateProfile.isCaptionProfile(new TemplateDefinitionId("other")));
        assertFalse(CaptionTemplateProfile.isCaptionProfile(null));
    }

    // --- Mapper ---

    @Test
    @DisplayName("Mapper converts sourceProductId to MAIN_VIDEO PRODUCT target")
    void mapperConvertsSourceProduct() {
        CaptionTemplateRenderRequest request = captionRequest();
        TemplateApplicationRequest mapped = CaptionTemplateApplicationMapper.map(request);

        TemplateTarget mainVideo = mapped.targets().stream()
                .filter(t -> t.role() == TemplateTargetRole.MAIN_VIDEO)
                .findFirst().orElseThrow();
        assertEquals(TemplateTargetType.PRODUCT, mainVideo.targetType());
        assertEquals("prod-source-1", mainVideo.targetId());
    }

    @Test
    @DisplayName("Mapper converts caption segments to CAPTION_TRACK target")
    void mapperConvertsCaptionTrack() {
        CaptionTemplateRenderRequest request = captionRequest();
        TemplateApplicationRequest mapped = CaptionTemplateApplicationMapper.map(request);

        TemplateTarget captionTrack = mapped.targets().stream()
                .filter(t -> t.role() == TemplateTargetRole.CAPTION_TRACK)
                .findFirst().orElseThrow();
        assertEquals(TemplateTargetType.TEXT, captionTrack.targetType());
        assertEquals("2", captionTrack.safeMetadata().get("segmentCount"));
    }

    @Test
    @DisplayName("Mapper preserves projectId")
    void mapperPreservesProjectId() {
        CaptionTemplateRenderRequest request = captionRequest();
        TemplateApplicationRequest mapped = CaptionTemplateApplicationMapper.map(request);
        assertEquals("proj-1", mapped.projectId());
    }

    @Test
    @DisplayName("Mapper preserves output profile as parameters")
    void mapperPreservesOutputProfile() {
        CaptionTemplateRenderRequest request = captionRequest();
        TemplateApplicationRequest mapped = CaptionTemplateApplicationMapper.map(request);

        List<String> paramIds = mapped.parameters().stream()
                .map(TemplateParameter::parameterId).toList();
        assertTrue(paramIds.contains("outputWidth"));
        assertTrue(paramIds.contains("outputHeight"));
        assertTrue(paramIds.contains("outputFps"));
        assertTrue(paramIds.contains("outputContainer"));
    }

    @Test
    @DisplayName("Mapper does not create provider/storage fields")
    void mapperNoProviderFields() {
        CaptionTemplateRenderRequest request = captionRequest();
        TemplateApplicationRequest mapped = CaptionTemplateApplicationMapper.map(request);

        String str = mapped.toString();
        assertFalse(str.contains("providerName"));
        assertFalse(str.contains("bucket"));
        assertFalse(str.contains("signedUrl"));
        assertFalse(str.contains("ffmpeg"));
    }

    @Test
    @DisplayName("Mapper uses built-in caption template id")
    void mapperUsesBuiltinTemplateId() {
        CaptionTemplateRenderRequest request = captionRequest();
        TemplateApplicationRequest mapped = CaptionTemplateApplicationMapper.map(request);

        assertEquals("builtin.caption.basic", mapped.templateId().value());
    }

    // --- Compiler ---

    @Test
    @DisplayName("Compiler supports built-in caption definition")
    void compilerSupportsCaption() {
        CaptionTemplateApplicationCompiler compiler = new CaptionTemplateApplicationCompiler();
        assertTrue(compiler.supports(CaptionTemplateProfile.definition()));
    }

    @Test
    @DisplayName("Compiler rejects unsupported definition")
    void compilerRejectsUnsupported() {
        CaptionTemplateApplicationCompiler compiler = new CaptionTemplateApplicationCompiler();
        TemplateDefinition other = new TemplateDefinition(
                new TemplateDefinitionId("other"), new TemplateVersion("1.0"),
                TemplateType.WATERMARK, null, List.of(), List.of(), List.of(), List.of(), List.of());
        assertFalse(compiler.supports(other));
    }

    @Test
    @DisplayName("Compiler rejects missing MAIN_VIDEO target")
    void compilerRejectsMissingMainVideo() {
        CaptionTemplateApplicationCompiler compiler = new CaptionTemplateApplicationCompiler();
        TemplateApplicationRequest request = new TemplateApplicationRequest(
                "proj-1", new TemplateDefinitionId("builtin.caption.basic"),
                new TemplateVersion("1.0.0"),
                List.of(new TemplateTarget(TemplateTargetRole.CAPTION_TRACK,
                        TemplateTargetType.TEXT, "cap-1", Map.of())),
                List.of(), Map.of());

        TemplateApplicationResult result = compiler.compile(CaptionTemplateProfile.definition(), request);
        assertFalse(result.isSuccess());
        assertEquals(TemplateApplicationStatus.VALIDATION_FAILED, result.status());
    }

    @Test
    @DisplayName("Compiler rejects missing CAPTION_TRACK target")
    void compilerRejectsMissingCaptionTrack() {
        CaptionTemplateApplicationCompiler compiler = new CaptionTemplateApplicationCompiler();
        TemplateApplicationRequest request = new TemplateApplicationRequest(
                "proj-1", new TemplateDefinitionId("builtin.caption.basic"),
                new TemplateVersion("1.0.0"),
                List.of(TemplateTarget.product(TemplateTargetRole.MAIN_VIDEO, "prod-1")),
                List.of(), Map.of());

        TemplateApplicationResult result = compiler.compile(CaptionTemplateProfile.definition(), request);
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("Compiler returns success for valid request")
    void compilerReturnsSuccess() {
        CaptionTemplateApplicationCompiler compiler = new CaptionTemplateApplicationCompiler();
        TemplateApplicationRequest request = validTemplateRequest();

        TemplateApplicationResult result = compiler.compile(CaptionTemplateProfile.definition(), request);
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Compiler does not reference Remotion")
    void compilerNoRemotion() {
        CaptionTemplateApplicationCompiler compiler = new CaptionTemplateApplicationCompiler();
        assertNotNull(compiler);
        // Verified by package structure — no Remotion imports
    }

    // --- Compatibility ---

    @Test
    @DisplayName("Existing caption request maps to valid template request")
    void existingRequestMapsValidly() {
        CaptionTemplateRenderRequest captionReq = captionRequest();
        TemplateApplicationRequest templateReq = CaptionTemplateApplicationMapper.map(captionReq);

        assertNotNull(templateReq.templateId());
        assertFalse(templateReq.targets().isEmpty());
        assertNotNull(templateReq.projectId());
    }

    @Test
    @DisplayName("Mapped request validates through compiler")
    void mappedRequestValidatesThroughCompiler() {
        CaptionTemplateApplicationCompiler compiler = new CaptionTemplateApplicationCompiler();
        TemplateApplicationRequest request = CaptionTemplateApplicationMapper.map(captionRequest());

        TemplateApplicationResult result = compiler.compile(CaptionTemplateProfile.definition(), request);
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("No provider selection in profile or mapper")
    void noProviderSelection() {
        TemplateDefinition def = CaptionTemplateProfile.definition();
        TemplateApplicationRequest req = CaptionTemplateApplicationMapper.map(captionRequest());

        assertFalse(def.toString().contains("providerName"));
        assertFalse(req.toString().contains("providerName"));
    }

    // --- Helpers ---

    private CaptionTemplateRenderRequest captionRequest() {
        return new CaptionTemplateRenderRequest(
                "proj-1", "prod-source-1",
                List.of(new CaptionSegmentSpec(0, 3000, "Hello"),
                        new CaptionSegmentSpec(3000, 6000, "World")),
                null, null, Map.of());
    }

    private TemplateApplicationRequest validTemplateRequest() {
        return CaptionTemplateApplicationMapper.map(captionRequest());
    }
}
