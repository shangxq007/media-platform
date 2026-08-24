package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Mechanical zero guards for Roadmap #22 Epoch 3 Task C. */
class TaskCArchitectureGuardTest {

    @Test
    void workerSelfSchedulingCountIsZero() {
        Set<Class<?>> componentTypes = Arrays.stream(RequestWork.class.getRecordComponents())
                .map(RecordComponent::getType)
                .collect(Collectors.toUnmodifiableSet());
        List<String> componentNames = Arrays.stream(RequestWork.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(componentTypes)
                .doesNotContain(ExecutableTaskId.class, ProviderBindingPin.class, ExecutionBackend.class);
        assertThat(componentNames).noneMatch(name -> name.matches(
                "(?i).*(task|queue|priority|fairness|deadline|provider|backend).*"));
    }

    @Test
    void requestWorkDuplicateActiveGrantCountIsZeroBySingleBoundary() {
        List<String> matcherFieldTypes = Arrays.stream(CentralWorkMatcher.class.getDeclaredFields())
                .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .map(Class::getSimpleName)
                .toList();

        assertThat(matcherFieldTypes).containsExactly("AtomicAssignmentGrantBoundary");
        assertThat(AtomicAssignmentGrantBoundary.ATOMIC_AUTHORITIES)
                .contains(AtomicAssignmentGrantBoundary.GrantAuthority.TASK_LEASE);
    }

    @Test
    void matcherSelectsTaskWithActiveLeaseCountIsZero() {
        assertThat(PendingNativeWorkCandidate.ClaimState.values()).contains(
                PendingNativeWorkCandidate.ClaimState.ACTIVE_NATIVE_LEASE);
        assertThat(PendingNativeWorkCandidate.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .contains("pendingWithoutActiveLease");
    }

    @Test
    void matcherUsesNondeterministicOrderingCountIsZero() throws IOException {
        String matcherSource = Files.readString(repoRoot().resolve(
                "worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/"
                        + "CentralWorkMatcher.java"));

        assertThat(matcherSource)
                .contains("Comparator.comparing")
                .contains("executableTask().id()")
                .doesNotContain("HashMap", "HashSet", "random", "shuffle", "currentTimeMillis");
    }

    @Test
    void taskCAndTaskDDoNotImplementTaskEAuthorities() throws IOException {
        Path domain = repoRoot().resolve(
                "worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain");
        Set<String> typeNames;
        try (var files = Files.list(domain)) {
            typeNames = files
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString().replace(".java", ""))
                    .collect(Collectors.toUnmodifiableSet());
        }

        assertThat(typeNames).doesNotContain(
                "LocalAdmission",
                "LeaseHeartbeat",
                "RetryScheduler",
                "OpenCueAdapter",
                "RemoteProviderAdapter");
    }

    private static Path repoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }
}
