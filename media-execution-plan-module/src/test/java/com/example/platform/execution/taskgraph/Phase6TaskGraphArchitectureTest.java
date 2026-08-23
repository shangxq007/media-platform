package com.example.platform.execution.taskgraph;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.graph.api.DirectedGraphView;
import com.example.platform.graph.api.GraphAlgorithms;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6TaskGraphArchitectureTest {

    private static final Pattern FORBIDDEN_RUNTIME_AUTHORITY = Pattern.compile(
            "\\b(?:WorkerRuntimeId|WorkerRuntimeIncarnationId|PhysicalHostId|"
                    + "PhysicalHostIncarnationId|DeviceAssignment|ExecutionBackend|"
                    + "ExecutionAssignment|Reservation|TaskLease|ExecutionAttempt|"
                    + "ExecutionOwnershipGeneration|ProviderProbe|ObservedUsage|"
                    + "SchedulableCapacity|queue|heartbeat|timestamp|logs|telemetry|"
                    + "correlation|trace|currentTimeMillis|nanoTime|randomUUID)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FORBIDDEN_RUNTIME_IMPORT = Pattern.compile(
            "(?m)^import\\s+.*(?:workerfabric|worker_fabric|worker\\.fabric|"
                    + "\\.Reservation(?:[.;]|$)|\\.PhysicalHost[A-Za-z0-9_]*(?:[.;]|$)|"
                    + "\\.WorkerRuntime[A-Za-z0-9_]*(?:[.;]|$)|"
                    + "\\.DeviceAssignment[A-Za-z0-9_]*(?:[.;]|$)|"
                    + "\\.ProviderProbe[A-Za-z0-9_]*(?:[.;]|$)|"
                    + "\\.TaskLease[A-Za-z0-9_]*(?:[.;]|$)|"
                    + "\\.ExecutionAttempt[A-Za-z0-9_]*(?:[.;]|$)|"
                    + "\\.ExecutionBackend[A-Za-z0-9_]*(?:[.;]|$))");

    @Test
    void etgContainsNoRuntimePlacementLifecycleOrTelemetryAuthority() throws IOException {
        String source = stripComments(readJavaSources(taskGraphSourceRoot()));

        assertEquals(0, FORBIDDEN_RUNTIME_IMPORT.matcher(source).results().count(),
                "MUTABLE_RUNTIME_IMPORT_COUNT=0");
        assertEquals(0, FORBIDDEN_RUNTIME_AUTHORITY.matcher(source).results().count(),
                "ETG_RUNTIME_FIELD_COUNT=0");
        assertFalse(source.contains("worker-fabric-module"));
        assertFalse(Files.readString(repoRoot().resolve(
                        "media-execution-plan-module/build.gradle.kts"))
                .contains("worker-fabric-module"));
    }

    @Test
    void executableTaskHasOneDerivedProviderBindingAndNoSecondSelectionField() {
        Set<Class<?>> fieldTypes = Arrays.stream(ExecutableTask.class.getDeclaredFields())
                .map(Field::getType).collect(java.util.stream.Collectors.toSet());
        assertFalse(fieldTypes.contains(ProviderBindingPin.class));
        assertEquals(ProviderBindingPin.class,
                assertMethod(ExecutableTask.class, "providerBindingPin").getReturnType());
        assertFalse(Arrays.stream(ExecutableTask.class.getDeclaredFields())
                .anyMatch(field -> field.getType().getSimpleName().equals("ProviderImplementationId")));
    }

    @Test
    void boundaryActionHasNoIndependentSchedulerLifecycleOrOwnershipSurface() {
        List<String> components = Arrays.stream(BoundaryAction.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertEquals(List.of("phase", "deterministicOrder", "target"), components);
        assertFalse(BoundaryAction.INDEPENDENTLY_SCHEDULABLE);
        assertTrue(Arrays.stream(ExecutableTask.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("boundaryActions")
                        && field.getType().equals(List.class)));
    }

    @Test
    void etgReusesPlatformDagMechanicsAndDefinesNoCompetingAlgorithm() throws IOException {
        String graphSource = readJavaSources(compositionSourceRoot(), taskGraphSourceRoot());
        assertTrue(graphSource.contains(DirectedGraphView.class.getName()));
        assertTrue(graphSource.contains(GraphAlgorithms.class.getName()));
        String executableSource = stripComments(graphSource);
        assertFalse(executableSource.contains("topologicalSort("));
        assertFalse(executableSource.contains("detectCycle("));
        assertFalse(executableSource.contains("PriorityQueue"));
        assertFalse(Pattern.compile("\\b(?:inDegree|indegree)\\b")
                .matcher(executableSource).find());
    }

    @Test
    void canonicalEncodingIsExplicitVersionedAndGraphDigestIsNotBusinessIdentity()
            throws IOException {
        String source = stripComments(readJavaSources(taskGraphSourceRoot()));
        assertTrue(source.contains("roadmap22.executable-task.v1"));
        assertTrue(source.contains("roadmap22.provider-bound-executable-task-graph.v1"));
        assertFalse(source.contains("ObjectOutputStream"));
        assertFalse(source.contains("java.lang.reflect"));
        assertFalse(source.contains("Object::toString"));
        assertFalse(source.contains("implements Serializable"));
        assertNotEquals(ExecutableTaskGraphDigest.class, ExecutableTaskId.class);
        assertFalse(source.matches("(?s).*\\b(?:class|record)\\s+ExecutableTaskGraphId\\b.*"));
    }

    @Test
    void etgSourceDefinesNoPhysicalPlanMutationSurface() throws IOException {
        String source = stripComments(readJavaSources(taskGraphSourceRoot()));
        Pattern mutation = Pattern.compile(
                "\\b(?:setUnits|setTypedInputs|setTypedOutputs|rewritePhysicalPlan|"
                        + "rewriteLogicalExecutionGraph|rewriteRenderGraph|rewriteRenderPlan)\\s*\\(");
        assertEquals(0, mutation.matcher(source).results().count(),
                "PHYSICAL_PLAN_MUTATION_FROM_ETG_COUNT=0");
        assertFalse(source.contains(".units().add("));
        assertFalse(source.contains(".units().remove("));
        assertFalse(source.contains(".units().clear("));
    }

    private static java.lang.reflect.Method assertMethod(
            Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getDeclaredMethod(name, parameters);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("missing method " + name, exception);
        }
    }

    private static Path repoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.exists(current.resolve(".git"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }

    private static Path taskGraphSourceRoot() {
        return repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/taskgraph");
    }

    private static Path compositionSourceRoot() {
        return repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/composition");
    }

    private static String readJavaSources(Path... roots) throws IOException {
        StringBuilder source = new StringBuilder();
        for (Path root : roots) {
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                    source.append(Files.readString(file)).append('\n');
                }
            }
        }
        return source.toString();
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ");
    }
}
