package com.example.platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PMPR-ST1-CRR1 OpenDAL provider boundary + remaining storage contract guards.
 *
 * OPENDAL_IS_A_STORAGE_PROVIDER_NOT_A_STORAGE_AUTHORITY_V1:
 * - storage-module owns ALL canonical storage contracts (no legacy OpenDAL POC)
 * - storage-module has NO direct OpenDAL dependency
 * - storage-provider-opendal depends on storage, NEVER on render
 */
class OpenDalProviderBoundaryTest {

    private static Path repoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.isRegularFile(p.resolve("settings.gradle.kts"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("repo root not found (settings.gradle.kts)");
        }
        return p;
    }

    private static List<String> filesUnder(String module, String subPath) {
        Path dir = repoRoot().resolve(module).resolve(subPath);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .map(Path::toString)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String buildFile(String module) {
        return repoRoot().resolve(module).resolve("build.gradle.kts").toString();
    }

    @Test
    void odl1StorageModuleHasNoLegacyOpenDalPoc() {
        List<String> poc = filesUnder("storage-module", "src/main/java")
                .stream()
                .filter(p -> p.contains("experimental/opendal"))
                .toList();
        assertTrue(poc.isEmpty(), "ODL-1: legacy OpenDAL POC must be retired, found: " + poc);
    }

    @Test
    void odl2StorageModuleHasNoDirectOpendalDependency() {
        try {
            String build = Files.readString(Path.of(buildFile("storage-module")));
            assertFalse(build.contains("org.apache.opendal"),
                    "ODL-2: storage-module must not depend on opendal-java");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void odl3OpendalProviderDoesNotDependOnRender() {
        try {
            String build = Files.readString(Path.of(buildFile("storage-provider-opendal")));
            assertFalse(build.contains("project(\":render-module\")"),
                    "ODL-3: storage-provider-opendal must not depend on render-module");
            assertTrue(build.contains("project(\":storage-module\")"),
                    "ODL-3: storage-provider-opendal must depend on storage-module");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void authorityRenderOwnsNoCanonicalStorageContracts() {
        // Remaining storage contracts (namespace/error/provider/read/write/lease/...) must
        // all have left render authority — render/domain/storage must be empty.
        List<String> remaining = filesUnder("render-module", "src/main/java/com/example/platform/render/domain/storage");
        assertTrue(remaining.isEmpty(),
                "All canonical storage contracts must leave render authority, remaining: " + remaining);
    }
}
