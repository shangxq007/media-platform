package com.example.platform.workerfabric.domain.providernative;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ProviderNativeArchitectureGuardTest {

    private static final Path REPO_ROOT = repoRoot();
    private static final Path PROVIDER_NATIVE_MAIN = REPO_ROOT.resolve(
            "worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/providernative");
    private static final Path MEDIA_EXECUTION_PLAN_BUILD = REPO_ROOT.resolve("media-execution-plan-module/build.gradle.kts");
    private static final Path MEDIA_EXECUTION_PLAN_MAIN = REPO_ROOT.resolve("media-execution-plan-module/src/main/java");
    private static final Path RENDER_MAIN = REPO_ROOT.resolve("render-module/src/main/java");
    private static final Path WORKER_MAIN = REPO_ROOT.resolve("worker-fabric-module/src/main/java");

    @Test
    void mediaExecutionPlanDoesNotDependOnWorkerFabric() {
        String build = source(MEDIA_EXECUTION_PLAN_BUILD);
        assertThat(build).doesNotContain("worker-fabric-module", ":worker-fabric-module");
        assertThat(javaSources(MEDIA_EXECUTION_PLAN_MAIN)).doesNotContain("com.example.platform.workerfabric");
    }

    @Test
    void providerBoundExecutionModelDoesNotGainNativeCommandFields() {
        String source = source(MEDIA_EXECUTION_PLAN_MAIN.resolve(
                "com/example/platform/execution/taskgraph/ExecutableTask.java"));
        assertThat(source).doesNotContain("ProviderNativeExecutionPlan", "InvocationSpec", "RuntimeAdapter");
        assertThat(Pattern.compile("String\\s+(?:shellCommand|commandLine|nativeCommand|ffmpegCommand)")
                .matcher(source).results().count()).isZero();
    }

    @Test
    void planLowererPackageDoesNotImportMutableRuntimeAuthorities() {
        String source = codeOnly(javaSources(PROVIDER_NATIVE_MAIN));
        assertThat(source).doesNotContain(
                "HostResourceSnapshot",
                "SchedulableCapacity",
                "TaskLease",
                "WorkerRuntimeAvailability",
                "Heartbeat",
                "Probe",
                "Scheduler",
                "ReservationLedger");
    }

    @Test
    void genericProviderNativePlanDoesNotExposeStringCommandAuthority() {
        String source = javaSources(PROVIDER_NATIVE_MAIN);
        assertThat(source).doesNotContain("String shellCommand", "String commandLine");
        assertThat(source).contains("List<String> arguments");
    }

    @Test
    void providerNativeInvocationRootPermitsOnlyTheImplementedProcessShape() {
        assertThat(PROVIDER_NATIVE_MAIN.resolve("BackendSubmissionInvocationSpec.java")).doesNotExist();

        String invocationKind = source(PROVIDER_NATIVE_MAIN.resolve("InvocationKind.java"));
        assertThat(invocationKind).doesNotContain("BACKEND_SUBMISSION");

        String invocationSpec = source(PROVIDER_NATIVE_MAIN.resolve("InvocationSpec.java"));
        assertThat(invocationSpec).contains("permits ProcessInvocationSpec");
        assertThat(javaSources(PROVIDER_NATIVE_MAIN)).doesNotContain(
                "BackendSubmissionInvocationSpec",
                "HttpInvocationSpec",
                "GrpcInvocationSpec",
                "NativeLibraryInvocationSpec",
                "GenericInvocationSpec");
    }

    @Test
    void providerNativeSpiDoesNotExposeGenericProviderOrBackendParameterBags() {
        String source = codeOnly(javaSources(PROVIDER_NATIVE_MAIN));

        assertThat(source).doesNotContain(
                "ProviderParameterBag",
                "BackendParameterBag",
                "providerParameters");
        assertThat(Pattern.compile("Map\\s*<\\s*String\\s*,\\s*Object\\s*>")
                .matcher(source).results().count()).isZero();
        assertThat(Pattern.compile("Map\\s*<\\s*String\\s*,\\s*String\\s*>\\s+typedFields\\b")
                .matcher(source).results().count()).isZero();
        assertThat(Pattern.compile("(?:JsonNode|Object)\\s+providerParameters\\b")
                .matcher(source).results().count()).isZero();
        assertThat(Pattern.compile("(?:Map\\s*<[^>]+>|JsonNode|Object)\\s+payload\\b")
                .matcher(source).results().count()).isZero();

        assertThat(source).contains("Map<String, String> environmentOverrides");
    }

    @Test
    void runtimeAdapterDoesNotDependOnCanonicalDomainRepositoriesForMutation() {
        String source = source(PROVIDER_NATIVE_MAIN.resolve("RuntimeAdapter.java"));
        assertThat(source).doesNotContain("Repository", "DSLContext", "EntityManager", "JdbcTemplate");
    }

    @Test
    void noConcreteFfmpegProviderProductionImplementationInPhase15() {
        String providerNativeSource = javaSources(PROVIDER_NATIVE_MAIN);
        assertThat(providerNativeSource).doesNotContain("FFmpeg", "Ffmpeg", "filter_complex", "libx264", "NVENC", "QSV", "VAAPI");
    }

    @Test
    void noNewDirectRenderOrTimelineToProviderNativeCompiler() {
        String renderSource = javaSources(RENDER_MAIN);
        assertThat(renderSource).doesNotContain(
                "com.example.platform.workerfabric.domain.providernative.PlanLowerer",
                "com.example.platform.workerfabric.domain.providernative.RuntimeAdapter",
                "ProviderNativeExecutionPlan");
    }

    @Test
    void noCrossTaskLoweringFusionApi() {
        String source = javaSources(PROVIDER_NATIVE_MAIN);
        assertThat(source).doesNotContain(
                "Collection<ExecutableTask>",
                "List<ExecutableTask>",
                "Set<ExecutableTask>",
                "ExecutableTaskGraph");
        assertThat(source).contains("lower(ExecutableTask task, StaticProviderExecutionContext context)");
    }

    @Test
    void runtimeAdapterHasNoProviderRebindOrLatestFallbackAuthority() {
        String source = codeOnly(javaSources(PROVIDER_NATIVE_MAIN));
        assertThat(source).doesNotContain(
                "providerName",
                "providerType",
                "latest provider",
                "default provider",
                "preferred provider",
                "rebind",
                "fallback");
    }

    @Test
    void nativeCommandDoesNotOwnIndependentLifecycleIdentity() {
        String commandSource = source(PROVIDER_NATIVE_MAIN.resolve("ExecutionCommand.java"));
        assertThat(commandSource).contains("platformExecutionAttemptId", "platformOwnershipGeneration");
        assertThat(commandSource).doesNotContain("LeaseId", "TaskLease", "ExecutionAssignmentId");
        assertThat(Pattern.compile("new\\s+ExecutionAttempt\\s*\\(").matcher(javaSources(PROVIDER_NATIVE_MAIN))
                .results().count()).isZero();
    }

    private static String codeOnly(String source) {
        String withoutBlockComments = source.replaceAll("(?s)/\\*.*?\\*/", "");
        String withoutLineComments = withoutBlockComments.replaceAll("(?m)//.*$", "");
        return withoutLineComments.replaceAll("\"(?:\\.|[^\\\"])*\"", "\"\"");
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }

    private static String javaSources(Path root) {
        try (var stream = Files.walk(root)) {
            return stream.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .map(ProviderNativeArchitectureGuardTest::source)
                    .reduce("", (left, right) -> left + "\n" + right);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String source(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
