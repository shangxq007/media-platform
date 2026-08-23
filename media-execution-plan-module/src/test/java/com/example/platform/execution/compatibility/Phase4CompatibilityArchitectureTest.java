package com.example.platform.execution.compatibility;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityImplementationId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Phase4CompatibilityArchitectureTest {

    private static final Pattern MUTABLE_RUNTIME_IMPORT = Pattern.compile(
            "(?m)^import\\s+.*(?:workerfabric|worker_fabric|worker\\.fabric|"
                    + "\\.Reservation(?:[.;]|$)|\\.PhysicalHost[A-Za-z0-9_]*(?:[.;]|$)|"
                    + "\\.WorkerRuntime[A-Za-z0-9_]*(?:[.;]|$)|"
                    + "\\.DeviceRuntime[A-Za-z0-9_]*(?:[.;]|$)|"
                    + "\\.ProviderProbe[A-Za-z0-9_]*(?:[.;]|$)|"
                    + "\\.TaskLease[A-Za-z0-9_]*(?:[.;]|$)|"
                    + "\\.ExecutionAttempt[A-Za-z0-9_]*(?:[.;]|$)|"
                    + "\\.ExecutionBackend[A-Za-z0-9_]*(?:[.;]|$))");

    private static final Pattern GRAPH_RUNTIME_FIELD = Pattern.compile(
            "\\b(?:WorkerId|WorkerRuntimeId|PhysicalHostId|DeviceAssignment|ProviderProbe|"
                    + "Reservation|Capacity|Queue|Health|Telemetry|TaskLease|ExecutionAttempt|"
                    + "ExecutionBackend)\\b");

    @Test
    void compatibilityKernelImportsNoMutableRuntimeAuthority() throws IOException {
        String kernel = Files.readString(compatibilitySourceRoot().resolve("CompatibilityKernel.java"));
        long count = MUTABLE_RUNTIME_IMPORT.matcher(kernel).results().count();

        assertEquals(0, count, "COMPATIBILITY_KERNEL_MUTABLE_RUNTIME_IMPORT_COUNT=0");
        assertFalse(Files.readString(repoRoot().resolve("media-execution-plan-module/build.gradle.kts"))
                        .contains("worker-fabric-module"),
                "media-execution-plan-module must not depend on worker-fabric-module");
    }

    @Test
    void providerCompatibilityGraphContainsNoRuntimeFieldsOrOrderingEscapeHatches() throws IOException {
        String graph = stripComments(Files.readString(
                compatibilitySourceRoot().resolve("ProviderCompatibilityGraph.java")));

        assertEquals(0, GRAPH_RUNTIME_FIELD.matcher(graph).results().count(),
                "PROVIDER_COMPATIBILITY_GRAPH_RUNTIME_FIELD_COUNT=0");
        assertFalse(graph.contains("HashMap"), "graph must not derive semantics from HashMap traversal");
        assertFalse(graph.contains("System.currentTimeMillis"));
        assertFalse(graph.contains("Instant.now"));
        assertFalse(graph.contains("UUID.randomUUID"));
        assertFalse(graph.contains("Math.random"));
    }

    @Test
    void epochOneIdentityAndSingleBindingAuthoritiesRemainDistinct() throws IOException {
        assertFalse(ProviderId.class.equals(CapabilityId.class));
        assertFalse(ProviderImplementationId.class.equals(CapabilityImplementationId.class));

        String production = stripComments(readJavaSources(moduleMainRoot()));
        Pattern bindingDefinition = Pattern.compile(
                "(?m)^(?:public\\s+)?(?:final\\s+)?(?:record|class|interface|sealed\\s+interface)"
                        + "\\s+ProviderBindingPin\\b");
        assertEquals(1, bindingDefinition.matcher(production).results().count(),
                "PROVIDER_BINDING_PIN_DEFINITION_COUNT=1");
        assertEquals(0, Pattern.compile(
                        "(?m)^(?:public\\s+)?(?:final\\s+)?(?:record|class|interface)"
                                + "\\s+(?:CapabilityId|CapabilityImplementationId)\\b")
                        .matcher(production).results().count(),
                "COMPATIBILITY_CAPABILITY_IDENTITY_REDEFINITION_COUNT=0");
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
