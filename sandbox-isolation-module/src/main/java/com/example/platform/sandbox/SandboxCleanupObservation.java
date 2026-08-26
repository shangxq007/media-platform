package com.example.platform.sandbox;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

@org.springframework.modulith.NamedInterface("API")
public record SandboxCleanupObservation(
        boolean completed,
        boolean namedContainerRemoved,
        boolean engineClientReaped,
        boolean workloadProcessesContained,
        boolean captureStreamsClosed,
        int descendantsObserved,
        List<Long> survivors,
        List<Long> detachedEngineHelpers,
        OptionalLong engineClientProcessId,
        String containerName,
        String containerStatus,
        Optional<SandboxFailure> failure) {
    public SandboxCleanupObservation {
        if (descendantsObserved < 0) {
            throw new IllegalArgumentException("descendantsObserved must be non-negative");
        }
        survivors = List.copyOf(Objects.requireNonNull(survivors, "survivors"));
        detachedEngineHelpers = List.copyOf(Objects.requireNonNull(
                detachedEngineHelpers, "detachedEngineHelpers"));
        engineClientProcessId = Objects.requireNonNull(
                engineClientProcessId, "engineClientProcessId");
        containerName = Objects.requireNonNull(containerName, "containerName");
        containerStatus = Objects.requireNonNull(containerStatus, "containerStatus");
        failure = Objects.requireNonNull(failure, "failure");
        boolean proven = namedContainerRemoved && engineClientReaped
                && workloadProcessesContained && captureStreamsClosed;
        if (completed != proven) {
            throw new IllegalArgumentException(
                    "completed cleanup requires container absence, a reaped engine client, "
                            + "workload containment, and closed capture streams");
        }
        if (completed && (!survivors.isEmpty() || failure.isPresent())) {
            throw new IllegalArgumentException(
                    "completed cleanup cannot report blocking survivors or failure");
        }
        if (!completed && failure.isEmpty()) {
            throw new IllegalArgumentException("incomplete cleanup must report a typed failure");
        }
        if (failure.isPresent()
                && failure.orElseThrow().code() != SandboxFailureCode.SANDBOX_CLEANUP_FAILED) {
            throw new IllegalArgumentException("cleanup failure must use SANDBOX_CLEANUP_FAILED");
        }
    }

    public SandboxCleanupObservation(
            boolean completed,
            int descendantsObserved,
            List<Long> survivors,
            String failureMessage) {
        this(completed, true, completed, completed, true,
                descendantsObserved, survivors, List.of(), OptionalLong.empty(),
                "", "not-applicable",
                completed ? Optional.empty() : Optional.of(SandboxFailure.of(
                        SandboxFailureCode.SANDBOX_CLEANUP_FAILED,
                        failureMessage == null || failureMessage.isBlank()
                                ? "sandbox cleanup did not complete"
                                : failureMessage,
                        Set.of())));
    }

    public static SandboxCleanupObservation succeeded(
            int descendantsObserved, List<Long> survivors) {
        return new SandboxCleanupObservation(
                true, true, true, true, true,
                descendantsObserved, survivors, List.of(), OptionalLong.empty(),
                "", "not-applicable", Optional.empty());
    }

    public static SandboxCleanupObservation failed(
            int descendantsObserved, List<Long> survivors, String message) {
        return new SandboxCleanupObservation(
                false, true, false, false, true,
                descendantsObserved, survivors, List.of(), OptionalLong.empty(),
                "", "not-applicable", Optional.of(SandboxFailure.of(
                        SandboxFailureCode.SANDBOX_CLEANUP_FAILED, message, Set.of())));
    }

    public String failureMessage() {
        return failure.map(SandboxFailure::message).orElse("");
    }

    public String diagnostic() {
        return "cleanup{completed=" + completed
                + ", failureCode=" + failure.map(value -> value.code().name()).orElse("none")
                + ", failureMessage=" + failureMessage()
                + ", survivors=" + survivors
                + ", detachedEngineHelpers=" + detachedEngineHelpers
                + ", enginePid=" + (engineClientProcessId.isPresent()
                        ? engineClientProcessId.getAsLong() : "not-applicable")
                + ", engineClientReaped=" + engineClientReaped
                + ", containerName=" + (containerName.isBlank() ? "not-applicable" : containerName)
                + ", containerStatus=" + containerStatus
                + ", namedContainerRemoved=" + namedContainerRemoved
                + ", workloadProcessesContained=" + workloadProcessesContained
                + ", captureStreamsClosed=" + captureStreamsClosed
                + ", descendantsObserved=" + descendantsObserved + "}";
    }
}
