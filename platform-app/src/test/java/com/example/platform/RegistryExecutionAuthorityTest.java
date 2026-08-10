package com.example.platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PMPR-S1-CRR1 registry/runtime authority closure guards.
 * RED on historical S1 candidate ce525191, GREEN after registry execution surface removal.
 */
class RegistryExecutionAuthorityTest {

    private Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("settings.gradle.kts"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("settings.gradle.kts not found");
        }
        return p;
    }

    private String read(String rel) {
        try {
            return Files.readString(repoRoot().resolve(rel));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PMPR-S1-AR-REGISTRY-01: ExtensionRegistryService exposes no effect-execution authority. */
    @Test
    void registryHasNoEffectExecutionAuthority() {
        String reg = read("extension-module/src/main/java/com/example/platform/extension/app/ExtensionRegistryService.java");
        assertFalse(reg.contains("public ExtensionResult executeProvider("),
                "executeProvider must be removed from ExtensionRegistryService");
        assertFalse(reg.contains("public ExtensionResult executePromptExtension("),
                "executePromptExtension must be removed (no production callers)");
        assertFalse(reg.contains("public ExtensionResult executeWorkflowStep("),
                "executeWorkflowStep must be removed (no production callers)");
        assertFalse(reg.contains("sandboxExecutionService"),
                "registry must not hold a sandbox execution service");
    }

    /** PMPR-S1-AR-RUNTIME-02: production consumers execute only through PluginRuntime. */
    @Test
    void productionConsumersUsePluginRuntimeOnly() {
        for (String h : new String[]{
                "EmbeddingTaskHandler.java", "OcrTaskHandler.java",
                "RealAsrTaskHandler.java", "VisionTaskHandler.java"}) {
            String src = read("render-module/src/main/java/com/example/platform/render/infrastructure/asset/provider/" + h);
            assertFalse(src.contains("registryService.executeProvider") || src.contains("extensionRegistry.executeProvider")
                            || src.contains("extReg.executeProvider"),
                    h + " must not call registry executeProvider");
            assertTrue(src.contains("pluginRuntime.executeProvider"),
                    h + " must execute through PluginRuntime");
        }
        String ctrl = read("extension-module/src/main/java/com/example/platform/extension/api/ExtensionController.java");
        assertTrue(ctrl.contains("pluginRuntime.executeProvider"),
                "ExtensionController must execute through PluginRuntime");
        assertFalse(ctrl.contains("registryService.executeProvider"),
                "ExtensionController must not call registry executeProvider");
    }

    /** Registry retains registration/metadata responsibilities. */
    @Test
    void registryRetainsMetadataResponsibilities() {
        String reg = read("extension-module/src/main/java/com/example/platform/extension/app/ExtensionRegistryService.java");
        assertTrue(reg.contains("public void registerProviderExtension("), "registration must remain");
        assertTrue(reg.contains("findProviderBinding"), "binding lookup must remain");
    }
}
