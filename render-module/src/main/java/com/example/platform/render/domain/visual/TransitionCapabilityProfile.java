package com.example.platform.render.domain.visual;

import java.util.List;
import java.util.Map;

/**
 * Transition capability profile — bounded set of transition capabilities.
 * Immutable. Internal domain model.
 */
public final class TransitionCapabilityProfile {

    private TransitionCapabilityProfile() {}

    // --- Baseline Candidates ---

    public static VisualCapabilityDefinition cut() {
        return definition("CUT", VisualCapabilityCategory.TRANSITION,
                VisualCapabilityStatus.PRODUCTION, "Cut",
                "Hard cut between clips — no transition effect",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.SAFE);
    }

    public static VisualCapabilityDefinition fade() {
        return definition("FADE", VisualCapabilityCategory.TRANSITION,
                VisualCapabilityStatus.PRODUCTION, "Fade",
                "Fade through black between clips",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.CUT,
                VisualCapabilitySafetyLevel.SAFE);
    }

    public static VisualCapabilityDefinition crossfade() {
        return definition("CROSSFADE", VisualCapabilityCategory.TRANSITION,
                VisualCapabilityStatus.PRODUCTION, "Crossfade",
                "Crossfade between clips",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.FADE_OUT_IN,
                VisualCapabilitySafetyLevel.SAFE);
    }

    public static VisualCapabilityDefinition dissolve() {
        return definition("DISSOLVE", VisualCapabilityCategory.TRANSITION,
                VisualCapabilityStatus.PRODUCTION, "Dissolve",
                "Dissolve between clips",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.FADE_OUT_IN,
                VisualCapabilitySafetyLevel.SAFE);
    }

    // --- POC Candidates ---

    public static VisualCapabilityDefinition slide() {
        return definition("SLIDE", VisualCapabilityCategory.TRANSITION,
                VisualCapabilityStatus.POC, "Slide",
                "Slide transition between clips",
                VisualConsistencyLevel.APPROX, VisualFallbackBehavior.CUT,
                VisualCapabilitySafetyLevel.VALIDATED);
    }

    public static VisualCapabilityDefinition wipe() {
        return definition("WIPE", VisualCapabilityCategory.TRANSITION,
                VisualCapabilityStatus.POC, "Wipe",
                "Wipe transition between clips",
                VisualConsistencyLevel.APPROX, VisualFallbackBehavior.CUT,
                VisualCapabilitySafetyLevel.VALIDATED);
    }

    public static VisualCapabilityDefinition push() {
        return definition("PUSH", VisualCapabilityCategory.TRANSITION,
                VisualCapabilityStatus.POC, "Push",
                "Push transition between clips",
                VisualConsistencyLevel.APPROX, VisualFallbackBehavior.CUT,
                VisualCapabilitySafetyLevel.VALIDATED);
    }

    public static VisualCapabilityDefinition zoom() {
        return definition("ZOOM", VisualCapabilityCategory.TRANSITION,
                VisualCapabilityStatus.POC, "Zoom",
                "Zoom transition between clips",
                VisualConsistencyLevel.APPROX, VisualFallbackBehavior.CUT,
                VisualCapabilitySafetyLevel.VALIDATED);
    }

    // --- Restricted / Future / Forbidden ---

    public static VisualCapabilityDefinition threeDTransition() {
        return definition("THREE_D_TRANSITION", VisualCapabilityCategory.TRANSITION,
                VisualCapabilityStatus.FUTURE, "3D Transition",
                "3D transition effect — FUTURE vocabulary only",
                VisualConsistencyLevel.UNKNOWN, VisualFallbackBehavior.REJECT_REQUEST,
                VisualCapabilitySafetyLevel.RESTRICTED);
    }

    public static VisualCapabilityDefinition shaderTransition() {
        return definition("SHADER_TRANSITION", VisualCapabilityCategory.TRANSITION,
                VisualCapabilityStatus.FORBIDDEN, "Shader Transition",
                "GPU shader transition — FORBIDDEN",
                VisualConsistencyLevel.FORBIDDEN, VisualFallbackBehavior.REJECT_REQUEST,
                VisualCapabilitySafetyLevel.FORBIDDEN);
    }

    public static VisualCapabilityDefinition arbitraryTransitionPlugin() {
        return definition("ARBITRARY_TRANSITION_PLUGIN", VisualCapabilityCategory.TRANSITION,
                VisualCapabilityStatus.FORBIDDEN, "Arbitrary Transition Plugin",
                "Arbitrary transition plugin — FORBIDDEN",
                VisualConsistencyLevel.FORBIDDEN, VisualFallbackBehavior.REJECT_REQUEST,
                VisualCapabilitySafetyLevel.FORBIDDEN);
    }

    public static VisualCapabilityDefinition userDefinedTransitionGraph() {
        return definition("USER_DEFINED_TRANSITION_GRAPH", VisualCapabilityCategory.TRANSITION,
                VisualCapabilityStatus.FORBIDDEN, "User-defined Transition Graph",
                "User-submitted transition graph — FORBIDDEN",
                VisualConsistencyLevel.FORBIDDEN, VisualFallbackBehavior.REJECT_REQUEST,
                VisualCapabilitySafetyLevel.FORBIDDEN);
    }

    public static VisualCapabilityDefinition providerSpecificTransitionGraph() {
        return definition("PROVIDER_SPECIFIC_TRANSITION_GRAPH", VisualCapabilityCategory.TRANSITION,
                VisualCapabilityStatus.FORBIDDEN, "Provider-specific Transition Graph",
                "Provider-specific transition graph — FORBIDDEN",
                VisualConsistencyLevel.FORBIDDEN, VisualFallbackBehavior.REJECT_REQUEST,
                VisualCapabilitySafetyLevel.FORBIDDEN);
    }

    // --- All capabilities ---

    public static List<VisualCapabilityDefinition> all() {
        return List.of(
                cut(), fade(), crossfade(), dissolve(),
                slide(), wipe(), push(), zoom(),
                threeDTransition(), shaderTransition(),
                arbitraryTransitionPlugin(), userDefinedTransitionGraph(),
                providerSpecificTransitionGraph());
    }

    public static List<VisualCapabilityDefinition> productionAllowed() {
        return all().stream().filter(VisualCapabilityDefinition::isProductionAllowed).toList();
    }

    public static List<VisualCapabilityDefinition> forbidden() {
        return all().stream().filter(d -> d.status() == VisualCapabilityStatus.FORBIDDEN).toList();
    }

    // --- Helpers ---

    private static VisualCapabilityDefinition definition(
            String id, VisualCapabilityCategory category, VisualCapabilityStatus status,
            String displayName, String description,
            VisualConsistencyLevel consistency, VisualFallbackBehavior fallback,
            VisualCapabilitySafetyLevel safety) {
        return new VisualCapabilityDefinition(
                new VisualCapabilityId(id), category, status,
                displayName, description, consistency, fallback, safety,
                List.of(), List.of(), Map.of());
    }
}
