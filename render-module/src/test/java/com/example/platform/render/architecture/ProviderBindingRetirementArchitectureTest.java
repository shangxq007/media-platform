package com.example.platform.render.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * EP-06 clean-forward guards.
 *
 * <p>GRD-A02 keeps provider binding out of render. GRD-D02 keeps provider-specific
 * declarations out of extension's neutral contracts. Provider bindings belong to
 * provider-plugin-runtime and concrete adapters; media-execution-plan owns the
 * provider-neutral plan.</p>
 */
class ProviderBindingRetirementArchitectureTest {

    @Test
    void grdA02_renderContainsNoRetiredProviderBindingAuthority() throws IOException {
        Path root = repositoryRoot();
        List<String> retiredPaths = List.of(
                "render-module/src/main/java/com/example/platform/render/app/timeline/compile/ProviderBindingCompiler.java",
                "render-module/src/main/java/com/example/platform/render/app/timeline/compile/ProviderExecutionDocumentDraftCompiler.java",
                "render-module/src/main/java/com/example/platform/render/domain/compile/binding/ProviderBindingPlan.java",
                "render-module/src/main/java/com/example/platform/render/domain/asset/BmfGraphDefinition.java",
                "render-module/src/main/java/com/example/platform/render/domain/asset/semantic/AiProviderDescriptor.java",
                "render-module/src/main/java/com/example/platform/render/domain/execution/BmfExecutionSpec.java",
                "render-module/src/main/java/com/example/platform/render/domain/execution/ExecutionCommand.java",
                "render-module/src/main/java/com/example/platform/render/domain/execution/LocalProcessExecutionSpec.java",
                "render-module/src/main/java/com/example/platform/render/domain/execution/RemotionExecutionSpec.java",
                "render-module/src/main/java/com/example/platform/render/domain/visual/ProviderVisualCapabilitySupport.java",
                "render-module/src/main/java/com/example/platform/render/domain/visual/ProviderVisualCapabilityMatrix.java");

        List<String> present = retiredPaths.stream()
                .filter(path -> Files.exists(root.resolve(path)))
                .toList();

        assertEquals(List.of(), present,
                "GRD-A02: render provider-binding/execution shadows must be physically absent");
    }

    @Test
    void grdD02_neutralExtensionContractsContainNoFfmpegDeclaration() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        for (String path : List.of(
                "extension-module/src/main/java/com/example/platform/extension/domain/PermissionDescriptor.java",
                "extension-module/src/main/java/com/example/platform/extension/domain/PluginGuarantee.java",
                "extension-module/src/main/java/com/example/platform/extension/domain/ResourceRequirement.java")) {
            String source = Files.readString(root.resolve(path));
            if (source.toLowerCase().contains("ffmpeg")) {
                violations.add(path);
            }
        }

        assertEquals(List.of(), violations,
                "GRD-D02: provider declarations belong to concrete provider adapters, not neutral contracts");
    }

    private static Path repositoryRoot() {
        Path root = Path.of("").toAbsolutePath().normalize();
        while (root != null && !Files.exists(root.resolve("settings.gradle.kts"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new IllegalStateException("repository root not found");
        }
        return root;
    }
}
