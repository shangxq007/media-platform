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
import com.example.platform.sandbox.SandboxProcessExecutionException;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SandboxRuntimeCommandExecutorTest {

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
                .isInstanceOfSatisfying(SandboxProcessExecutionException.class, failure ->
                        assertThat(failure.failure().code())
                                .isEqualTo(SandboxFailureCode.OUTPUT_STAGING_FAILED));
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
                .isInstanceOfSatisfying(SandboxProcessExecutionException.class,
                        failure -> assertThat(failure.failure()).isEqualTo(cleanup));
    }

    private RuntimeExecutionBundle bundle() {
        ExecutableTaskId taskId = mock(ExecutableTaskId.class);
        ProviderBindingPin binding = mock(ProviderBindingPin.class);
        ExecutionAttemptId attempt = ExecutionAttemptId.of("attempt-sandbox-test");
        ExecutionOwnershipGeneration generation = ExecutionOwnershipGeneration.first();
        ExecutionCommand command = new ExecutionCommand(taskId, binding, attempt, generation, 0,
                ProcessInvocationSpec.of("/bin/true", List.of()));
        return new RuntimeExecutionBundle(taskId, binding, attempt, generation, List.of(command));
    }

    private SandboxExecutionResult result(
            OptionalInt exit, byte[] stdout, Optional<SandboxFailure> failure) {
        Instant now = Instant.now();
        return new SandboxExecutionResult(exit, new BoundedCapture(stdout, false),
                new BoundedCapture(new byte[0], false), failure,
                new SandboxExecutionObservation(new SandboxExecutionHandle(1, now),
                        java.nio.file.Path.of("/tmp"), Duration.ZERO,
                        new SandboxCleanupObservation(failure.isEmpty(), 0, List.of(), "")));
    }
}
