package com.example.platform.render.domain.visual;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Visual Capability Contract.
 * Proves: capability classification, status model, provider support, safety rules, forbidden capabilities.
 */
class VisualCapabilityContractTest {

    // --- Stage 1: Capability IDs and Categories ---

    @Test @DisplayName("VisualCapabilityId rejects null")
    void capabilityIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> new VisualCapabilityId(null));
    }

    @Test @DisplayName("VisualCapabilityId rejects blank")
    void capabilityIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new VisualCapabilityId("  "));
    }

    @Test @DisplayName("All required categories exist")
    void allCategoriesExist() {
        assertNotNull(VisualCapabilityCategory.CAPTION);
        assertNotNull(VisualCapabilityCategory.WATERMARK);
        assertNotNull(VisualCapabilityCategory.OVERLAY);
        assertNotNull(VisualCapabilityCategory.EFFECT);
        assertNotNull(VisualCapabilityCategory.TRANSITION);
        assertNotNull(VisualCapabilityCategory.COLOR);
        assertNotNull(VisualCapabilityCategory.TRANSFORM);
        assertNotNull(VisualCapabilityCategory.AUDIO);
        assertNotNull(VisualCapabilityCategory.PACKAGING);
    }

    // --- Stage 2: Capability Status Model ---

    @Test @DisplayName("All required statuses exist")
    void allStatusesExist() {
        assertNotNull(VisualCapabilityStatus.PRODUCTION);
        assertNotNull(VisualCapabilityStatus.BASELINE_CANDIDATE);
        assertNotNull(VisualCapabilityStatus.POC);
        assertNotNull(VisualCapabilityStatus.SPIKE);
        assertNotNull(VisualCapabilityStatus.FUTURE);
        assertNotNull(VisualCapabilityStatus.RESTRICTED);
        assertNotNull(VisualCapabilityStatus.FORBIDDEN);
        assertNotNull(VisualCapabilityStatus.DEPRECATED);
    }

    @Test @DisplayName("PRODUCTION is production allowed")
    void productionIsProductionAllowed() {
        assertTrue(VisualCapabilityStatus.PRODUCTION.isProductionAllowed());
    }

    @Test @DisplayName("POC is not production allowed")
    void pocIsNotProductionAllowed() {
        assertFalse(VisualCapabilityStatus.POC.isProductionAllowed());
    }

    @Test @DisplayName("FORBIDDEN is not production allowed")
    void forbiddenIsNotProductionAllowed() {
        assertFalse(VisualCapabilityStatus.FORBIDDEN.isProductionAllowed());
    }

    @Test @DisplayName("PRODUCTION is auto-dispatch allowed")
    void productionIsAutoDispatchAllowed() {
        assertTrue(VisualCapabilityStatus.PRODUCTION.isAutoDispatchAllowed());
    }

    @Test @DisplayName("BASELINE_CANDIDATE is auto-dispatch allowed")
    void baselineCandidateIsAutoDispatchAllowed() {
        assertTrue(VisualCapabilityStatus.BASELINE_CANDIDATE.isAutoDispatchAllowed());
    }

    @Test @DisplayName("POC is not auto-dispatch allowed")
    void pocIsNotAutoDispatchAllowed() {
        assertFalse(VisualCapabilityStatus.POC.isAutoDispatchAllowed());
    }

    // --- Stage 3: Effect Capability Profile ---

    @Test @DisplayName("Baseline effect capabilities exist")
    void baselineEffectCapabilitiesExist() {
        assertNotNull(EffectCapabilityProfile.scale());
        assertNotNull(EffectCapabilityProfile.crop());
        assertNotNull(EffectCapabilityProfile.fit());
        assertNotNull(EffectCapabilityProfile.fill());
        assertNotNull(EffectCapabilityProfile.contain());
        assertNotNull(EffectCapabilityProfile.rotate());
        assertNotNull(EffectCapabilityProfile.opacity());
        assertNotNull(EffectCapabilityProfile.fadeIn());
        assertNotNull(EffectCapabilityProfile.fadeOut());
        assertNotNull(EffectCapabilityProfile.textOverlay());
        assertNotNull(EffectCapabilityProfile.imageOverlay());
        assertNotNull(EffectCapabilityProfile.captionOverlay());
        assertNotNull(EffectCapabilityProfile.watermarkOverlay());
    }

    @Test @DisplayName("Baseline effects are PRODUCTION")
    void baselineEffectsAreProduction() {
        assertEquals(VisualCapabilityStatus.PRODUCTION, EffectCapabilityProfile.scale().status());
        assertEquals(VisualCapabilityStatus.PRODUCTION, EffectCapabilityProfile.crop().status());
        assertEquals(VisualCapabilityStatus.PRODUCTION, EffectCapabilityProfile.fadeIn().status());
        assertEquals(VisualCapabilityStatus.PRODUCTION, EffectCapabilityProfile.captionOverlay().status());
    }

    @Test @DisplayName("POC effect capabilities exist")
    void pocEffectCapabilitiesExist() {
        assertNotNull(EffectCapabilityProfile.blur());
        assertNotNull(EffectCapabilityProfile.colorAdjust());
        assertNotNull(EffectCapabilityProfile.brightness());
        assertNotNull(EffectCapabilityProfile.contrast());
        assertNotNull(EffectCapabilityProfile.saturation());
        assertNotNull(EffectCapabilityProfile.volumeAdjust());
        assertNotNull(EffectCapabilityProfile.audioFadeIn());
        assertNotNull(EffectCapabilityProfile.audioFadeOut());
        assertNotNull(EffectCapabilityProfile.pictureInPicture());
        assertNotNull(EffectCapabilityProfile.backgroundBlur());
    }

    @Test @DisplayName("POC effects are not production allowed")
    void pocEffectsAreNotProductionAllowed() {
        assertFalse(EffectCapabilityProfile.blur().isProductionAllowed());
        assertFalse(EffectCapabilityProfile.brightness().isProductionAllowed());
        assertFalse(EffectCapabilityProfile.pictureInPicture().isProductionAllowed());
    }

    @Test @DisplayName("Forbidden effect capabilities exist")
    void forbiddenEffectCapabilitiesExist() {
        assertNotNull(EffectCapabilityProfile.arbitraryFfmpegFiltergraph());
        assertNotNull(EffectCapabilityProfile.arbitraryShader());
        assertNotNull(EffectCapabilityProfile.arbitraryScriptEffect());
        assertNotNull(EffectCapabilityProfile.arbitraryOfxPlugin());
        assertNotNull(EffectCapabilityProfile.natronNodeGraph());
        assertNotNull(EffectCapabilityProfile.blenderCompositorGraph());
        assertNotNull(EffectCapabilityProfile.remotionComponentExecution());
        assertNotNull(EffectCapabilityProfile.userDefinedRenderDag());
        assertNotNull(EffectCapabilityProfile.pluginInsertedRenderNode());
        assertNotNull(EffectCapabilityProfile.providerSpecificRawCommand());
    }

    @Test @DisplayName("Forbidden effects are not production allowed")
    void forbiddenEffectsAreNotProductionAllowed() {
        assertFalse(EffectCapabilityProfile.arbitraryFfmpegFiltergraph().isProductionAllowed());
        assertFalse(EffectCapabilityProfile.remotionComponentExecution().isProductionAllowed());
        assertFalse(EffectCapabilityProfile.userDefinedRenderDag().isProductionAllowed());
    }

    @Test @DisplayName("Arbitrary FFmpeg filtergraph is FORBIDDEN")
    void arbitraryFfmpegFiltergraphIsForbidden() {
        assertEquals(VisualCapabilityStatus.FORBIDDEN, EffectCapabilityProfile.arbitraryFfmpegFiltergraph().status());
        assertEquals(VisualCapabilitySafetyLevel.FORBIDDEN, EffectCapabilityProfile.arbitraryFfmpegFiltergraph().safetyLevel());
    }

    @Test @DisplayName("Remotion component execution is FORBIDDEN")
    void remotionComponentExecutionIsForbidden() {
        assertEquals(VisualCapabilityStatus.FORBIDDEN, EffectCapabilityProfile.remotionComponentExecution().status());
    }

    @Test @DisplayName("User-defined Render DAG is FORBIDDEN")
    void userDefinedRenderDagIsForbidden() {
        assertEquals(VisualCapabilityStatus.FORBIDDEN, EffectCapabilityProfile.userDefinedRenderDag().status());
    }

    @Test @DisplayName("Effect production-allowed list contains only PRODUCTION")
    void effectProductionAllowedListContainsOnlyProduction() {
        List<VisualCapabilityDefinition> allowed = EffectCapabilityProfile.productionAllowed();
        assertFalse(allowed.isEmpty());
        for (var cap : allowed) {
            assertEquals(VisualCapabilityStatus.PRODUCTION, cap.status(),
                    "Expected PRODUCTION for " + cap.id().value());
        }
    }

    // --- Stage 4: Transition Capability Profile ---

    @Test @DisplayName("Baseline transition capabilities exist")
    void baselineTransitionCapabilitiesExist() {
        assertNotNull(TransitionCapabilityProfile.cut());
        assertNotNull(TransitionCapabilityProfile.fade());
        assertNotNull(TransitionCapabilityProfile.crossfade());
        assertNotNull(TransitionCapabilityProfile.dissolve());
    }

    @Test @DisplayName("Baseline transitions are PRODUCTION")
    void baselineTransitionsAreProduction() {
        assertEquals(VisualCapabilityStatus.PRODUCTION, TransitionCapabilityProfile.cut().status());
        assertEquals(VisualCapabilityStatus.PRODUCTION, TransitionCapabilityProfile.fade().status());
        assertEquals(VisualCapabilityStatus.PRODUCTION, TransitionCapabilityProfile.crossfade().status());
        assertEquals(VisualCapabilityStatus.PRODUCTION, TransitionCapabilityProfile.dissolve().status());
    }

    @Test @DisplayName("POC transition capabilities exist")
    void pocTransitionCapabilitiesExist() {
        assertNotNull(TransitionCapabilityProfile.slide());
        assertNotNull(TransitionCapabilityProfile.wipe());
        assertNotNull(TransitionCapabilityProfile.push());
        assertNotNull(TransitionCapabilityProfile.zoom());
    }

    @Test @DisplayName("POC transitions are not production allowed")
    void pocTransitionsAreNotProductionAllowed() {
        assertFalse(TransitionCapabilityProfile.slide().isProductionAllowed());
        assertFalse(TransitionCapabilityProfile.wipe().isProductionAllowed());
    }

    @Test @DisplayName("Forbidden transition capabilities exist")
    void forbiddenTransitionCapabilitiesExist() {
        assertNotNull(TransitionCapabilityProfile.shaderTransition());
        assertNotNull(TransitionCapabilityProfile.arbitraryTransitionPlugin());
        assertNotNull(TransitionCapabilityProfile.userDefinedTransitionGraph());
        assertNotNull(TransitionCapabilityProfile.providerSpecificTransitionGraph());
    }

    @Test @DisplayName("Forbidden transitions are not production allowed")
    void forbiddenTransitionsAreNotProductionAllowed() {
        assertFalse(TransitionCapabilityProfile.shaderTransition().isProductionAllowed());
        assertFalse(TransitionCapabilityProfile.userDefinedTransitionGraph().isProductionAllowed());
    }

    // --- Stage 5: Consistency Model ---

    @Test @DisplayName("All consistency levels exist")
    void allConsistencyLevelsExist() {
        assertNotNull(VisualConsistencyLevel.EXACT);
        assertNotNull(VisualConsistencyLevel.APPROX);
        assertNotNull(VisualConsistencyLevel.PROVIDER_SPECIFIC);
        assertNotNull(VisualConsistencyLevel.UNSUPPORTED);
        assertNotNull(VisualConsistencyLevel.FORBIDDEN);
        assertNotNull(VisualConsistencyLevel.UNKNOWN);
    }

    // --- Stage 6: Fallback Model ---

    @Test @DisplayName("All fallback behaviors exist")
    void allFallbackBehaviorsExist() {
        assertNotNull(VisualFallbackBehavior.NO_FALLBACK);
        assertNotNull(VisualFallbackBehavior.CUT);
        assertNotNull(VisualFallbackBehavior.FADE_OUT_IN);
        assertNotNull(VisualFallbackBehavior.DISABLE_EFFECT);
        assertNotNull(VisualFallbackBehavior.REJECT_REQUEST);
        assertNotNull(VisualFallbackBehavior.MANUAL_REVIEW_REQUIRED);
        assertNotNull(VisualFallbackBehavior.PROVIDER_SPECIFIC_ONLY);
    }

    // --- Stage 7: Provider Visual Support ---

    @Test @DisplayName("FFmpeg/libass baseline support can be represented")
    void ffmpegBaselineSupportCanBeRepresented() {
        ProviderVisualCapabilitySupport support = new ProviderVisualCapabilitySupport(
                "ffmpeg", new VisualCapabilityId("CAPTION_OVERLAY"),
                VisualCapabilityCategory.CAPTION,
                VisualCapabilityStatus.PRODUCTION,
                VisualConsistencyLevel.EXACT,
                VisualFallbackBehavior.NO_FALLBACK,
                true, true, Map.of());

        assertTrue(support.isProductionEligible());
        assertTrue(support.isAutoDispatchEligible());
        assertEquals("ffmpeg", support.providerId());
    }

    @Test @DisplayName("Remotion is not production allowed")
    void remotionIsNotProductionAllowed() {
        ProviderVisualCapabilitySupport support = new ProviderVisualCapabilitySupport(
                "remotion", new VisualCapabilityId("REMOTION_COMPONENT_EXECUTION"),
                VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.FORBIDDEN,
                VisualConsistencyLevel.FORBIDDEN,
                VisualFallbackBehavior.REJECT_REQUEST,
                false, false, Map.of());

        assertFalse(support.isProductionEligible());
        assertFalse(support.isAutoDispatchEligible());
    }

    @Test @DisplayName("POC provider is not auto-dispatch allowed")
    void pocProviderIsNotAutoDispatchAllowed() {
        ProviderVisualCapabilitySupport support = new ProviderVisualCapabilitySupport(
                "natron", new VisualCapabilityId("NATRON_NODE_GRAPH"),
                VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.POC,
                VisualConsistencyLevel.PROVIDER_SPECIFIC,
                VisualFallbackBehavior.REJECT_REQUEST,
                false, false, Map.of());

        assertFalse(support.isAutoDispatchEligible());
        assertFalse(support.isProductionEligible());
    }

    @Test @DisplayName("FORBIDDEN capability cannot be auto-dispatch allowed")
    void forbiddenCapabilityCannotBeAutoDispatchAllowed() {
        ProviderVisualCapabilitySupport support = new ProviderVisualCapabilitySupport(
                "test", new VisualCapabilityId("ARBITRARY_FFMPEG_FILTERGRAPH"),
                VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.FORBIDDEN,
                VisualConsistencyLevel.FORBIDDEN,
                VisualFallbackBehavior.REJECT_REQUEST,
                true, true, Map.of()); // even if declared true

        assertFalse(support.isAutoDispatchEligible());
        assertFalse(support.isProductionEligible());
    }

    @Test @DisplayName("Provider consistency level is explicit")
    void providerConsistencyLevelIsExplicit() {
        ProviderVisualCapabilitySupport support = new ProviderVisualCapabilitySupport(
                "ffmpeg", new VisualCapabilityId("BLUR"),
                VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.POC,
                VisualConsistencyLevel.APPROX,
                VisualFallbackBehavior.DISABLE_EFFECT,
                false, false, Map.of());

        assertEquals(VisualConsistencyLevel.APPROX, support.consistencyLevel());
    }

    @Test @DisplayName("Fallback behavior is explicit")
    void fallbackBehaviorIsExplicit() {
        ProviderVisualCapabilitySupport support = new ProviderVisualCapabilitySupport(
                "ffmpeg", new VisualCapabilityId("CROSSFADE"),
                VisualCapabilityCategory.TRANSITION,
                VisualCapabilityStatus.PRODUCTION,
                VisualConsistencyLevel.EXACT,
                VisualFallbackBehavior.FADE_OUT_IN,
                true, true, Map.of());

        assertEquals(VisualFallbackBehavior.FADE_OUT_IN, support.fallbackBehavior());
    }

    // --- Stage 8: Provider Matrix ---

    @Test @DisplayName("Provider matrix can find support")
    void providerMatrixCanFindSupport() {
        ProviderVisualCapabilitySupport support = new ProviderVisualCapabilitySupport(
                "ffmpeg", new VisualCapabilityId("SCALE"),
                VisualCapabilityCategory.TRANSFORM,
                VisualCapabilityStatus.PRODUCTION,
                VisualConsistencyLevel.EXACT,
                VisualFallbackBehavior.NO_FALLBACK,
                true, true, Map.of());

        ProviderVisualCapabilityMatrix matrix = new ProviderVisualCapabilityMatrix(
                List.of(support), Map.of());

        assertTrue(matrix.findSupport("ffmpeg", new VisualCapabilityId("SCALE")).isPresent());
        assertFalse(matrix.findSupport("ffmpeg", new VisualCapabilityId("BLUR")).isPresent());
    }

    @Test @DisplayName("Matrix has forbidden capabilities check")
    void matrixHasForbiddenCapabilitiesCheck() {
        ProviderVisualCapabilitySupport forbidden = new ProviderVisualCapabilitySupport(
                "test", new VisualCapabilityId("ARBITRARY_FFMPEG_FILTERGRAPH"),
                VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.FORBIDDEN,
                VisualConsistencyLevel.FORBIDDEN,
                VisualFallbackBehavior.REJECT_REQUEST,
                false, false, Map.of());

        ProviderVisualCapabilityMatrix matrix = new ProviderVisualCapabilityMatrix(
                List.of(forbidden), Map.of());

        assertTrue(matrix.hasForbiddenCapabilities());
    }

    // --- Stage 9: Policy and Validation ---

    @Test @DisplayName("Forbidden capability is rejected by policy")
    void forbiddenCapabilityIsRejectedByPolicy() {
        VisualCapabilityDefinition cap = EffectCapabilityProfile.arbitraryFfmpegFiltergraph();
        assertTrue(VisualCapabilityPolicy.isForbidden(cap));
        assertFalse(VisualCapabilityPolicy.mayBeProductionEligible(cap));
    }

    @Test @DisplayName("Restricted capability requires manual review")
    void restrictedCapabilityRequiresManualReview() {
        VisualCapabilityDefinition cap = new VisualCapabilityDefinition(
                new VisualCapabilityId("RESTRICTED_TEST"),
                VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.RESTRICTED,
                "Restricted Test", "Test",
                VisualConsistencyLevel.UNKNOWN, VisualFallbackBehavior.MANUAL_REVIEW_REQUIRED,
                VisualCapabilitySafetyLevel.RESTRICTED,
                List.of(), List.of(), Map.of());

        assertTrue(VisualCapabilityPolicy.requiresManualReview(cap));
        assertFalse(VisualCapabilityPolicy.mayBeProductionEligible(cap));
    }

    @Test @DisplayName("POC capability is internal-only")
    void pocCapabilityIsInternalOnly() {
        VisualCapabilityDefinition cap = EffectCapabilityProfile.blur();
        assertTrue(VisualCapabilityPolicy.isInternalOnly(cap));
    }

    @Test @DisplayName("Production capability may be eligible")
    void productionCapabilityMayBeEligible() {
        VisualCapabilityDefinition cap = EffectCapabilityProfile.scale();
        assertTrue(VisualCapabilityPolicy.mayBeProductionEligible(cap));
    }

    @Test @DisplayName("Unknown provider support is not production allowed")
    void unknownProviderSupportIsNotProductionAllowed() {
        ProviderVisualCapabilitySupport support = new ProviderVisualCapabilitySupport(
                "unknown", new VisualCapabilityId("SCALE"),
                VisualCapabilityCategory.TRANSFORM,
                VisualCapabilityStatus.POC,
                VisualConsistencyLevel.UNKNOWN,
                VisualFallbackBehavior.REJECT_REQUEST,
                false, false, Map.of());

        assertFalse(VisualCapabilityPolicy.isProductionAllowed(support));
    }

    @Test @DisplayName("Safe metadata only")
    void safeMetadataOnly() {
        ProviderVisualCapabilitySupport support = new ProviderVisualCapabilitySupport(
                "ffmpeg", new VisualCapabilityId("SCALE"),
                VisualCapabilityCategory.TRANSFORM,
                VisualCapabilityStatus.PRODUCTION,
                VisualConsistencyLevel.EXACT,
                VisualFallbackBehavior.NO_FALLBACK,
                true, true, Map.of("key", "value"));

        assertNotNull(support.safeMetadata());
        assertEquals("value", support.safeMetadata().get("key"));
    }

    // --- Stage 10: Validator ---

    @Test @DisplayName("Validator rejects null definition")
    void validatorRejectsNullDefinition() {
        List<VisualCapabilityIssue> issues = VisualCapabilityValidator.validateDefinition(null);
        assertFalse(issues.isEmpty());
    }

    @Test @DisplayName("Validator rejects forbidden definition")
    void validatorRejectsForbiddenDefinition() {
        List<VisualCapabilityIssue> issues = VisualCapabilityValidator.validateDefinition(
                EffectCapabilityProfile.arbitraryFfmpegFiltergraph());
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i ->
                i.code() == VisualCapabilityIssueCode.FORBIDDEN_CAPABILITY));
    }

    @Test @DisplayName("Validator accepts valid definition")
    void validatorAcceptsValidDefinition() {
        List<VisualCapabilityIssue> issues = VisualCapabilityValidator.validateDefinition(
                EffectCapabilityProfile.scale());
        assertTrue(issues.isEmpty());
    }

    @Test @DisplayName("Validator rejects null provider support")
    void validatorRejectsNullProviderSupport() {
        List<VisualCapabilityIssue> issues = VisualCapabilityValidator.validateProviderSupport(null);
        assertFalse(issues.isEmpty());
    }

    @Test @DisplayName("Validator warns on unknown consistency")
    void validatorWarnsOnUnknownConsistency() {
        ProviderVisualCapabilitySupport support = new ProviderVisualCapabilitySupport(
                "test", new VisualCapabilityId("SCALE"),
                VisualCapabilityCategory.TRANSFORM,
                VisualCapabilityStatus.PRODUCTION,
                VisualConsistencyLevel.UNKNOWN,
                VisualFallbackBehavior.NO_FALLBACK,
                true, true, Map.of());

        List<VisualCapabilityIssue> issues = VisualCapabilityValidator.validateProviderSupport(support);
        assertTrue(issues.stream().anyMatch(i ->
                i.code() == VisualCapabilityIssueCode.CONSISTENCY_LEVEL_UNKNOWN));
    }

    @Test @DisplayName("Validator checks forbidden metadata keywords")
    void validatorChecksForbiddenMetadataKeywords() {
        List<VisualCapabilityIssue> issues = VisualCapabilityValidator.checkForbiddenMetadata(
                Map.of("filter_complex", "some_value"));
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i ->
                i.code() == VisualCapabilityIssueCode.PROVIDER_INTERNAL_LEAK));
    }

    @Test @DisplayName("Validator accepts clean metadata")
    void validatorAcceptsCleanMetadata() {
        List<VisualCapabilityIssue> issues = VisualCapabilityValidator.checkForbiddenMetadata(
                Map.of("key", "value"));
        assertTrue(issues.isEmpty());
    }

    // --- Stage 11: Safety Rules ---

    @Test @DisplayName("No provider/storage fields in definitions")
    void noProviderStorageFieldsInDefinitions() {
        String repr = EffectCapabilityProfile.scale().toString();
        assertFalse(repr.contains("bucket"));
        assertFalse(repr.contains("objectKey"));
        assertFalse(repr.contains("signedUrl"));
        assertFalse(repr.contains("providerName"));
    }

    @Test @DisplayName("No provider/storage fields in support")
    void noProviderStorageFieldsInSupport() {
        ProviderVisualCapabilitySupport support = new ProviderVisualCapabilitySupport(
                "ffmpeg", new VisualCapabilityId("SCALE"),
                VisualCapabilityCategory.TRANSFORM,
                VisualCapabilityStatus.PRODUCTION,
                VisualConsistencyLevel.EXACT,
                VisualFallbackBehavior.NO_FALLBACK,
                true, true, Map.of());

        String repr = support.toString();
        assertFalse(repr.contains("bucket"));
        assertFalse(repr.contains("signedUrl"));
    }

    @Test @DisplayName("Issue fields are safe")
    void issueFieldsAreSafe() {
        VisualCapabilityIssue issue = VisualCapabilityIssue.blocking(
                VisualCapabilityIssueCode.FORBIDDEN_CAPABILITY, "Test issue");

        String repr = issue.toString();
        assertFalse(repr.contains("bucket"));
        assertFalse(repr.contains("signedUrl"));
        assertFalse(repr.contains("providerName"));
    }

    @Test @DisplayName("Capability definition has no provider internals")
    void capabilityDefinitionHasNoProviderInternals() {
        VisualCapabilityDefinition cap = EffectCapabilityProfile.scale();
        String repr = cap.toString();
        assertFalse(repr.contains("rawCommand"));
        assertFalse(repr.contains("shell command"));
        assertFalse(repr.contains("ProcessBuilder"));
    }

    @Test @DisplayName("Remotion component execution fallback is REJECT_REQUEST")
    void remotionComponentExecutionFallbackIsRejectRequest() {
        assertEquals(VisualFallbackBehavior.REJECT_REQUEST,
                EffectCapabilityProfile.remotionComponentExecution().defaultFallback());
    }

    @Test @DisplayName("Arbitrary FFmpeg filtergraph fallback is REJECT_REQUEST")
    void arbitraryFfmpegFiltergraphFallbackIsRejectRequest() {
        assertEquals(VisualFallbackBehavior.REJECT_REQUEST,
                EffectCapabilityProfile.arbitraryFfmpegFiltergraph().defaultFallback());
    }

    @Test @DisplayName("User-defined Render DAG fallback is REJECT_REQUEST")
    void userDefinedRenderDagFallbackIsRejectRequest() {
        assertEquals(VisualFallbackBehavior.REJECT_REQUEST,
                EffectCapabilityProfile.userDefinedRenderDag().defaultFallback());
    }

    @Test @DisplayName("Plugin-inserted Render Node fallback is REJECT_REQUEST")
    void pluginInsertedRenderNodeFallbackIsRejectRequest() {
        assertEquals(VisualFallbackBehavior.REJECT_REQUEST,
                EffectCapabilityProfile.pluginInsertedRenderNode().defaultFallback());
    }
}
