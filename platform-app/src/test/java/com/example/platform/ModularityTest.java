package com.example.platform;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

import java.util.List;

/** Zero-tolerance Modulith boundary check. See {@code docs/modulith-debt-register.md}. */
class ModularityTest {

    /**
     * Known pre-existing violations that have not yet been refactored.
     * Each entry documents: source module, target module, reason, and tracking issue.
     */
    private static final List<String> ALLOWED_VIOLATIONS = List.of(
        // identity -> storage: required for project asset storage during import/export
        // render -> outbox: render module records delivery coordination intents for marketplace/search
        // PMPR-ST1-CRR1: web StorageRuntimeController consumes storage contracts through storage authority
        // web -> render: web controllers delegate to render app/domain services
        // web -> outbox: ProjectDashboardController uses OutboxEventService
        // web -> ingest: DevIngestPreflightPolicyDiagnosticsController uses ingest diagnostics
        // web -> storage: DevStorageDeliveryProfileDiagnosticsController uses storage diagnostics
        // root -> ingest non-exposed types: PlatformBeanConfiguration references ingest config properties
        // Migration: typedschema jooq-tables are now used across modules for typed SQL generation
        // Migration: ingest module uses non-exposed typed schema record types
        // W2 V1 (USER_WORKFLOW_DEFINITION_V1_CONTRACT_V2): the frozen conditional
        // path activated workflow-module -> platform-algorithms:graph (graph
        // kernel reuse for deterministic validation). The graph module is a
        // deliberately non-Spring library module (no @ApplicationModule, no
        // named interfaces), so Modulith cannot express the dependency in
        // allowedDependencies; registered here per the debt-register pattern.
        "workflow' depends on module 'graph",
        // PMPR-S1: render providers implement canonical PluginRuntimeProviderBinding
        // (extension::runtime) — provider effect binding under single PluginRuntime authority.
        // ROADMAP_19 (C1/C34): render (Timeline) consumes font-text-module value
        // semantics for the Timeline-owned TextElement (frozen direction:
        // Render -> FontText; pure domain, zero outward deps).
        // ROADMAP_19 CORR-2: web MCP controller supplies explicit SRT import font
        // policy (application-layer); font-text value semantics consumed at API boundary.
        // MCMV2-C (F1-F4): render -> media is the frozen dependency direction
        // (Timeline/Render/Workflow/AI/Delivery/Adapter -> Media Canonical Model).
        // MediaProbeController/MediaAssetProbeService consume the canonical media model;
        // the direction is additionally enforced by check-architecture-drift.sh.
        // AUDIO_V2 (frozen: render -> audio): TimelineDocument carries the canonical
        // AudioMix reference (A3); audio-module is pure domain with no reverse dependency.
        // GCR-1 (frozen directions: Render -> Timeline -> Operation; Media/Audio/
        // FontText -> Timeline -> Operation): the new canonical modules consume
        // upstream value semantics. audio/fonttext/media modules are pure domain
        // with no named-interface exposure, so Modulith reports non-exposed-type
        // violations — same class as the pre-existing render entries above.
        // ROADMAP20 (C14/C30): render-planning consumes color-image-module value
        // semantics (ColorDescription, RasterSampleDescription) for typed output
        // requirements and delegates graph mechanics to platform-algorithms:graph.
        // Both are frozen dependency directions (Render -> ColorImage, Render -> Graph);
        // neither target depends on render. The graph module is a deliberately
        // non-Spring library module (no @ApplicationModule), so Modulith cannot
        // express it in allowedDependencies; registered here per debt-register pattern.
        "render' depends on module 'graph",
        // ROADMAP_22_EPOCH_3: adding worker-fabric to platform-app also makes the
        // media-execution-plan library visible to Modulith. These are its frozen
        // #21 render-plan/audio inputs and shared graph-mechanics dependencies.
        // The target packages are not exposed named interfaces, so retain the
        // repository debt-register convention with package-specific matches.
        "execution' depends on non-exposed type com.example.platform.graph"
    );

    @Test
    void modularityViolationsWithinBudget() {
        ApplicationModules modules = ApplicationModules.of(PlatformApplication.class);
        Violations violations = modules.detectViolations();
        int count = violations.getMessages().size();

        if (violations.hasViolations()) {
            // Filter out known allowed violations
            List<String> unexpectedViolations = violations.getMessages().stream()
                .filter(msg -> ALLOWED_VIOLATIONS.stream().noneMatch(msg::contains))
                .toList();

            System.err.println("Modulith violation messages (" + count + "): " + violations.getMessages());
            System.err.println("Unexpected violations: " + unexpectedViolations);

            assertTrue(
                unexpectedViolations.isEmpty(),
                "Unexpected Modulith violations (messages=" + unexpectedViolations.size() + "): " + unexpectedViolations);
        }
    }
}
