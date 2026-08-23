package com.example.platform.execution.domain.provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProviderImmutableFoundationArchitectureTest {

    private static final Pattern MUTABLE_RUNTIME_IMPORT = Pattern.compile(
            "(?m)^import\\s+.*(?:workerfabric|\\.[A-Za-z0-9_]*Worker[A-Za-z0-9_]*|"
                    + "\\.[A-Za-z0-9_]*Device[A-Za-z0-9_]*|\\.ProviderProbe[A-Za-z0-9_]*|"
                    + "\\.Reservation[A-Za-z0-9_]*|\\.TaskLease[A-Za-z0-9_]*|"
                    + "\\.ExecutionAttempt[A-Za-z0-9_]*|\\.HostResourceSnapshot[A-Za-z0-9_]*)(?:[.;]|$)");

    @Test
    void immutableModuleImportsNoMutableRuntimeConcepts() throws IOException {
        String mainSource = readJavaSources(moduleMainRoot());
        long mutableRuntimeImports = MUTABLE_RUNTIME_IMPORT.matcher(mainSource).results().count();

        assertEquals(0, mutableRuntimeImports, "IMMUTABLE_MODULE_MUTABLE_RUNTIME_IMPORT_COUNT=0");
        assertFalse(Files.readString(repoRoot().resolve("media-execution-plan-module/build.gradle.kts"))
                        .contains("worker-fabric-module"),
                "immutable module must not depend on worker-fabric-module");
    }

    @Test
    void providerBindingPinHasExactlyOneProductionDefinition() throws IOException {
        String code = stripComments(readJavaSources(moduleMainRoot()));
        Pattern definition = Pattern.compile(
                "(?m)^(?:public\\s+)?(?:final\\s+)?(?:record|class|interface|sealed\\s+interface)"
                        + "\\s+ProviderBindingPin\\b");

        assertEquals(1, definition.matcher(code).results().count(),
                "PROVIDER_BINDING_PIN_DEFINITION_COUNT=1");
    }

    @Test
    void providerFoundationDoesNotCreateStringlyCapabilityAuthority() throws IOException {
        String providerCode = stripComments(readJavaSources(moduleMainRoot()
                .resolve("com/example/platform/execution/domain/provider")));

        assertFalse(providerCode.contains("Map<String, String>"));
        assertFalse(providerCode.contains("Map<String,String>"));
        assertFalse(providerCode.contains("System.currentTimeMillis"));
        assertFalse(providerCode.contains("Instant.now"));
        assertFalse(providerCode.contains("UUID.randomUUID"));
        assertFalse(providerCode.contains("Math.random"));
        assertFalse(providerCode.matches("(?s).*(?:record|class|interface)\\s+CapabilityId\\b.*"));
        assertFalse(providerCode.matches(
                "(?s).*(?:record|class|interface)\\s+CapabilityImplementationId\\b.*"));
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
