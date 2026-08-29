package com.example.platform.execution.planning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase20ResourceRequirementAuthorityTest {

    @Test
    void planningRequirementIsTheSingleSourceAuthority() throws IOException {
        Path mainJava = repoRoot().resolve("media-execution-plan-module/src/main/java");
        Path shadow = mainJava.resolve(
                "com/example/platform/execution/domain/ExecutionResourceRequirement.java");
        Path canonical = mainJava.resolve(
                "com/example/platform/execution/planning/ExecutionRequirement.java");

        assertFalse(Files.exists(shadow),
                "ExecutionResourceRequirement shadow must remain clean-forward deleted");
        try (var sources = Files.walk(mainJava)) {
            for (Path source : sources.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java")).toList()) {
                assertFalse(Files.readString(source).contains("ExecutionResourceRequirement"),
                        "no compatibility wrapper or alias may restore the shadow authority: " + source);
            }
        }
        assertTrue(Files.isRegularFile(canonical),
                "canonical planning ExecutionRequirement source must remain present");
        assertTrue(Files.readString(canonical).contains("public record ExecutionRequirement("),
                "canonical planning ExecutionRequirement must remain an immutable record");
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
}
