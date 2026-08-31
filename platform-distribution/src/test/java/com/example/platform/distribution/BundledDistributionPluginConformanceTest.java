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
import java.util.List;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundledDistributionPluginConformanceTest {

    @TempDir Path temp;

    @Test
    void bundledPluginConformanceDistributionHasTheExpectedLauncherAndPlugin() throws Exception {
        Path executable = Path.of(System.getProperty("distribution.executable.jar"));
        assertThat(executable.getFileName().toString()).isEqualTo("media-platform-all-in-one.jar");
        try (JarFile jar = new JarFile(executable.toFile())) {
            assertThat(jar.getManifest().getMainAttributes().getValue("Start-Class"))
                    .isEqualTo(PlatformDistributionLauncher.class.getName());
            assertThat(jar.stream()
                    .filter(entry -> entry.getName().startsWith("embedded-plugins/")
                            && entry.getName().endsWith(".jar"))
                    .count()).isEqualTo(1);
        }
    }

    @Test
    void bundled_mode_uses_exact_producer_bytes_and_pf4j_contracts() throws Exception {
        Path producerPlugin = Path.of(System.getProperty("distribution.provider.jar"));
        Path allInOne = Path.of(System.getProperty("distribution.executable.jar"));
        EmbeddedPluginExtractor.ExtractedPlugin embedded = EmbeddedPluginExtractor.extractSingle(
                allInOne, "embedded-plugins/", temp.resolve("embedded-host"));

        assertThat(sha256(producerPlugin)).isEqualTo(embedded.sha256());
        assertThat(Files.readAllBytes(producerPlugin))
                .containsExactly(Files.readAllBytes(embedded.pluginJar()));

        ProviderPluginHost host = host(embedded.pluginJar().getParent());
        try (host) {
            ProviderPluginContribution contribution =
                    host.loadAndStart().contributions().getFirst();

            assertThat(contribution.pluginId()).isEqualTo("media.transcode.ffmpeg");
            assertThat(contribution.pluginVersion()).isEqualTo("1.0.0");
            assertThat(contribution.providerBindingPin().providerId().value()).isEqualTo("ffmpeg");
            assertThat(contribution.providerBindingPin().providerImplementationId().value())
                    .isEqualTo("ffmpeg.cpu.native-pull.v1");

            Path executable = binary("ffmpeg");
            byte[] source = generateInput(executable, temp.resolve("source.mp4"));
            ExternalProviderClosedLoopHarness.Outcome outcome =
                    ExternalProviderClosedLoopHarness.success(
                            contribution, executable, temp.resolve("bundled-run"), source, "bundled");
            assertThat(outcome.commitCount()).isEqualTo(1);
            assertThat(outcome.completionCount()).isEqualTo(1);

            var failure = ExternalProviderClosedLoopHarness.failure(
                    contribution, executable, temp.resolve("bundled-failure"),
                    "not-media".getBytes(), false, "bundled-failure");
            assertThat(failure.code()).isEqualTo(ProviderNativeFailureCode.PROCESS_NONZERO_EXIT);
            assertThat(failure.commitCount()).isZero();
            assertThat(failure.completionCount()).isZero();
            assertThat(failure.publicationCount()).isZero();

            var cancellation = ExternalProviderClosedLoopHarness.failure(
                    contribution, executable, temp.resolve("bundled-cancel"),
                    source, true, "bundled-cancel");
            assertThat(cancellation.code()).isEqualTo(ProviderNativeFailureCode.PROCESS_CANCELLED);
            assertThat(cancellation.commitCount()).isZero();
            assertThat(cancellation.completionCount()).isZero();
            assertThat(cancellation.publicationCount()).isZero();

            assertThat(host.disable(contribution.pluginId())).isTrue();
            assertThat(host.catalog().contributions()).isEmpty();
        }
        assertThat(host.catalog().contributions()).isEmpty();
    }

    @Test
    void executable_all_in_one_launcher_uses_the_bundled_pf4j_host() throws Exception {
        Path allInOne = Path.of(System.getProperty("distribution.executable.jar"));

        ProcessResult embedded = launch(allInOne);

        assertThat(embedded.exitCode()).as(embedded.output()).isZero();
        assertThat(embedded.output()).contains(
                "PROVIDER_PLUGIN=media.transcode.ffmpeg@1.0.0",
                "PROVIDER=ffmpeg", "IMPLEMENTATION=ffmpeg.cpu.native-pull.v1");
    }

    @Test
    void embedded_launcher_removes_its_controlled_extraction_directory_after_host_close()
            throws Exception {
        Path allInOne = Path.of(System.getProperty("distribution.executable.jar"));
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
