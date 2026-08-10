package com.example.platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * PMPR-A1R1 architecture guards (PMPR-AR-A1R1-01..04).
 * RED on published parent e19308e9, GREEN after artifact-catalog -> artifact convergence.
 */
class ArtifactAuthorityTest {

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

    private List<String> filesUnder(String module, String dir) {
        Path base = repoRoot().resolve(module).resolve(dir);
        if (!Files.exists(base)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(base)) {
            return s.filter(Files::isRegularFile)
                    .map(p -> p.toString().replace('\\', '/'))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PMPR-AR-A1R1-01: artifact-catalog physical Gradle module absent. */
    @Test
    void a1r101ArtifactCatalogPhysicalModuleAbsent() {
        Path settings = repoRoot().resolve("settings.gradle.kts");
        String content;
        try {
            content = Files.readString(settings);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertFalse(content.contains("\"artifact-catalog-module\""),
                "artifact-catalog-module must be retired from settings.gradle.kts");
        assertFalse(Files.exists(repoRoot().resolve("artifact-catalog-module")),
                "artifact-catalog-module directory must not exist");
    }

    /** PMPR-AR-A1R1-02: single Artifact authority — catalog must not be an independent Modulith authority. */
    @Test
    void a1r102SingleArtifactAuthority() {
        Path catalogPkgInfo = repoRoot().resolve("artifact-module/src/main/java/com/example/platform/artifact/package-info.java");
        String content;
        try {
            content = Files.readString(catalogPkgInfo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // artifact-module root package-info must NOT declare a separate "Artifact Catalog" application module
        assertFalse(content.contains("@ApplicationModule(\"Artifact Catalog\")"),
                "Artifact Catalog must not remain an independent Modulith authority");
    }

    /** PMPR-AR-A1R1-03: artifact/catalog storage imports resolve through storage authority, never render. */
    @Test
    void a1r103NoArtifactStorageThroughRender() {
        List<String> files = new java.util.ArrayList<>(filesUnder("artifact-module", "src/main"));
        files.addAll(filesUnder("artifact-catalog-module", "src/main"));
        for (String f : files) {
            try {
                String content = Files.readString(Path.of(f));
                assertFalse(content.contains("com.example.platform.render.domain.storage"),
                        "artifact/catalog must not import render-owned Storage semantics: " + f);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** PMPR-AR-A1R1-04: no new dependencies into PMPR retirement candidates. */
    @Test
    void a1r104NoNewRetirementCandidateDependencies() {
        List<String> candidates = List.of("quota-billing-module", "product-layer-module",
                "compatibility-migration-module", "sandbox-runtime-module");
        Path build = repoRoot().resolve("artifact-module/build.gradle.kts");
        try {
            String content = Files.readString(build);
            for (String c : candidates) {
                assertFalse(content.contains("\"" + c + "\""),
                        "artifact-module must not depend on retirement candidate " + c);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
