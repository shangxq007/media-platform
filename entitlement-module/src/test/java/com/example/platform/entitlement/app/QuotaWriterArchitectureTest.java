package com.example.platform.entitlement.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class QuotaWriterArchitectureTest {

    private static final Path REPOSITORY_ROOT = repositoryRoot();

    @Test
    void exactlyOneProductionClassContainsQuotaUsageMutationSql() throws IOException {
        List<Path> writers;
        try (Stream<Path> files = Files.walk(REPOSITORY_ROOT)) {
            writers = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/"))
                    .filter(path -> {
                        String source = read(path).toLowerCase();
                        return source.contains("insert into quota_usage")
                                || source.contains("update quota_usage")
                                || source.contains("insertinto(quota_usage)")
                                || source.contains("update(quota_usage)");
                    })
                    .toList();
        }

        assertEquals(List.of(REPOSITORY_ROOT.resolve(
                        "entitlement-module/src/main/java/com/example/platform/entitlement/infrastructure/QuotaUsageJdbcRepository.java")),
                writers);
    }

    @Test
    void entitlementQuotaPathHasNoInMemoryUsageAuthority() {
        String usageFacade = read(REPOSITORY_ROOT.resolve(
                "entitlement-module/src/main/java/com/example/platform/entitlement/app/QuotaUsageService.java"));
        assertFalse(usageFacade.contains("ConcurrentHashMap"));
        assertFalse(usageFacade.contains("getUsage(subjectId, featureCode) + delta"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("settings.gradle.kts"))) {
            return parent;
        }
        throw new IllegalStateException("Could not locate repository root from " + current);
    }
}
