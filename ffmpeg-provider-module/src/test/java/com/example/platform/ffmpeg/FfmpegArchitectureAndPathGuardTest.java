package com.example.platform.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.sandbox.SandboxResolution;
import com.example.platform.sandbox.SandboxRuntimeCapabilities;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.domain.providernative.ExecutionCommand;
import com.example.platform.workerfabric.domain.providernative.ProcessInvocationSpec;
import com.example.platform.workerfabric.reuse.MaterializedArtifact;
import com.example.platform.workerfabric.reuse.MaterializedExecutionInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegArchitectureAndPathGuardTest {

    @TempDir Path temp;

    @Test
    void staging_escape_and_unknown_materialized_input_token_are_rejected() throws Exception {
        Path executable = Files.writeString(temp.resolve("ffmpeg"), "fixture").toAbsolutePath();
        executable.toFile().setExecutable(true);
        Path workspace = Files.createDirectory(temp.resolve("workspace")).toAbsolutePath();
        Path outside = Files.createDirectory(temp.resolve("outside")).toAbsolutePath();
        Path inputPath = Files.writeString(temp.resolve("input.mp4"), "fixture").toAbsolutePath();
        ExecutionInputId inputId = new ExecutionInputId("input-media");
        ContentDigest digest = ContentDigest.sha256("a".repeat(64));
        ArtifactPin pin = new ArtifactPin(new ArtifactId("source-media"), digest);
        MaterializedExecutionInput input = new MaterializedExecutionInput(
                inputId, pin, new MaterializedArtifact(pin, inputPath, Files.size(inputPath)));
        ExecutionCommand exactCommand = command(executable,
                FfmpegCpuRuntimeAdapter.materializedInputToken(inputId));

        var escaping = new FfmpegSandboxExecutionPolicyResolver(
                executable,
                new FfmpegSandboxWorkspace(
                        workspace, workspace.resolve("tmp"), outside, workspace),
                Duration.ofSeconds(1), 4096,
                SandboxRuntimeCapabilities.unavailable("path-test"));
        var exactWorkspace = new FfmpegSandboxExecutionPolicyResolver(
                executable, FfmpegSandboxWorkspace.under(workspace), Duration.ofSeconds(1), 4096,
                SandboxRuntimeCapabilities.unavailable("path-test"));

        assertThat(escaping.resolve(exactCommand, List.of(input)))
                .isInstanceOf(SandboxResolution.Rejected.class);
        assertThat(exactWorkspace.resolve(command(executable,
                FfmpegCpuRuntimeAdapter.materializedInputToken(
                        new ExecutionInputId("input-unknown"))), List.of(input)))
                .isInstanceOf(SandboxResolution.Rejected.class);
    }

    @Test
    void core_has_no_concrete_provider_dependency_and_provider_has_no_forbidden_authority() throws Exception {
        Path root = repositoryRoot();
        String settings = Files.readString(root.resolve("settings.gradle.kts"));
        assertThat(settings).contains("\"ffmpeg-provider-module\"");
        for (String core : List.of(
                "media-execution-plan-module", "worker-fabric-module", "sandbox-isolation-module")) {
            assertThat(Files.readString(root.resolve(core).resolve("build.gradle.kts")))
                    .doesNotContain("ffmpeg-provider-module");
            assertThat(readJava(root.resolve(core).resolve("src/main/java")))
                    .doesNotContain("com.example.platform.ffmpeg", "FfmpegCpu", "FFmpegCpu");
        }
        String provider = readJava(root.resolve("ffmpeg-provider-module/src/main/java"));
        assertThat(provider)
                .doesNotContain(
                        "new ProcessBuilder", "Runtime.getRuntime().exec", "shellCommand",
                        "ArtifactCommitService", "ArtifactOutputCommitOrchestrator",
                        "ResourceAccounting", "FAOF3", "Roadmap23")
                .doesNotContain("NVIDIA", "CUDA", "OpenCue", "BMF", "cloud");
    }

    private static ExecutionCommand command(Path executable, String token) {
        return new ExecutionCommand(
                new ExecutableTaskId("b".repeat(64)),
                FfmpegCpuProvider.BINDING,
                ExecutionAttemptId.of("attempt-path-guard"),
                ExecutionOwnershipGeneration.first(),
                0,
                ProcessInvocationSpec.of(executable.toString(), List.of("-i", token, "pipe:1")));
    }

    private static String readJava(Path directory) throws Exception {
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
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
