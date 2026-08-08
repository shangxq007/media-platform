package com.example.platform.workflow.definition.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * UWD-RED-018 (ARCHITECTURE). AR-W2-01..12 as deterministic source-boundary
 * assertions (TemporalWorkflowArchitectureTest mechanism; ArchUnit is NOT a
 * dependency). Authentic RED: fails while the W2 definition source tree does
 * not exist.
 */
class WorkflowDefinitionArchitectureTest {

    private static final Path DEFINITION_SRC =
            Path.of("src/main/java/com/example/platform/workflow/definition");
    private static final Path DEFINITION_DOMAIN =
            Path.of("src/main/java/com/example/platform/workflow/definition/domain");
    private static final Path DEFINITION_APP =
            Path.of("src/main/java/com/example/platform/workflow/definition/app");
    private static final Path TEMPORAL_SRC =
            Path.of("src/main/java/com/example/platform/workflow/temporal");
    private static final Path RENDER_SRC =
            Path.of("../render-module/src/main/java/com/example/platform/render");

    @Test
    void definitionSourceTreeExists() {
        // AR-W2 preconditions: the W2 definition surface must exist.
        assertTrue(Files.isDirectory(DEFINITION_SRC),
                "definition source tree must exist (fails authentically while W2 is absent)");
    }

    @Test
    void arW201DefinitionDomainHasNoTemporalImports() {
        assertNoImport(DEFINITION_DOMAIN, "io.temporal", "AR-W2-01");
    }

    @Test
    void arW202DefinitionDomainHasNoSpringJdbcImports() {
        assertNoImport(DEFINITION_DOMAIN, "org.springframework.jdbc", "AR-W2-02");
    }

    @Test
    void arW203DefinitionDomainHasNoWebImports() {
        assertNoImport(DEFINITION_DOMAIN, "org.springframework.web", "AR-W2-03");
    }

    @Test
    void arW204DefinitionDomainHasNoRenderImports() {
        assertNoImport(DEFINITION_DOMAIN, "com.example.platform.render", "AR-W2-04");
    }

    @Test
    void arW205DefinitionAppDoesNotStartTemporalWorkflows() {
        for (Path file : javaFiles(DEFINITION_APP)) {
            String content = read(file);
            assertFalse(content.contains("WorkflowClient"), "AR-W2-05: " + file + " references WorkflowClient");
            assertFalse(content.contains("TemporalWorkflowStarter"), "AR-W2-05: " + file + " references TemporalWorkflowStarter");
            assertFalse(content.contains("io.temporal"), "AR-W2-05: " + file + " imports Temporal SDK");
        }
    }

    @Test
    void arW206DefinitionAppDoesNotExecutePlugins() {
        assertNoImport(DEFINITION_APP, "com.example.platform.extension", "AR-W2-06");
    }

    @Test
    void arW207TemporalDoesNotUseDefinitionJdbcAdapter() {
        for (Path file : javaFiles(TEMPORAL_SRC)) {
            String content = read(file);
            assertFalse(content.contains("workflow.definition.infrastructure"),
                    "AR-W2-07: " + file + " imports the definition JDBC adapter");
            assertFalse(content.contains("UserWorkflowDefinitionRepository"),
                    "AR-W2-07: " + file + " references the definition repository");
        }
    }

    @Test
    void arW208RenderModuleDoesNotOwnUserWorkflowDefinition() {
        if (!Files.isDirectory(RENDER_SRC)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(RENDER_SRC)) {
            List<Path> hits = stream.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> read(p).contains("UserWorkflowDefinition"))
                    .toList();
            assertTrue(hits.isEmpty(), "AR-W2-08: render-module owns UserWorkflowDefinition: " + hits);
        } catch (IOException e) {
            fail("AR-W2-08 scan failed: " + e.getMessage());
        }
    }

    @Test
    void arW209AutomationFlowRemainsNonPersistentVocabulary() {
        // W2 main code never imports shared-kernel capability (vocabulary source only).
        assertNoImport(DEFINITION_SRC, "com.example.platform.shared.capability", "AR-W2-09");
    }

    @Test
    void arW210NodeDeclarationsDoNotImplementRuntimeExecution() {
        for (Path file : javaFiles(DEFINITION_DOMAIN)) {
            String content = read(file);
            if (content.contains("UserWorkflowDefinitionNode") || file.getFileName().toString().contains("Node")) {
                assertFalse(content.contains("public void execute("), "AR-W2-10: " + file);
                assertFalse(content.contains("public void invoke("), "AR-W2-10: " + file);
                assertFalse(content.contains("public void start("), "AR-W2-10: " + file);
            }
        }
    }

    @Test
    void arW211TimelineProductRenderAuthoritiesUntouched() {
        assertNoImport(DEFINITION_SRC, "com.example.platform.render.app.product", "AR-W2-11");
        assertNoImport(DEFINITION_SRC, "com.example.platform.execution", "AR-W2-11");
        assertNoImport(DEFINITION_SRC, "com.example.platform.web.render", "AR-W2-11");
    }

    @Test
    void arW212PluginRuntimeV2Absent() {
        assertNoImport(DEFINITION_SRC, "com.example.platform.extension", "AR-W2-12");
    }

    private static void assertNoImport(Path dir, String fragment, String rule) {
        for (Path file : javaFiles(dir)) {
            String content = read(file);
            assertFalse(content.contains("import " + fragment),
                    rule + ": " + file + " imports " + fragment);
        }
    }

    private static List<Path> javaFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            fail("scan failed for " + dir + ": " + e.getMessage());
            return List.of();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            fail("read failed for " + file + ": " + e.getMessage());
            return "";
        }
    }
}
