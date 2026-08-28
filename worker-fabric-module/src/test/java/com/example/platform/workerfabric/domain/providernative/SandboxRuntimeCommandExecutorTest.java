package com.example.platform.workerfabric.domain.providernative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.platform.sandbox.BoundedCapture;
import com.example.platform.sandbox.BoundedProcessLauncher;
import com.example.platform.sandbox.EffectiveSandboxExecutionSpecification;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.sandbox.SandboxCleanupObservation;
import com.example.platform.sandbox.SandboxExecutionHandle;
import com.example.platform.sandbox.SandboxExecutionObservation;
import com.example.platform.sandbox.SandboxExecutionRequirement;
import com.example.platform.sandbox.SandboxExecutionResult;
import com.example.platform.sandbox.SandboxFailure;
import com.example.platform.sandbox.SandboxFailureCode;
import com.example.platform.sandbox.SandboxResolution;
import com.example.platform.sandbox.SandboxRuntimeCapabilities;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.domain.SandboxRuntimeAvailability;
import com.example.platform.workerfabric.domain.SandboxRuntimeRequirement;
import com.example.platform.workerfabric.domain.providernative.ExecutionCommand;
import com.example.platform.workerfabric.domain.providernative.ProcessInvocationSpec;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionBundle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SandboxRuntimeCommandExecutorTest {

    private static final String UNRELATED_SECRET_ENVIRONMENT_FIXTURE =
            "D1_SECRET_FIXTURE_MUST_NOT_LEAK";

    @Test
    void worker_eligibility_and_neutral_capability_evidence_must_agree() {
        SandboxResolution resolution = WorkerSandboxEligibilityResolver.resolve(
                SandboxRuntimeRequirement.REQUIRED,
                SandboxRuntimeAvailability.AVAILABLE,
                mock(SandboxExecutionRequirement.class),
                SandboxRuntimeCapabilities.unavailable("probe-failed"));

        assertThat(resolution).isEqualTo(new SandboxResolution.Rejected(SandboxFailure.of(
                SandboxFailureCode.SANDBOX_UNAVAILABLE,
                "coarse sandbox availability disagrees with capability evidence",
                Set.of())));
    }

    @Test
    void exit_zero_without_candidate_bytes_cannot_reach_output_staging_or_completion() {
        BoundedProcessLauncher launcher = (specification, cancellation) -> result(
                OptionalInt.of(0), new byte[0], Optional.empty());
        SandboxRuntimeCommandExecutor executor = new SandboxRuntimeCommandExecutor(
                launcher, (command, inputs) -> new SandboxResolution.Resolved(mock(
                        EffectiveSandboxExecutionSpecification.class)), SandboxCancellation.never());

        assertThatThrownBy(() -> executor.execute(bundle(), List.of()))
                .isInstanceOfSatisfying(ProviderNativeExecutionFailure.class, failure ->
                        assertThat(failure.code())
                                .isEqualTo(ProviderNativeFailureCode.EMPTY_PROVIDER_OUTPUT));
    }

    @Test
    void cleanup_failure_is_observable_and_cannot_become_provider_output() {
        SandboxFailure cleanup = SandboxFailure.of(
                SandboxFailureCode.SANDBOX_CLEANUP_FAILED, "survivor", Set.of());
        BoundedProcessLauncher launcher = (specification, cancellation) -> result(
                OptionalInt.of(0), "candidate".getBytes(), Optional.of(cleanup));
        SandboxRuntimeCommandExecutor executor = new SandboxRuntimeCommandExecutor(
                launcher, (command, inputs) -> new SandboxResolution.Resolved(mock(
                        EffectiveSandboxExecutionSpecification.class)), SandboxCancellation.never());

        assertThatThrownBy(() -> executor.execute(bundle(), List.of()))
                .isInstanceOfSatisfying(ProviderNativeExecutionFailure.class,
                        failure -> assertThat(failure.code())
                                .isEqualTo(ProviderNativeFailureCode.PROCESS_CLEANUP_FAILED));
    }

    @Test
    void truncated_stdout_cannot_become_provider_output() {
        BoundedProcessLauncher launcher = (specification, cancellation) -> result(
                OptionalInt.of(0),
                new BoundedCapture("partial-media".getBytes(), true),
                Optional.empty());
        SandboxRuntimeCommandExecutor executor = new SandboxRuntimeCommandExecutor(
                launcher, (command, inputs) -> new SandboxResolution.Resolved(mock(
                        EffectiveSandboxExecutionSpecification.class)), SandboxCancellation.never());

        assertThatThrownBy(() -> executor.execute(bundle(), List.of()))
                .isInstanceOfSatisfying(ProviderNativeExecutionFailure.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                ProviderNativeFailureCode.PROCESS_OUTPUT_TRUNCATED));
    }

    @Test
    void launch_nonzero_cancel_and_unknown_failures_map_to_platform_algebra() {
        assertMapped(SandboxFailureCode.PROCESS_LAUNCH_FAILED,
                ProviderNativeFailureCode.PROCESS_LAUNCH_FAILED);
        assertMapped(SandboxFailureCode.PROCESS_CRASHED,
                ProviderNativeFailureCode.PROCESS_NONZERO_EXIT);
        assertMapped(SandboxFailureCode.PROCESS_TERMINATED_BY_LIMIT,
                ProviderNativeFailureCode.PROCESS_CANCELLED);

        BoundedProcessLauncher unknown = (specification, cancellation) -> {
            throw new IOException("unclassified launcher failure");
        };
        SandboxRuntimeCommandExecutor executor = new SandboxRuntimeCommandExecutor(
                unknown, (command, inputs) -> new SandboxResolution.Resolved(mock(
                        EffectiveSandboxExecutionSpecification.class)), SandboxCancellation.never());
        assertThatThrownBy(() -> executor.execute(bundle(), List.of()))
                .isInstanceOfSatisfying(ProviderNativeExecutionFailure.class,
                        failure -> assertThat(failure.code())
                                .isEqualTo(ProviderNativeFailureCode.RUNTIME_EXECUTION_UNKNOWN));
    }

    @Test
    void failure_preserves_bounded_process_evidence_without_environment_dump() {
        byte[] stdout = "bounded-output".getBytes(StandardCharsets.UTF_8);
        String stderr = "stable diagnostic fixture: process rejected its arguments";
        SandboxFailure sandboxFailure = SandboxFailure.of(
                SandboxFailureCode.PROCESS_CRASHED, "typed process crash", Set.of());
        BoundedProcessLauncher launcher = (specification, cancellation) -> result(
                OptionalInt.of(8),
                new BoundedCapture(stdout, false),
                new BoundedCapture(stderr.getBytes(StandardCharsets.UTF_8), true),
                Optional.of(sandboxFailure));
        SandboxRuntimeCommandExecutor executor = new SandboxRuntimeCommandExecutor(
                launcher, (command, inputs) -> new SandboxResolution.Resolved(mock(
                        EffectiveSandboxExecutionSpecification.class)), SandboxCancellation.never());

        assertThatThrownBy(() -> executor.execute(bundle(), List.of()))
                .isInstanceOfSatisfying(ProviderNativeExecutionFailure.class, failure -> {
                    assertThat(failure.code())
                            .isEqualTo(ProviderNativeFailureCode.PROCESS_NONZERO_EXIT);
                    assertThat(failure.diagnostics()).isEqualTo(Map.of(
                            "sandboxFailureCode", "PROCESS_CRASHED",
                            "processExitCode", "8",
                            "boundedStderr", stderr,
                            "stderrTruncated", "true",
                            "boundedStdoutSize", Integer.toString(stdout.length),
                            "stdoutTruncated", "false"));
                    assertThat(failure.diagnostics().values())
                            .noneMatch(value -> value.contains(UNRELATED_SECRET_ENVIRONMENT_FIXTURE));
                });
    }

    private void assertMapped(
            SandboxFailureCode sandboxCode, ProviderNativeFailureCode expected) {
        SandboxFailure sandboxFailure = SandboxFailure.of(sandboxCode, "typed", Set.of());
        BoundedProcessLauncher launcher = (specification, cancellation) -> result(
                OptionalInt.empty(), new byte[0], Optional.of(sandboxFailure));
        SandboxRuntimeCommandExecutor executor = new SandboxRuntimeCommandExecutor(
                launcher, (command, inputs) -> new SandboxResolution.Resolved(mock(
                        EffectiveSandboxExecutionSpecification.class)), SandboxCancellation.never());

        assertThatThrownBy(() -> executor.execute(bundle(), List.of()))
                .isInstanceOfSatisfying(ProviderNativeExecutionFailure.class,
                        failure -> assertThat(failure.code()).isEqualTo(expected));
    }

    private RuntimeExecutionBundle bundle() {
        ExecutableTaskId taskId = mock(ExecutableTaskId.class);
        ProviderBindingPin binding = mock(ProviderBindingPin.class);
        ExecutionAttemptId attempt = ExecutionAttemptId.of("attempt-sandbox-test");
        ExecutionOwnershipGeneration generation = ExecutionOwnershipGeneration.first();
        ProcessInvocationSpec invocation = new ProcessInvocationSpec(
                "/bin/true",
                List.of(),
                Map.of("UNRELATED_SECRET", UNRELATED_SECRET_ENVIRONMENT_FIXTURE),
                Optional.empty());
        ExecutionCommand command = new ExecutionCommand(
                taskId, binding, attempt, generation, 0, invocation);
        return new RuntimeExecutionBundle(taskId, binding, attempt, generation, List.of(command));
    }

    private SandboxExecutionResult result(
            OptionalInt exit, byte[] stdout, Optional<SandboxFailure> failure) {
        return result(exit, new BoundedCapture(stdout, false), failure);
    }

    private SandboxExecutionResult result(
            OptionalInt exit, BoundedCapture stdout, Optional<SandboxFailure> failure) {
        return result(exit, stdout, new BoundedCapture(new byte[0], false), failure);
    }

    private SandboxExecutionResult result(
            OptionalInt exit,
            BoundedCapture stdout,
            BoundedCapture stderr,
            Optional<SandboxFailure> failure) {
        Instant now = Instant.now();
        return new SandboxExecutionResult(exit, stdout, stderr, failure,
                new SandboxExecutionObservation(new SandboxExecutionHandle(1, now),
                        java.nio.file.Path.of("/tmp"), Duration.ZERO,
                        new SandboxCleanupObservation(failure.isEmpty(), 0, List.of(), "")));
    }
}
