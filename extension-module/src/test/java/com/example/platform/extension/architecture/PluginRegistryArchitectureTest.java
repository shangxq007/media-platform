package com.example.platform.extension.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * ARCHITECTURE RULES AR-P1-01..07 (frozen contract A096; conditional path —
 * activated because no existing architecture test class can host the rules:
 * only platform-app ModularityTest exists, which is Modulith-level, not
 * class-level P1 rule enforcement).
 *
 * <p>ArchUnit is NOT a project dependency (dependency changes are forbidden by
 * the candidate governance contract), so the frozen rules are enforced as
 * deterministic source-boundary assertions over the exact P1 paths. The rules
 * are scoped to the new P1 types only — no broad rule invalidates unrelated
 * legacy code.</p>
 *
 * <ul>
 *   <li>AR-P1-01 stable descriptor API has no Spring dependency</li>
 *   <li>AR-P1-02 public registry API has no PF4J dependency</li>
 *   <li>AR-P1-03 workflow-module is not a registry-foundation dependency</li>
 *   <li>AR-P1-04 render-module may depend on registry API (existing direction, no cycle)</li>
 *   <li>AR-P1-05 extension-module does NOT depend on render-module (contributor supplies binding)</li>
 *   <li>AR-P1-06 provider-facing API exposes no repositories</li>
 *   <li>AR-P1-07 provider-facing API exposes no Timeline write services</li>
 * </ul>
 */
class PluginRegistryArchitectureTest {

    private static final Path EXTENSION_SRC =
            Path.of("src/main/java/com/example/platform/extension");
    private static final Path RENDER_SRC =
            Path.of("../render-module/src/main/java/com/example/platform/render");

    private static final List<String> P1_DOMAIN_FILES = List.of(
            "domain/PluginDescriptor.java",
            "domain/CapabilityDescriptor.java",
            "domain/HandledObjectDescriptor.java",
            "domain/InvocationContract.java",
            "domain/PermissionDescriptor.java",
            "domain/ResourceRequirement.java",
            "domain/PluginRuntimeRequirement.java",
            "domain/PluginGuarantee.java",
            "domain/PluginHealth.java",
            "domain/OperationRequest.java",
            "domain/PluginSelectionResult.java",
            "domain/PluginDiagnosticCode.java",
            "domain/PluginDescriptorValidationIssue.java");

    private static final List<String> P1_PUBLIC_API_FILES = List.of(
            "api/port/PluginRegistryPort.java",
            "api/port/PluginSelectionPolicy.java",
            "api/port/PluginTenantEnablementPolicy.java");

    private static final List<String> P1_APP_FILES = List.of(
            "app/PluginRegistryImpl.java",
            "app/PluginDescriptorValidator.java",
            "app/PluginMatcher.java",
            "app/PluginHealthRegistry.java",
            "app/PluginDefaultSelectionPolicy.java");

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            fail("Cannot read " + path + ": " + e.getMessage());
            return "";
        }
    }

    private static boolean hasImport(String source, String packagePrefix) {
        return source.lines()
                .filter(l -> l.startsWith("import "))
                .anyMatch(l -> l.contains(packagePrefix));
    }

    @Test
    void arP101StableDescriptorApiHasNoSpringDependency() {
        for (String file : P1_DOMAIN_FILES) {
            String source = read(EXTENSION_SRC.resolve(file));
            assertTrue(!hasImport(source, "org.springframework"),
                    "AR-P1-01 violated: " + file + " imports Spring");
        }
    }

    @Test
    void arP102PublicRegistryApiHasNoPf4jDependency() {
        for (String file : P1_PUBLIC_API_FILES) {
            String source = read(EXTENSION_SRC.resolve(file));
            assertTrue(!hasImport(source, "org.pf4j"),
                    "AR-P1-02 violated: " + file + " imports PF4J");
            assertTrue(!source.contains("PluginManager") && !source.contains("PluginWrapper"),
                    "AR-P1-02 violated: " + file + " references PF4J types");
        }
    }

    @Test
    void arP103WorkflowModuleIsNotRegistryFoundationDependency() {
        for (String file : Stream.concat(
                P1_DOMAIN_FILES.stream(),
                Stream.concat(P1_PUBLIC_API_FILES.stream(), P1_APP_FILES.stream())).toList()) {
            String source = read(EXTENSION_SRC.resolve(file));
            assertTrue(!hasImport(source, "com.example.platform.workflow"),
                    "AR-P1-03 violated: " + file + " depends on workflow-module");
        }
    }

    @Test
    void arP104RenderMayDependOnRegistryApiExistingDirectionNoCycle() {
        // Render -> extension is the existing direction (render-module
        // build.gradle.kts api(project(":extension-module"))). The contributor
        // consumes the registry API. Verify the dependency exists and points the
        // allowed way.
        Path contributor = RENDER_SRC.resolve(
                "infrastructure/plugin/FfmpegRenderToolSelfDescription.java");
        assertTrue(Files.exists(contributor), "contributor source missing: " + contributor);
        String source = read(contributor);
        assertTrue(hasImport(source, "com.example.platform.extension"),
                "AR-P1-04 violated: contributor does not use extension registry API");
    }

    @Test
    void arP105ExtensionModuleDoesNotDependOnRenderModule() {
        for (String file : Stream.concat(
                P1_DOMAIN_FILES.stream(),
                Stream.concat(P1_PUBLIC_API_FILES.stream(), P1_APP_FILES.stream())).toList()) {
            String source = read(EXTENSION_SRC.resolve(file));
            assertTrue(!hasImport(source, "com.example.platform.render"),
                    "AR-P1-05 violated: " + file + " depends on render-module");
        }
    }

    @Test
    void arP106ProviderFacingApiExposesNoRepositories() {
        for (String file : Stream.concat(P1_DOMAIN_FILES.stream(), P1_PUBLIC_API_FILES.stream()).toList()) {
            String source = read(EXTENSION_SRC.resolve(file));
            assertTrue(!source.lines().filter(l -> l.startsWith("import "))
                            .anyMatch(l -> l.contains("repository") || l.contains("Repository")),
                    "AR-P1-06 violated: " + file + " references repositories");
            assertTrue(!source.contains("Repository"),
                    "AR-P1-06 violated: " + file + " exposes repository types");
        }
    }

    @Test
    void arP107ProviderFacingApiExposesNoTimelineWriteServices() {
        for (String file : Stream.concat(P1_DOMAIN_FILES.stream(), P1_PUBLIC_API_FILES.stream()).toList()) {
            String source = read(EXTENSION_SRC.resolve(file));
            // Real dependency check: imports of Timeline write services or
            // ProductRuntimeService. (Prose documenting the prohibition in
            // javadoc is not a dependency and is expected.)
            assertTrue(!source.lines().filter(l -> l.startsWith("import "))
                            .anyMatch(l -> l.contains("com.example.platform.render")
                                    || l.contains("TimelineWrite")
                                    || l.contains("ProductRuntimeService")),
                    "AR-P1-07 violated: " + file + " imports Timeline/Product write services");
        }
    }
}
