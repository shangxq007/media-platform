package com.example.platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * PMPR-A1R1-GC1 greenfield canonicalization guards (PMPR-AR-A1R1-GC1-01..04).
 * RED on published parent ac3bf2ed (stale "module": "artifact-catalog-module" in active runtime),
 * GREEN after canonicalization to authority/capability.
 */
class ArtifactCanonicalizationTest {

    private Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("settings.gradle.kts"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("settings.gradle.kts not found");
        }
        return p;
    }

    private String read(String rel) {
        try {
            return Files.readString(repoRoot().resolve(rel));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean anyMainSourceContains(Path base, String needle) {
        if (!Files.exists(base)) {
            return false;
        }
        try (Stream<Path> s = Files.walk(base)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .anyMatch(p -> {
                        try {
                            return Files.readString(p).contains(needle);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PMPR-AR-A1R1-GC1-01: active production source must not expose "artifact-catalog-module" as runtime/module identity. */
    @Test
    void gc101NoRetiredModuleIdentityInActiveRuntime() {
        assertFalse(anyMainSourceContains(repoRoot().resolve("artifact-module/src/main"), "\"artifact-catalog-module\""),
                "artifact-module production source must not expose retired module identity in runtime metadata");
    }

    /** PMPR-AR-A1R1-GC1-02: single Artifact authority — catalog is Artifact-owned capability. */
    @Test
    void gc102SingleArtifactAuthority() {
        String pkgInfo = read("artifact-module/src/main/java/com/example/platform/artifact/package-info.java");
        assertFalse(pkgInfo.contains("@ApplicationModule(\"Artifact Catalog\")"),
                "Artifact Catalog must not be a peer authority");
    }

    /** PMPR-AR-A1R1-GC1-03: retired physical module stays retired. */
    @Test
    void gc103PhysicalModuleRetired() {
        assertFalse(Files.exists(repoRoot().resolve("artifact-catalog-module")),
                "artifact-catalog-module must not exist");
        assertFalse(read("settings.gradle.kts").contains("artifact-catalog-module"),
                "settings.gradle.kts must not reference artifact-catalog-module");
    }

    /** PMPR-AR-A1R1-GC1-04: no compatibility alias preserving retired module identity. */
    @Test
    void gc104NoCompatibilityAlias() {
        String service = read("artifact-module/src/main/java/com/example/platform/artifact/app/ArtifactCatalogService.java");
        assertFalse(service.contains("legacyModule") || service.contains("oldModuleName")
                        || service.contains("compatibilityModule") || service.contains("deprecatedModule"),
                "no compatibility alias fields for retired module identity");
    }
}
