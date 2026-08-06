package com.example.platform.workflow.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Frozen contract RED-10 / architecture rules AR-T1..AR-T9 + payload/reference
 * rule (W1-GAP-004/010).
 *
 * <p>ArchUnit is NOT a project dependency (dependency changes outside the
 * frozen allowlist are prohibited), so the frozen rules are enforced as
 * deterministic source-boundary assertions over the exact W1 paths — the same
 * mechanism the published P1 architecture test uses. The payload rule asserts
 * that W1 workflow/activity signatures carry only stable references (no raw
 * timeline JSON / large documents).</p>
 *
 * <ul>
 *   <li>AR-T1 workflow implementations do not import repositories</li>
 *   <li>AR-T2 workflow implementations do not perform network/filesystem/process I/O</li>
 *   <li>AR-T3 workflow implementations do not depend on render implementation classes</li>
 *   <li>AR-T4 workflow implementations do not write Timeline or Product state</li>
 *   <li>AR-T5 activities invoke application ports rather than domain persistence internals</li>
 *   <li>AR-T6 Temporal SDK types do not leak into unrelated public domain APIs</li>
 *   <li>AR-T7 workflow-module does not become a general business-authority module</li>
 *   <li>AR-T8 PluginRegistry remains independent of Temporal execution</li>
 *   <li>AR-T9 W2 user workflow definition remains absent</li>
 *   <li>AR-T10 payload/reference rule: no raw timeline JSON in workflow signatures</li>
 * </ul>
 */
class TemporalWorkflowArchitectureTest {

    private static final Path WORKFLOW_SRC =
            Path.of("src/main/java/com/example/platform/workflow");
    private static final Path RENDER_SRC =
            Path.of("../render-module/src/main/java/com/example/platform/render");
    private static final Path EXTENSION_SRC =
            Path.of("../extension-module/src/main/java/com/example/platform/extension");

    private static final String[] WORKFLOW_IMPLS = {
        "temporal/RenderWorkflowImpl.java",
        "temporal/RenderPipelineWorkflowImpl.java",
    };

    private static final String[] WORKFLOW_SIGNATURES = {
        "temporal/RenderWorkflow.java",
        "temporal/RenderActivities.java",
    };

    private static List<String> lines(Path base, String rel) {
        try {
            return Files.readAllLines(base.resolve(rel));
        } catch (IOException e) {
            fail("cannot read " + base.resolve(rel) + ": " + e.getMessage());
            return List.of();
        }
    }

    private static String imports(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            String t = l.trim();
            if (t.startsWith("import ")) {
                sb.append(t).append('\n');
            }
        }
        return sb.toString();
    }

    private static void assertNoImport(String imports, String fragment, String rule) {
        for (String line : imports.split("\n")) {
            if (line.contains(fragment)) {
                fail(rule + " violated by import: " + line.trim());
            }
        }
    }

    @Test
    void arT1_workflowImpls_doNotImportRepositories() {
        for (String impl : WORKFLOW_IMPLS) {
            String imports = imports(lines(WORKFLOW_SRC, impl));
            assertNoImport(imports, ".infrastructure.", "AR-T1");
            assertNoImport(imports, "org.springframework.data", "AR-T1");
            assertNoImport(imports, "org.jooq", "AR-T1");
            assertNoImport(imports, ".Repository", "AR-T1");
        }
    }

    @Test
    void arT2_workflowImpls_noNetworkFilesystemProcessIo() {
        for (String impl : WORKFLOW_IMPLS) {
            String imports = imports(lines(WORKFLOW_SRC, impl));
            assertNoImport(imports, "java.net.", "AR-T2");
            assertNoImport(imports, "java.io.", "AR-T2");
            assertNoImport(imports, "java.nio.file", "AR-T2");
            assertNoImport(imports, "java.lang.ProcessBuilder", "AR-T2");
            assertNoImport(imports, "java.lang.Runtime", "AR-T2");
            assertNoImport(imports, "java.util.concurrent", "AR-T2");
        }
    }

    @Test
    void arT3_workflowImpls_noRenderImplementationDependency() {
        for (String impl : WORKFLOW_IMPLS) {
            String imports = imports(lines(WORKFLOW_SRC, impl));
            assertNoImport(imports, "com.example.platform.render.app", "AR-T3");
            assertNoImport(imports, "com.example.platform.render.infrastructure", "AR-T3");
            assertNoImport(imports, "com.example.platform.render.domain", "AR-T3");
        }
    }

    @Test
    void arT4_workflowImpls_noTimelineOrProductWrites() {
        for (String impl : WORKFLOW_IMPLS) {
            String imports = imports(lines(WORKFLOW_SRC, impl));
            assertNoImport(imports, "Timeline", "AR-T4");
            assertNoImport(imports, "ProductRuntimeService", "AR-T4");
        }
    }

    @Test
    void arT5_activities_usePortsOnly() {
        String imports = imports(lines(WORKFLOW_SRC, "temporal/RenderActivitiesImpl.java"));
        assertNoImport(imports, ".infrastructure.", "AR-T5");
        assertNoImport(imports, "org.jooq", "AR-T5");
        assertNoImport(imports, ".Repository", "AR-T5");
    }

    @Test
    void arT6_temporalSdk_doesNotLeakIntoUnrelatedDomains() {
        // render-module and extension-module must not import io.temporal
        Stream.of(RENDER_SRC, EXTENSION_SRC).forEach(root -> {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(p -> p.toString().endsWith(".java"))
                        .forEach(p -> {
                            try {
                                String content = Files.readString(p);
                                if (content.contains("import io.temporal")) {
                                    fail("AR-T6 io.temporal leak: " + p);
                                }
                            } catch (IOException e) {
                                fail("read error " + p + ": " + e.getMessage());
                            }
                        });
            } catch (IOException e) {
                fail("walk error " + root + ": " + e.getMessage());
            }
        });
    }

    @Test
    void arT7_workflowModule_exposesNoBusinessAuthorityTypes() {
        // workflow public API must not export Timeline/Product/Plugin authority types
        String portImports = imports(lines(WORKFLOW_SRC, "port/RenderExecutionPort.java"));
        assertNoImport(portImports, "com.example.platform.render.app", "AR-T7");
        assertNoImport(portImports, "Timeline", "AR-T7");
        assertNoImport(portImports, "ProductRuntimeService", "AR-T7");
    }

    @Test
    void arT8_extensionModule_hasNoTemporalDependency() {
        try (Stream<Path> paths = Files.walk(EXTENSION_SRC)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            if (content.contains("io.temporal")) {
                                fail("AR-T8 extension io.temporal reference: " + p);
                            }
                        } catch (IOException e) {
                            fail("read error " + p + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            fail("walk error: " + e.getMessage());
        }
    }

    @Test
    void arT9_w2UserWorkflowDefinition_absent() {
        Path w2 = WORKFLOW_SRC.resolve("userdefinition");
        if (Files.exists(w2)) {
            fail("AR-T9 W2 user workflow definition package must remain absent");
        }
    }

    /**
     * AR-T10 payload/reference rule (W1-GAP-010). W1 workflow/activity
     * signatures must carry stable references only — no raw timeline JSON,
     * no large document parameter types. The dormant RenderPipelineWorkflow
     * family is EXCLUDED from W1 by the frozen contract (BLOCKED_FROM_W1, left
     * untouched) and is NOT a production-reachable signature set; the rule
     * covers the production-reachable wired surface.
     */
    @Test
    void arT10_noRawTimelineOrLargeDocumentsInWorkflowSignatures() {
        for (String sig : WORKFLOW_SIGNATURES) {
            List<String> fileLines = lines(WORKFLOW_SRC, sig);
            // Only inspect import lines and method-signature lines — javadoc
            // prose that DOCUMENTS the prohibition is not a violation.
            StringBuilder signatureOnly = new StringBuilder();
            for (String l : fileLines) {
                String t = l.trim();
                if (t.startsWith("import ") || t.startsWith("@") || t.startsWith("String ")
                        || t.startsWith("void ") || t.startsWith("public ") || t.contains("(")) {
                    signatureOnly.append(t).append('\n');
                }
            }
            String all = signatureOnly.toString();
            assertTrue(!all.contains("timelineJson"),
                    "AR-T10 raw timeline JSON in " + sig);
            assertTrue(!all.contains("TimelineDocument"),
                    "AR-T10 TimelineDocument in " + sig);
            assertTrue(!all.contains("RenderExecutionPlan "),
                    "AR-T10 RenderExecutionPlan payload in " + sig);
            assertTrue(!all.contains("byte[]"),
                    "AR-T10 raw bytes in " + sig);
            assertTrue(!all.contains("org.jooq"),
                    "AR-T10 ORM type in " + sig);
        }
    }

    /**
     * RED-10 (W1-GAP-010): the dormant RenderPipelineWorkflow carries raw
     * timeline JSON in its signature. The frozen contract BLOCKS this family
     * from W1 (BLOCKED_FROM_W1, left untouched) and requires it to remain
     * dormant — i.e. never registered (no @WorkflowImpl) and never started.
     * This assertion demonstrates the dormant raw-payload risk is identified
     * and the family stays inactive. Pre-implementation this is the payload
     * rule gap: a naive "all workflow types" scan would flag
     * RenderPipelineWorkflow.execute(jobId, timelineJson, profile); the W1
     * rule therefore pins the wired surface clean AND proves the dormant
     * family is not registered.
     */
    @Test
    void arT10b_dormantPipelineRawJsonStaysExcludedAndUnregistered() {
        // Dormant family must NOT be registered: RenderPipelineWorkflowImpl
        // must have no @WorkflowImpl annotation (stays unregistered/dormant).
        String implSrc = String.join("\n",
                lines(WORKFLOW_SRC, "temporal/RenderPipelineWorkflowImpl.java"));
        assertTrue(!implSrc.contains("@WorkflowImpl"),
                "RenderPipelineWorkflowImpl must remain unregistered (dormant)");
        assertTrue(implSrc.contains("timelineJson"),
                "dormant pipeline signature carries raw timeline JSON (identified risk)");
    }
}
