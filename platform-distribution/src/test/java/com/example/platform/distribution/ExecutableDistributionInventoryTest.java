package com.example.platform.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.distribution.ExecutableDistributionInventoryVerifier.Classification;
import com.example.platform.distribution.ExecutableDistributionInventoryVerifier.ExecutableFact;
import com.example.platform.distribution.ExecutableDistributionInventoryVerifier.FactKind;
import com.example.platform.distribution.ExecutableDistributionInventoryVerifier.ScanArea;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutableDistributionInventoryTest {

    private static final String SPRING_BOOT_JAR_LAUNCHER =
            "org.springframework.boot.loader.launch.JarLauncher";
    private final ExecutableDistributionInventoryVerifier verifier =
            new ExecutableDistributionInventoryVerifier();

    @Test
    void actualRepositoryExecutableUniverseIsCompletelyClassified() throws Exception {
        Path repositoryRoot = Path.of(System.getProperty("distribution.repository.root"));
        var report = verifier.inspect(repositoryRoot);

        report.requireComplete();
        assertThat(report.inspectedAreas()).containsExactlyInAnyOrder(ScanArea.values());
        assertThat(report.discovered()).isNotEmpty();
        assertThat(report.classified()).hasSameSizeAs(report.discovered());
        assertThat(report.unclassified()).isEmpty();
        assertThat(report.discovered())
                .extracting(ExecutableFact::kind)
                .contains(
                        FactKind.INCLUDED_BOOT_JAR_PROJECT,
                        FactKind.BUILD_DECLARED_LAUNCH,
                        FactKind.PRODUCTION_MAIN_CLASS,
                        FactKind.DOCKER_LAUNCH,
                        FactKind.COMPOSE_SERVICE_LAUNCH,
                        FactKind.DEPLOYMENT_CONTAINER,
                        FactKind.LAUNCH_SCRIPT);
        assertThat(report.classified())
                .anySatisfy(item -> {
                    assertThat(item.fact().source()).isEqualTo("gradlew");
                    assertThat(item.classification()).isEqualTo(Classification.BUILD_ONLY_TOOL);
                })
                .anySatisfy(item -> {
                    assertThat(item.fact().source()).startsWith(
                            "docs/architecture/governance/automated-guards/");
                    assertThat(item.fact().source()).endsWith(".py");
                    assertThat(item.classification()).isEqualTo(Classification.BUILD_ONLY_TOOL);
                })
                .anySatisfy(item -> {
                    assertThat(item.fact().kind()).isEqualTo(FactKind.DEPLOYMENT_CONTAINER);
                    assertThat(item.fact().source()).startsWith("k8s/");
                });

        assertThat(report.classified())
                .anySatisfy(item -> {
                    assertThat(item.fact().kind()).isEqualTo(FactKind.INCLUDED_BOOT_JAR_PROJECT);
                    assertThat(item.fact().detail()).contains("platform-app");
                    assertThat(item.classification()).isEqualTo(Classification.CANONICAL_APPLICATION_RUNTIME);
                })
                .anySatisfy(item -> {
                    assertThat(item.fact().kind()).isEqualTo(FactKind.INCLUDED_BOOT_JAR_PROJECT);
                    assertThat(item.fact().detail()).contains("platform-distribution");
                    assertThat(item.classification()).isEqualTo(Classification.OTHER_EXPLICIT);
                })
                .anySatisfy(item -> {
                    assertThat(item.fact().kind()).isEqualTo(FactKind.INCLUDED_BOOT_JAR_PROJECT);
                    assertThat(item.fact().detail()).contains("remote-render-worker");
                    assertThat(item.classification()).isEqualTo(Classification.WORKER_RUNTIME);
                })
                .anySatisfy(item -> {
                    assertThat(item.fact().kind()).isEqualTo(FactKind.INCLUDED_BOOT_JAR_PROJECT);
                    assertThat(item.fact().detail()).contains("sandbox-worker");
                    assertThat(item.classification()).isEqualTo(Classification.SANDBOX_RUNTIME);
                });

        System.out.println("EXECUTABLE_DISTRIBUTION_COUNT=" + report.discovered().size());
        System.out.println("EXECUTABLE_DISTRIBUTION_CLASSIFIED_COUNT=" + report.classified().size());
        System.out.println("UNCLASSIFIED_EXECUTABLE_DISTRIBUTION_COUNT=" + report.unclassified().size());
    }

    @Test
    void secondLaunchersInsideKnownModulesAndUnknownRuntimeFactsFailClosed() {
        List<ExecutableFact> unknown = List.of(
                new ExecutableFact(
                        FactKind.PRODUCTION_MAIN_CLASS,
                        "platform-app/src/main/java/com/example/platform/SecondPlatformApplication.java",
                        "com.example.platform.SecondPlatformApplication"),
                new ExecutableFact(
                        FactKind.PRODUCTION_MAIN_CLASS,
                        "remote-render-worker/src/main/java/com/example/platform/remoterender/"
                                + "SecondWorkerApplication.java",
                        "com.example.platform.remoterender.SecondWorkerApplication"),
                new ExecutableFact(
                        FactKind.DOCKER_LAUNCH,
                        "platform-app/Dockerfile.second:1",
                        "ENTRYPOINT [\"java\", \"-jar\", \"second-app.jar\"]"),
                new ExecutableFact(
                        FactKind.DEPLOYMENT_CONTAINER,
                        "k8s/base/second-api-runtime.yaml:1",
                        "image: second-platform-api:dev"),
                new ExecutableFact(
                        FactKind.LAUNCH_SCRIPT,
                        "platform-app/scripts/second-launcher.py",
                        "#!/usr/bin/env python3"));
        var report = verifier.classify(unknown);

        assertThat(report.unclassified()).containsExactlyElementsOf(unknown);
        assertThat(report.classified()).isEmpty();
        assertThatThrownBy(report::requireComplete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unclassified");
    }

    @Test
    void deploymentAndScriptDiscoveryAreFilenameAndExtensionIndependent(
            @TempDir Path repositoryRoot) throws Exception {
        Files.writeString(repositoryRoot.resolve("settings.gradle.kts"),
                "rootProject.name = \"discovery-fixture\"\n");
        Path gitopsManifest = repositoryRoot.resolve("gitops/arbitrary-name.yaml");
        Path k8sManifest = repositoryRoot.resolve("k8s/nested/not-a-deployment-prefix.yml");
        Files.createDirectories(gitopsManifest.getParent());
        Files.createDirectories(k8sManifest.getParent());
        Files.writeString(gitopsManifest, "kind: Pod\nspec:\n  image: unknown/gitops:1\n");
        Files.writeString(k8sManifest, "kind: Pod\nspec:\n  image: unknown/k8s:1\n");
        Path extensionless = repositoryRoot.resolve("tooling/extensionless-guard");
        Path pythonGuard = repositoryRoot.resolve("tooling/governance-guard.py");
        Files.createDirectories(extensionless.getParent());
        Files.writeString(extensionless, "#!/bin/sh\nexit 0\n");
        Files.writeString(pythonGuard, "#!/usr/bin/env python3\n");

        var report = verifier.inspect(repositoryRoot);

        assertThat(report.discovered())
                .extracting(ExecutableFact::source)
                .containsExactlyInAnyOrder(
                        "gitops/arbitrary-name.yaml:3",
                        "k8s/nested/not-a-deployment-prefix.yml:3",
                        "tooling/extensionless-guard",
                        "tooling/governance-guard.py");
        assertThat(report.unclassified()).hasSameSizeAs(report.discovered());
        assertThatThrownBy(report::requireComplete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unclassified");
    }

    @Test
    void builtOutputFactsAreDiscoveredAndClassified(@TempDir Path repositoryRoot) throws Exception {
        Files.writeString(repositoryRoot.resolve("settings.gradle.kts"),
                "rootProject.name = \"built-output-fixture\"\n");
        Path executable = repositoryRoot.resolve("platform-app/build/libs/platform-app.jar");
        writeExecutableJar(
                executable,
                "com.example.platform.PlatformApplication",
                SPRING_BOOT_JAR_LAUNCHER);

        var report = verifier.inspect(repositoryRoot);

        report.requireComplete();
        assertThat(report.discovered()).singleElement().satisfies(fact -> {
            assertThat(fact.kind()).isEqualTo(FactKind.BUILT_EXECUTABLE_ARTIFACT);
            assertThat(fact.detail()).isEqualTo(
                    "Start-Class=com.example.platform.PlatformApplication Main-Class="
                            + SPRING_BOOT_JAR_LAUNCHER);
        });
        assertThat(report.classified()).singleElement().satisfies(item ->
                assertThat(item.classification())
                        .isEqualTo(Classification.CANONICAL_APPLICATION_RUNTIME));
    }

    @Test
    void exactKnownBuiltArtifactIdentitiesAreClassified() {
        List<ExecutableFact> known = List.of(
                builtArtifact(
                        "platform-app/build/libs/platform-app.jar",
                        "com.example.platform.PlatformApplication",
                        SPRING_BOOT_JAR_LAUNCHER),
                builtArtifact(
                        "platform-distribution/build/libs/media-platform-all-in-one.jar",
                        "com.example.platform.distribution.PlatformDistributionLauncher",
                        SPRING_BOOT_JAR_LAUNCHER),
                builtArtifact(
                        "remote-render-worker/build/libs/remote-render-worker.jar",
                        "com.example.platform.remoterender.RemoteRenderWorkerApplication",
                        SPRING_BOOT_JAR_LAUNCHER),
                builtArtifact(
                        "sandbox-worker/build/libs/sandbox-worker-0.0.1-SNAPSHOT.jar",
                        "com.example.platform.sandbox.worker.SandboxWorkerApplication",
                        SPRING_BOOT_JAR_LAUNCHER));

        var report = verifier.classify(known);

        report.requireComplete();
        assertThat(report.classified())
                .extracting(item -> item.classification())
                .containsExactly(
                        Classification.CANONICAL_APPLICATION_RUNTIME,
                        Classification.OTHER_EXPLICIT,
                        Classification.WORKER_RUNTIME,
                        Classification.SANDBOX_RUNTIME);
    }

    @Test
    void unknownBuiltJarsInsideKnownModulePathsAndManifestMismatchesFailClosed(
            @TempDir Path repositoryRoot) throws Exception {
        Files.writeString(repositoryRoot.resolve("settings.gradle.kts"),
                "rootProject.name = \"hostile-built-output-fixture\"\n");
        List<ExecutableFact> unknown = List.of(
                builtArtifact(
                        "platform-app/build/libs/second-platform-app.jar",
                        "com.example.platform.SecondPlatformApplication",
                        SPRING_BOOT_JAR_LAUNCHER),
                builtArtifact(
                        "platform-distribution/build/libs/second-distribution.jar",
                        "com.example.platform.distribution.PlatformDistributionLauncher",
                        SPRING_BOOT_JAR_LAUNCHER),
                builtArtifact(
                        "remote-render-worker/build/libs/second-worker.jar",
                        "com.example.platform.remoterender.RemoteRenderWorkerApplication",
                        SPRING_BOOT_JAR_LAUNCHER),
                builtArtifact(
                        "sandbox-worker/build/libs/second-sandbox.jar",
                        "com.example.platform.sandbox.worker.SandboxWorkerApplication",
                        SPRING_BOOT_JAR_LAUNCHER),
                builtArtifact(
                        "platform-app/build/libs/platform-app.jar",
                        "com.example.platform.SecondPlatformApplication",
                        SPRING_BOOT_JAR_LAUNCHER),
                builtArtifact(
                        "platform-distribution/build/libs/media-platform-all-in-one.jar",
                        "com.example.platform.distribution.PlatformDistributionLauncher",
                        "com.example.platform.distribution.SecondLauncher"),
                builtArtifact(
                        "remote-render-worker/build/libs/remote-render-worker.jar",
                        "com.example.platform.remoterender.SecondWorkerApplication",
                        SPRING_BOOT_JAR_LAUNCHER),
                builtArtifact(
                        "sandbox-worker/build/libs/sandbox-worker-0.0.1-SNAPSHOT.jar",
                        "com.example.platform.sandbox.worker.SandboxWorkerApplication",
                        "com.example.platform.sandbox.worker.SecondLauncher"));
        for (ExecutableFact fact : unknown) {
            String[] manifestIdentity = fact.detail().split(" Main-Class=", 2);
            writeExecutableJar(
                    repositoryRoot.resolve(fact.source()),
                    manifestIdentity[0].replaceFirst("^Start-Class=", ""),
                    manifestIdentity[1]);
        }

        var report = verifier.inspect(repositoryRoot);

        assertThat(report.discovered()).containsExactlyInAnyOrderElementsOf(unknown);
        assertThat(report.unclassified()).containsExactlyInAnyOrderElementsOf(unknown);
        assertThat(report.classified()).isEmpty();
        assertThatThrownBy(report::requireComplete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unclassified");
    }

    private static ExecutableFact builtArtifact(
            String source, String startClass, String mainClass) {
        return new ExecutableFact(
                FactKind.BUILT_EXECUTABLE_ARTIFACT,
                source,
                "Start-Class=" + startClass + " Main-Class=" + mainClass);
    }

    private static void writeExecutableJar(
            Path executable, String startClass, String mainClass) throws Exception {
        Files.createDirectories(executable.getParent());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Start-Class", startClass);
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        try (JarOutputStream ignored = new JarOutputStream(
                Files.newOutputStream(executable), manifest)) {
            // The manifest is the runtime fact; no payload is needed for inventory classification.
        }
    }
}
