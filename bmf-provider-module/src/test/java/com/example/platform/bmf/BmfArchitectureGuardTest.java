package com.example.platform.bmf;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.domain.RuntimeDependencyFingerprint;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BmfArchitectureGuardTest {

    private static final List<String> CORE_MODULES = List.of(
            "platform-app",
            "timeline-module",
            "render-module",
            "operation-module",
            "media-execution-plan-module",
            "worker-fabric-module",
            "extension-module",
            "provider-plugin-runtime-module");

    @Test
    void production_has_no_ambient_runtime_authority_h1_shadow_or_plugin_contribution()
            throws Exception {
        Path root = repositoryRoot();
        String production = readJava(root.resolve("bmf-provider-module/src/main/java"));
        String build = Files.readString(root.resolve("bmf-provider-module/build.gradle.kts"));

        List<String> ambientRuntimeAuthorities = List.of(
                "ProcessBuilder",
                "Runtime.getRuntime",
                "System.getenv",
                "System.getProperty",
                "System.setProperty",
                "user.dir",
                "\"PATH\"",
                " which ",
                "/usr/bin/ffmpeg",
                "/usr/local",
                "/opt/",
                "/nix/store",
                "RuntimeProbe",
                "runtimeProbe",
                "ExecutablePath",
                "executablePath",
                "ProcessInvocationSpec",
                "shellCommand",
                "new ExecutionCommand",
                "new RuntimeExecutionBundle",
                "ProviderPluginRuntimeContext",
                ".executable()",
                "c39146c636c6b2b68ffaf741095ce737bf123254");
        long ambientAuthorityCount = ambientRuntimeAuthorities.stream()
                .filter(production::contains)
                .count();
        assertThat(ambientAuthorityCount)
                .as("forbidden ambient runtime authority token count")
                .isZero();

        long sharedShadowDeclarationCount = List.of(
                        "RuntimeDependencyRequirement",
                        "RuntimeDependencyObservation",
                        "RuntimeDependencyFingerprint",
                        "RuntimeDependencyMatcher",
                        "RuntimeDependencyMatchResult",
                        "ProviderHardwareRequirement",
                        "ProviderHardwareObservation",
                        "WorkerRuntimeId")
                .stream()
                .mapToLong(name -> declarationCount(production, name))
                .sum();
        assertThat(sharedShadowDeclarationCount)
                .as("forbidden H1 shared-type shadow declaration count")
                .isZero();

        assertThat(production)
                .doesNotContain(
                        "org.pf4j",
                        "ProviderPlugin",
                        "ProviderPluginContribution",
                        "Timeline",
                        "RenderGraph",
                        "LogicalExecutionGraph",
                        "ExecutableTaskGraph");
        assertThat(build)
                .doesNotContain("org.pf4j", "pf4j", "provider-plugin-runtime-module");
        assertThat(Files.exists(root.resolve("bmf-provider-module/src/main/resources")))
                .isFalse();
    }

    @Test
    void runtime_evidence_types_claim_no_h1_or_semantic_execution_authority() throws Exception {
        Path root = repositoryRoot();
        String production = readJava(root.resolve("bmf-provider-module/src/main/java"));

        List<String> forbiddenClaims = List.of(
                "ProviderRuntimeBundleId",
                "RuntimeEligibility",
                "CAN_RUN",
                "GaussianBlur",
                "Gaussian blur",
                "semantic conformance",
                "OutputEquivalence",
                "output equivalence",
                "BmfGpu",
                "GPU",
                "OpenCue");
        long forbiddenClaimCount = forbiddenClaims.stream().filter(production::contains).count();
        assertThat(forbiddenClaimCount)
                .as("forbidden bundle/eligibility/semantic/GPU/OpenCue claim token count")
                .isZero();

        List<String> forbiddenAssessmentAuthority = List.of(
                "eligibility",
                "canrun",
                "workerruntime",
                "physicalhost",
                "device",
                "capacity",
                "reservation",
                "observedusage");
        for (RecordComponent component :
                BmfCpuRuntimeDependencyAssessment.class.getRecordComponents()) {
            String signature = (component.getName() + " " + component.getType().getName())
                    .toLowerCase();
            assertThat(signature)
                    .as("provider-local assessment component authority")
                    .doesNotContain(forbiddenAssessmentAuthority.toArray(String[]::new));
        }
    }

    @Test
    void authoritative_fingerprint_is_the_h1_type_and_is_separate_from_implementation_identity()
            throws Exception {
        Field field = BmfCpuProvider.class.getField("EXPECTED_RUNTIME_DEPENDENCY_FINGERPRINT");
        assertThat(field.getType()).isEqualTo(RuntimeDependencyFingerprint.class);
        assertThat(field.get(null)).isSameAs(BmfCpuProvider.EXPECTED_RUNTIME_DEPENDENCY_FINGERPRINT);
        assertThat(BmfCpuProvider.EXPECTED_RUNTIME_DEPENDENCY_FINGERPRINT.canonicalSha256())
                .isEqualTo("sha256:5ad7e1e40dd3cfa453960b829a6f61de7216c956638d06e7ad2cefe4be96dfd5")
                .isNotEqualTo(BmfCpuProvider.IMPLEMENTATION_ID.value());
        assertThat(field.getType()).isNotEqualTo(String.class);
    }

    @Test
    void native_plan_has_no_graph_or_canonical_leak_fields() {
        List<Field> instanceFields = Arrays.stream(BmfCpuNativePlan.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();

        assertThat(instanceFields).extracting(Field::getName)
                .containsExactlyInAnyOrder("executableTaskId", "providerBindingPin");
        assertThat(instanceFields).extracting(Field::getType)
                .containsExactlyInAnyOrder(ExecutableTaskId.class, ProviderBindingPin.class);
    }

    @Test
    void canonical_and_h1_modules_have_no_reverse_bmf_dependency_or_import() throws Exception {
        Path root = repositoryRoot();
        for (String module : CORE_MODULES) {
            Path moduleRoot = root.resolve(module);
            assertThat(Files.readString(moduleRoot.resolve("build.gradle.kts")))
                    .as("%s build dependency", module)
                    .doesNotContain("bmf-provider-module");
            assertThat(readJava(moduleRoot.resolve("src/main/java")))
                    .as("%s production import", module)
                    .doesNotContain("com.example.platform.bmf");
        }
    }

    @Test
    void canonical_and_h1_modules_have_zero_bmf_private_topology() throws Exception {
        Path root = repositoryRoot();
        List<String> canonicalH1SourceRoots = List.of(
                "timeline-module/src/main/java",
                "operation-module/src/main/java",
                "render-module/src/main/java/com/example/platform/render/domain/renderplan",
                "media-execution-plan-module/src/main/java",
                "worker-fabric-module/src/main/java",
                "extension-module/src/main/java");
        List<String> bmfTopologyTokens = List.of(
                "BmfGraph",
                "BmfNode",
                "BmfEdge",
                "BmfModule",
                "bmf.graph",
                "bmf.node",
                "bmf.edge",
                "bmf.module");
        long topologyCount = 0;
        for (String sourceRoot : canonicalH1SourceRoots) {
            String production = readJava(root.resolve(sourceRoot));
            topologyCount += bmfTopologyTokens.stream().filter(production::contains).count();
        }
        assertThat(topologyCount).as("canonical/H1 BMF topology token count").isZero();
    }

    @Test
    void clean_forward_ledger_remains_the_frozen_classified_65_path_universe()
            throws Exception {
        String ledger = Files.readString(repositoryRoot().resolve(
                "docs/architecture/governance/h2-bmf-clean-forward-disposition-v1.json"));

        assertThat(ledger)
                .contains(
                        "\"targeted_universe_path_count\": 65",
                        "\"unclassified\": 0",
                        "\"UNCLASSIFIED\": 0");
        assertThat(countOccurrences(ledger, "\"disposition\":"))
                .as("materialized ledger classified row count")
                .isEqualTo(65);
    }

    @Test
    void settings_and_module_source_inventory_are_exact() throws Exception {
        Path root = repositoryRoot();
        String settings = Files.readString(root.resolve("settings.gradle.kts"));
        assertThat(countOccurrences(settings, "\"bmf-provider-module\""))
                .isEqualTo(1);

        assertThat(relativeJavaFiles(
                        root.resolve("bmf-provider-module/src/main/java"),
                        root.resolve("bmf-provider-module")))
                .containsExactlyInAnyOrder(
                        "src/main/java/com/example/platform/bmf/BmfCpuProvider.java",
                        "src/main/java/com/example/platform/bmf/BmfCpuNativePlan.java",
                        "src/main/java/com/example/platform/bmf/BmfCpuUnsupportedLowerer.java",
                        "src/main/java/com/example/platform/bmf/BmfCpuUnsupportedRuntimeAdapter.java",
                        "src/main/java/com/example/platform/bmf/BmfCpuRuntimeDependencyAssessment.java",
                        "src/main/java/com/example/platform/bmf/BmfCpuRuntimeEvidenceAssessor.java",
                        "src/main/java/com/example/platform/bmf/BmfCpuRuntimeEvidenceIssue.java",
                        "src/main/java/com/example/platform/bmf/package-info.java");
        assertThat(relativeJavaFiles(
                        root.resolve("bmf-provider-module/src/test/java"),
                        root.resolve("bmf-provider-module")))
                .containsExactlyInAnyOrder(
                        "src/test/java/com/example/platform/bmf/BmfCpuProviderContractTest.java",
                        "src/test/java/com/example/platform/bmf/BmfCpuRuntimeEvidenceAssessmentTest.java",
                        "src/test/java/com/example/platform/bmf/BmfCpuUnsupportedSeamsTest.java",
                        "src/test/java/com/example/platform/bmf/BmfArchitectureGuardTest.java");
    }

    private static long declarationCount(String source, String simpleName) {
        Pattern declaration = Pattern.compile(
                "\\b(?:class|record|interface|enum)\\s+" + Pattern.quote(simpleName) + "\\b");
        return declaration.matcher(source).results().count();
    }

    private static int countOccurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static Set<String> relativeJavaFiles(Path directory, Path moduleRoot) throws Exception {
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(moduleRoot::relativize)
                    .map(Path::toString)
                    .collect(Collectors.toSet());
        }
    }

    private static String readJava(Path directory) throws Exception {
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(value -> value.toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                source.append(Files.readString(path)).append('\n');
            }
        }
        return source.toString();
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null && !Files.isRegularFile(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("repository root not found");
        }
        return candidate;
    }
}
