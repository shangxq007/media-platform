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
        // identity -> artifact: required for project asset listing during import/export
        "identity' depends on named interface(s) 'artifact",
        // identity -> storage: required for project asset storage during import/export
        "identity' depends on named interface(s) 'storage",
        // render -> outbox: render module uses outbox coordination for task execution, marketplace, search
        "render' depends on module 'outbox",
        // render -> outbox app: render uses OutboxEventService for event publishing
        "render' depends on named interface(s) 'outbox",
        // render -> storage infrastructure: render needs S3ObjectMaterializer/Writer for artifact I/O
        "render' depends on named interface(s) 'storage :: infrastructure",
        // PMPR-ST1: render consumes canonical storage contracts (ContentDigest/StorageObjectId/...)
        // through storage authority — LEGAL target dependency (STORAGE_SPI_BELONGS_TO_STORAGE_AUTHORITY)
        "render' depends on named interface(s) 'storage :: contract",
        // PMPR-ST1-CRR1: web StorageRuntimeController consumes storage contracts through storage authority
        "web' depends on named interface(s) 'storage :: contract",
        // web -> render: web controllers delegate to render app/domain services
        "web' depends on module 'render",
        // web -> outbox: ProjectDashboardController uses OutboxEventService
        "web' depends on named interface(s) 'outbox",
        // web -> ingest: DevIngestPreflightPolicyDiagnosticsController uses ingest diagnostics
        "web' depends on module 'ingest",
        // web -> storage: DevStorageDeliveryProfileDiagnosticsController uses storage diagnostics
        "web' depends on module 'storage",
        // root -> ingest non-exposed types: PlatformBeanConfiguration references ingest config properties
        "root:com.example.platform' depends on non-exposed type",
        // Migration: typedschema jooq-tables are now used across modules for typed SQL generation
        "depends on named interface(s) 'typedschema :: jooq-tables'",
        // Migration: ingest module uses non-exposed typed schema record types
        "depends on non-exposed type com.example.platform.typedschema.jooq.generated.tables.records",
        // W2 V1 (USER_WORKFLOW_DEFINITION_V1_CONTRACT_V2): the frozen conditional
        // path activated workflow-module -> platform-algorithms:graph (graph
        // kernel reuse for deterministic validation). The graph module is a
        // deliberately non-Spring library module (no @ApplicationModule, no
        // named interfaces), so Modulith cannot express the dependency in
        // allowedDependencies; registered here per the debt-register pattern.
        "workflow' depends on module 'graph",
        // PMPR-S1: render providers implement canonical PluginRuntimeProviderBinding
        // (extension::runtime) — provider effect binding under single PluginRuntime authority.
        "render' depends on named interface(s) 'extension :: runtime",
        // PMPR-S1: lifecycle graceful-shutdown coordinator holds optional SandboxRuntimeService
        // (sandbox runtime moved into extension::runtime sandbox package) — declared also in
        // lifecycle allowedDependencies; Modulith reports nested named-interface subpackage at
        // module level, so registered here per debt-register pattern.
        "lifecycle' depends on module 'extension",
        // K2 (K2-04): cost ports (CostEstimationPort/BudgetGuardPort + nested results) rehomed
        // from shared-kernel to billing::app — entitlement consumes the cost-facing contract at
        // the billing owner (EUMF usage/cost semantics preserved at billing).
        "entitlement' depends on named interface(s) 'billing :: app",
        // ROADMAP_19 (C1/C34): render (Timeline) consumes font-text-module value
        // semantics for the Timeline-owned TextElement (frozen direction:
        // Render -> FontText; pure domain, zero outward deps).
        "render' depends on module 'fonttext",
        // ROADMAP_19 CORR-2: web MCP controller supplies explicit SRT import font
        // policy (application-layer); font-text value semantics consumed at API boundary.
        "web' depends on module 'fonttext",
        // K2 (K2-11): NotificationEventPublisher rehomed from shared-kernel to notification;
        // outbox implements the notification publisher port (outbox -> notification, leaf owner).
        "outbox' depends on non-exposed type com.example.platform.notification.app.NotificationEventPublisher",
        // K2 (K2-11): render consumes NotificationEventPublisher at its notification owner.
        "render' depends on module 'notification",
        // K2 (K2-05): EntitlementPort rehomed from shared-kernel to entitlement::app — render
        // consumes the entitlement validation contract at its owner (render -> entitlement was
        // already an allowed module edge; the port now lives in the app named interface).
        "render' depends on named interface(s) 'entitlement :: app",
        // MCMV2-C (F1-F4): render -> media is the frozen dependency direction
        // (Timeline/Render/Workflow/AI/Delivery/Adapter -> Media Canonical Model).
        // MediaProbeController/MediaAssetProbeService consume the canonical media model;
        // the direction is additionally enforced by check-architecture-drift.sh.
        "render' depends on module 'media",
        // AUDIO_V2 (frozen: render -> audio): TimelineDocument carries the canonical
        // AudioMix reference (A3); audio-module is pure domain with no reverse dependency.
        "render' depends on module 'audio",
        // GCR-1 (frozen directions: Render -> Timeline -> Operation; Media/Audio/
        // FontText -> Timeline -> Operation): the new canonical modules consume
        // upstream value semantics. audio/fonttext/media modules are pure domain
        // with no named-interface exposure, so Modulith reports non-exposed-type
        // violations — same class as the pre-existing render entries above.
        "render' depends on module 'timeline",
        "render' depends on module 'operation",
        // GCR-2 reference-integrity dependency: Timeline validates immutable
        // ArtifactId + ContentDigest pins through the Artifact authority
        // (ArtifactQueryService) before revision commit. Bounded to Artifact-facing
        // query contracts; Artifact does not depend on Timeline (no cycle).
        "timeline' depends on module 'artifact",
        // GCR-2 render reconnect: Render is Artifact producer/consumer through
        // Artifact-owned services (ArtifactQueryService / ArtifactCatalogService /
        // ArtifactCommitService) and domain value types. Frozen direction
        // Render -> Artifact; artifact-module has no dependency on render.
        "render' depends on named interface(s) 'artifact :: domain",
        "render' depends on named interface(s) 'artifact :: app",
        "web' depends on module 'timeline",
        "timeline' depends on non-exposed type com.example.platform.audio",
        "timeline' depends on non-exposed type com.example.platform.fonttext",
        "timeline' depends on non-exposed type com.example.platform.media",
        "operation' depends on non-exposed type com.example.platform.timeline",
        "operation' depends on non-exposed type com.example.platform.audio",
        "operation' depends on non-exposed type com.example.platform.fonttext",
        // ROADMAP20 (C14/C30): render-planning consumes color-image-module value
        // semantics (ColorDescription, RasterSampleDescription) for typed output
        // requirements and delegates graph mechanics to platform-algorithms:graph.
        // Both are frozen dependency directions (Render -> ColorImage, Render -> Graph);
        // neither target depends on render. The graph module is a deliberately
        // non-Spring library module (no @ApplicationModule), so Modulith cannot
        // express it in allowedDependencies; registered here per debt-register pattern.
        "render' depends on module 'colorimage",
        "render' depends on module 'graph"
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
