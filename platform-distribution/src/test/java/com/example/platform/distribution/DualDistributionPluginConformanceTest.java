package com.example.platform.distribution;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.extension.app.PluginDescriptorValidator;
import com.example.platform.extension.app.PluginHealthRegistry;
import com.example.platform.extension.app.PluginRegistryImpl;
import com.example.platform.providerplugin.EmbeddedPluginExtractor;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.providerplugin.ProviderPluginHost;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DualDistributionPluginConformanceTest {

    @TempDir Path temp;

    @Test
    void modular_and_embedded_modes_use_identical_bytes_and_pf4j_contracts() throws Exception {
        Path modularPlugin = Path.of(System.getProperty("distribution.modular.plugin"));
        Path allInOne = Path.of(System.getProperty("distribution.allinone.jar"));
        Path modularDirectory = Files.createDirectories(temp.resolve("modular-host"));
        Path isolatedModularPlugin = modularDirectory.resolve(modularPlugin.getFileName());
        Files.copy(modularPlugin, isolatedModularPlugin);
        EmbeddedPluginExtractor.ExtractedPlugin embedded = EmbeddedPluginExtractor.extractSingle(
                allInOne, "embedded-plugins/", temp.resolve("embedded-host"));

        assertThat(sha256(isolatedModularPlugin)).isEqualTo(embedded.sha256());
        assertThat(Files.readAllBytes(isolatedModularPlugin))
                .containsExactly(Files.readAllBytes(embedded.pluginJar()));

        ProviderPluginHost modular = host(modularDirectory);
        ProviderPluginHost allInOneHost = host(embedded.pluginJar().getParent());
        try (modular; allInOneHost) {
            ProviderPluginContribution modularContribution =
                    modular.loadAndStart().contributions().getFirst();
            ProviderPluginContribution embeddedContribution =
                    allInOneHost.loadAndStart().contributions().getFirst();

            assertThat(modularContribution.pluginId()).isEqualTo("media.transcode.ffmpeg");
            assertThat(modularContribution.pluginVersion()).isEqualTo("1.0.0");
            assertThat(embeddedContribution.pluginId()).isEqualTo(modularContribution.pluginId());
            assertThat(embeddedContribution.pluginVersion()).isEqualTo(modularContribution.pluginVersion());
            assertThat(embeddedContribution.pluginDescriptor())
                    .isEqualTo(modularContribution.pluginDescriptor());
            assertThat(embeddedContribution.providerDescriptor())
                    .isEqualTo(modularContribution.providerDescriptor());
            assertThat(embeddedContribution.providerExecutionContract())
                    .isEqualTo(modularContribution.providerExecutionContract());
            assertThat(embeddedContribution.providerCapabilityProfile())
                    .isEqualTo(modularContribution.providerCapabilityProfile());
            assertThat(embeddedContribution.workerRuntimeSupportRequirement())
                    .isEqualTo(modularContribution.workerRuntimeSupportRequirement());
            assertThat(embeddedContribution.providerBindingPin())
                    .isEqualTo(modularContribution.providerBindingPin());

            Path executable = binary("ffmpeg");
            byte[] source = generateInput(executable, temp.resolve("source.mp4"));
            ExternalProviderClosedLoopHarness.Outcome modularOutcome =
                    ExternalProviderClosedLoopHarness.success(
                            modularContribution, executable, temp.resolve("modular-run"), source, "modular");
            ExternalProviderClosedLoopHarness.Outcome embeddedOutcome =
                    ExternalProviderClosedLoopHarness.success(
                            embeddedContribution, executable, temp.resolve("embedded-run"), source, "embedded");
            assertThat(modularOutcome.outputBytes()).isEqualTo(embeddedOutcome.outputBytes());
            assertThat(modularOutcome.contentDigest()).isEqualTo(embeddedOutcome.contentDigest());
            assertThat(modularOutcome.commitCount()).isEqualTo(1);
            assertThat(modularOutcome.completionCount()).isEqualTo(1);
            assertThat(embeddedOutcome.commitCount()).isEqualTo(1);
            assertThat(embeddedOutcome.completionCount()).isEqualTo(1);

            var modularFailure = ExternalProviderClosedLoopHarness.failure(
                    modularContribution, executable, temp.resolve("modular-failure"),
                    "not-media".getBytes(), false, "modular-failure");
            var embeddedFailure = ExternalProviderClosedLoopHarness.failure(
                    embeddedContribution, executable, temp.resolve("embedded-failure"),
                    "not-media".getBytes(), false, "embedded-failure");
            assertThat(modularFailure).isEqualTo(embeddedFailure);
            assertThat(modularFailure.code())
                    .isEqualTo(ProviderNativeFailureCode.PROCESS_NONZERO_EXIT);
            assertThat(modularFailure.commitCount()).isZero();
            assertThat(modularFailure.completionCount()).isZero();
            assertThat(modularFailure.publicationCount()).isZero();

            var modularCancellation = ExternalProviderClosedLoopHarness.failure(
                    modularContribution, executable, temp.resolve("modular-cancel"),
                    source, true, "modular-cancel");
            var embeddedCancellation = ExternalProviderClosedLoopHarness.failure(
                    embeddedContribution, executable, temp.resolve("embedded-cancel"),
                    source, true, "embedded-cancel");
            assertThat(modularCancellation).isEqualTo(embeddedCancellation);
            assertThat(modularCancellation.code())
                    .isEqualTo(ProviderNativeFailureCode.PROCESS_CANCELLED);
            assertThat(modularCancellation.commitCount()).isZero();
            assertThat(modularCancellation.completionCount()).isZero();
            assertThat(modularCancellation.publicationCount()).isZero();

            assertThat(modular.disable(modularContribution.pluginId())).isTrue();
            assertThat(modular.catalog().contributions()).isEmpty();
        }
        assertThat(modular.catalog().contributions()).isEmpty();
        assertThat(allInOneHost.catalog().contributions()).isEmpty();
    }

    @Test
    void executable_modular_and_all_in_one_launchers_use_the_pf4j_host() throws Exception {
        Path modularPlugin = Path.of(System.getProperty("distribution.modular.plugin"));
        Path modularDirectory = Files.createDirectories(temp.resolve("launcher-modular-plugins"));
        Files.copy(modularPlugin, modularDirectory.resolve(modularPlugin.getFileName()));
        Path modularLauncher = modularPlugin.getParent().getParent().resolve("media-platform-launcher.jar");
        Path allInOne = Path.of(System.getProperty("distribution.allinone.jar"));

        ProcessResult modular = launch(modularLauncher, "--plugins-dir=" + modularDirectory);
        ProcessResult embedded = launch(allInOne);

        assertThat(modular.exitCode()).as(modular.output()).isZero();
        assertThat(embedded.exitCode()).as(embedded.output()).isZero();
        assertThat(modular.output()).contains(
                "PROVIDER_PLUGIN=media.transcode.ffmpeg@1.0.0",
                "PROVIDER=ffmpeg", "IMPLEMENTATION=ffmpeg.cpu.native-pull.v1");
        assertThat(embedded.output()).contains(
                "PROVIDER_PLUGIN=media.transcode.ffmpeg@1.0.0",
                "PROVIDER=ffmpeg", "IMPLEMENTATION=ffmpeg.cpu.native-pull.v1");
    }

    @Test
    void embedded_launcher_removes_its_controlled_extraction_directory_after_host_close() throws Exception {
        Path allInOne = Path.of(System.getProperty("distribution.allinone.jar"));
        Path controlledJavaTmp = Files.createDirectories(temp.resolve("launcher-java-tmp"));

        ProcessResult embedded = launchWithJavaTmp(allInOne, controlledJavaTmp);

        assertThat(embedded.exitCode()).as(embedded.output()).isZero();
        try (var survivors = Files.list(controlledJavaTmp)) {
            assertThat(survivors
                    .filter(path -> path.getFileName().toString()
                            .startsWith("media-platform-embedded-plugins-"))
                    .toList())
                    .as("embedded extraction directories surviving launcher exit")
                    .isEmpty();
        }
    }

    private static ProviderPluginHost host(Path directory) {
        return new ProviderPluginHost(directory, new PluginRegistryImpl(
                new PluginDescriptorValidator(), new PluginHealthRegistry()));
    }

    private static String sha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private static Path binary(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            throw new IllegalStateException("PATH is absent; cannot locate " + name);
        }
        for (String directory : path.split(
                java.util.regex.Pattern.quote(java.io.File.pathSeparator), -1)) {
            if (directory.isBlank()) {
                continue;
            }
            Path candidate = Path.of(directory).resolve(name).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(name + " unavailable on PATH");
    }

    private static byte[] generateInput(Path executable, Path output) throws Exception {
        Process process = new ProcessBuilder(
                executable.toString(), "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "testsrc=size=64x48:rate=5",
                "-t", "1", "-c:v", "mpeg4", "-pix_fmt", "yuv420p", output.toString())
                .redirectErrorStream(true).start();
        byte[] diagnostic = process.getInputStream().readAllBytes();
        assertThat(process.waitFor()).as(new String(diagnostic)).isZero();
        return Files.readAllBytes(output);
    }

    private static ProcessResult launch(Path jar, String... arguments) throws Exception {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add(binary("java").toString());
        command.add("-jar");
        command.add(jar.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        return new ProcessResult(process.waitFor(), output);
    }

    private static ProcessResult launchWithJavaTmp(Path jar, Path javaTmp) throws Exception {
        Process process = new ProcessBuilder(
                binary("java").toString(),
                "-Djava.io.tmpdir=" + javaTmp.toAbsolutePath().normalize(),
                "-jar", jar.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        return new ProcessResult(process.waitFor(), output);
    }

    private record ProcessResult(int exitCode, String output) {}
}
