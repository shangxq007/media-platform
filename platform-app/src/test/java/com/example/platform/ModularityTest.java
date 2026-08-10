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
        "workflow' depends on module 'graph"
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
