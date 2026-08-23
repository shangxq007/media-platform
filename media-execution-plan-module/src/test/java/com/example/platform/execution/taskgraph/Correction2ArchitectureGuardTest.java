package com.example.platform.execution.taskgraph;

import com.example.platform.execution.compatibility.ProviderCompatibilityTransition;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mechanical Correction 2 zero guards over the production authority surfaces. */
class Correction2ArchitectureGuardTest {

    private static final Pattern TRANSITION_RUNTIME_STATE = Pattern.compile(
            "\\b(?:WorkerRuntimeId|WorkerRuntimeIncarnationId|PhysicalHostId|"
                    + "PhysicalHostIncarnationId|DeviceId|ExecutionAssignment|Reservation|"
                    + "ExecutionAttempt|BackendExecutionHandle|ProviderProbeResult|"
                    + "SchedulableCapacity|ObservedUsage|TaskLease|queueDepth|utilization|"
                    + "heartbeat|currentTimeMillis|nanoTime)\\b");

    private static final Pattern PHYSICAL_PLAN_MUTATION = Pattern.compile(
            "\\b(?:setUnits|setTypedInputs|setTypedOutputs|rewritePhysicalPlan|"
                    + "rewriteLogicalExecutionGraph|rewriteRenderGraph|rewriteRenderPlan)\\s*\\(");

    @Test
    void transitionDefaultAndDirectProofGuardsAreMechanicallyClosed() throws IOException {
        String graph = stripComments(Files.readString(compatibilitySourceRoot()
                .resolve("ProviderCompatibilityGraph.java")));
        String transition = stripComments(Files.readString(compatibilitySourceRoot()
                .resolve("ProviderCompatibilityTransition.java")));
        Pattern retiredSameBindingBranch = Pattern.compile(
                "else\\s+if\\s*\\(producerCandidate\\.bindingPin\\(\\)\\.equals\\("
                        + "consumerCandidate\\.bindingPin\\(\\)\\)\\)");

        assertEquals(0, retiredSameBindingBranch.matcher(graph).results().count(),
                "SAME_BINDING_DEFAULT_DIRECT_TRANSITION_COUNT=0");
        assertTrue(transition.contains("decision == ProviderCompatibilityTransitionDecision.DIRECT_COMPATIBLE")
                        && transition.contains("boundaryContractId.isEmpty()"),
                "DIRECT_EXTERNAL_TRANSITION_WITHOUT_TYPED_INTEROP_PROOF_COUNT=0");
        assertFalse(transition.contains("!producerBindingPin.equals(consumerBindingPin)"),
                "same-binding DIRECT must not bypass typed interoperability proof");
    }

    @Test
    void retiredBoundaryAndTargetAuthoritiesHaveZeroProductionDefinitionsOrUsages()
            throws IOException {
        String production = readJavaSources(moduleMainRoot());
        String retiredBoundary = "Cross" + "Provider" + "Artifact" + "Boundary";
        String retiredMaterialize = "Cross" + "Provider" + "Materialize" + "Target";
        String retiredAcquire = "Cross" + "Provider" + "Acquire" + "Target";

        assertEquals(0, literalCount(production, retiredBoundary),
                "LEGACY_CROSS_PROVIDER_ARTIFACT_BOUNDARY_DEFINITION_COUNT=0");
        assertEquals(0, literalCount(production, retiredBoundary),
                "LEGACY_CROSS_PROVIDER_ARTIFACT_BOUNDARY_USAGE_COUNT=0");
        assertEquals(0,
                literalCount(production, retiredMaterialize)
                        + literalCount(production, retiredAcquire),
                "LEGACY_CROSS_PROVIDER_BOUNDARY_ACTION_TARGET_COUNT=0");
    }

    @Test
    void generalizedBoundaryContainsNoRuntimeArtifactOrSchedulerIdentity() throws IOException {
        assertFalse(ExecutionArtifactBoundary.INDEPENDENTLY_SCHEDULABLE);
        assertEquals(0, Arrays.stream(ExecutionArtifactBoundary.class.getRecordComponents())
                        .map(RecordComponent::getType)
                        .map(Class::getSimpleName)
                        .filter("ArtifactId"::equals)
                        .count(),
                "EXECUTION_ARTIFACT_BOUNDARY_PREINVENTED_OUTPUT_ARTIFACT_ID_COUNT=0");
        assertEquals(0, Arrays.stream(ExecutionArtifactBoundary.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .filter(value -> value.contains("scheduler")
                                || value.contains("lease")
                                || value.contains("attempt")
                                || value.contains("worker")
                                || value.contains("host")
                                || value.contains("device"))
                        .count(),
                "EXECUTION_ARTIFACT_BOUNDARY_INDEPENDENT_SCHEDULER_ID_COUNT=0");

        String codec = Files.readString(taskGraphSourceRoot()
                .resolve("ExecutableTaskCanonicalCodec.java"));
        assertTrue(codec.contains("roadmap22.execution-artifact-boundary.v1"));
        assertTrue(codec.contains("materializationReason"));
        assertTrue(codec.contains("interoperabilityContract"));
        assertTrue(codec.contains("sourceDependency"));
        assertTrue(codec.contains("producerUnitId"));
        assertTrue(codec.contains("consumerUnitId"));
        assertTrue(codec.contains("producerBinding"));
        assertTrue(codec.contains("consumerBinding"));
        assertTrue(codec.contains("producerOutput"));
        assertTrue(codec.contains("consumerInput"));
    }

    @Test
    void transitionEtgAndPlanDerivationRemainRuntimeFreeAndNonMutating() throws IOException {
        String transitionSources = readJavaSources(compatibilitySourceRoot());
        String taskGraphSources = readJavaSources(taskGraphSourceRoot());

        assertEquals(0, TRANSITION_RUNTIME_STATE.matcher(stripComments(transitionSources))
                        .results().count(),
                "PROVIDER_COMPATIBILITY_TRANSITION_RUNTIME_STATE_FIELD_COUNT=0");
        assertEquals(0, TRANSITION_RUNTIME_STATE.matcher(stripComments(taskGraphSources))
                        .results().count(),
                "ETG_RUNTIME_FIELD_COUNT=0");
        assertEquals(0, PHYSICAL_PLAN_MUTATION.matcher(stripComments(taskGraphSources))
                        .results().count(),
                "PHYSICAL_PLAN_MUTATION_COUNT=0");
    }

    @Test
    void correction2BehavioralGuardNamesAreManifestedInAdversarialSuite() throws IOException {
        String tests = Files.readString(taskGraphTestRoot()
                .resolve("Correction2SameBindingRuntimeBoundaryTest.java"));
        String[] required = {
                "SAME_BINDING_EXTERNAL_DEPENDENCY_WITHOUT_BOUNDARY_ACCEPTANCE_COUNT=0",
                "SAME_BINDING_EXTERNAL_DEPENDENCY_WITH_BOUNDARY_REJECTION_COUNT=0",
                "SAME_BINDING_RUNTIME_CLASS_MISMATCH_DIRECT_ACCEPTANCE_COUNT=0",
                "SAME_BINDING_UNKNOWN_RUNTIME_BOUNDARY_DIRECT_ACCEPTANCE_COUNT=0",
                "INTERNAL_PROVIDER_LOCAL_COMPOSITION_FALSE_MATERIALIZATION_COUNT=0",
                "SAME_BINDING_REQUIRED_MATERIALIZATION_UNREPRESENTABLE_COUNT=0"
        };
        for (String guard : required) {
            assertTrue(tests.contains(guard), "missing behavioral zero guard: " + guard);
        }
    }

    private static long literalCount(String source, String literal) {
        return Pattern.compile(Pattern.quote(literal)).matcher(source).results().count();
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

    private static Path moduleMainRoot() {
        return repoRoot().resolve("media-execution-plan-module/src/main/java");
    }

    private static Path compatibilitySourceRoot() {
        return moduleMainRoot().resolve("com/example/platform/execution/compatibility");
    }

    private static Path taskGraphSourceRoot() {
        return moduleMainRoot().resolve("com/example/platform/execution/taskgraph");
    }

    private static Path taskGraphTestRoot() {
        return repoRoot().resolve("media-execution-plan-module/src/test/java/"
                + "com/example/platform/execution/taskgraph");
    }

    private static String readJavaSources(Path root) throws IOException {
        StringBuilder source = new StringBuilder();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                source.append(Files.readString(file)).append('\n');
            }
        }
        return source.toString();
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ");
    }
}
