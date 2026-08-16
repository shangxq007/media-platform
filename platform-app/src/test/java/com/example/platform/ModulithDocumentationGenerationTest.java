package com.example.platform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * AMRA-V1 — Spring Modulith AS-BUILT structure documentation generation.
 *
 * <p>Outputs PlantUML diagrams derived from {@link ApplicationModules} (the same
 * source that {@code ModularityTest} verifies) into
 * {@code docs/architecture/maps/generated/modulith/}.</p>
 *
 * <p>Role: AS-BUILT / DERIVED / NON-HUMAN-EDITED structural view. It is NOT a
 * business-architecture authority (that is LikeC4). Do NOT hand-edit generated
 * output — regenerate by running this test.</p>
 *
 * <p>Deterministic: no DB, no Docker, no network, no application startup side
 * effects; pure {@code ApplicationModules.of(PlatformApplication.class)} model
 * introspection. The Spring Modulith Documenter emits relationship lines in
 * iteration-dependent order, so the generator additionally canonicalizes every
 * contiguous {@code Rel(...)} block lexicographically before writing. Generation
 * from the same repository state is therefore byte-identical across runs
 * (GCR-1 Phase B: ARCHITECTURE_MAP_GENERATOR_DETERMINISM).</p>
 */
class ModulithDocumentationGenerationTest {

    @Test
    void generatesAsBuiltPlantUmlDiagrams() throws Exception {
        ApplicationModules modules = ApplicationModules.of(PlatformApplication.class);

        // Assert the model is inspectable (as-built inventory source of truth).
        org.junit.jupiter.api.Assertions.assertTrue(
                modules.stream().findAny().isPresent(),
                "ApplicationModules must contain at least one application module");

        // Generated output is a documented, diffable, GENERATED artifact.
        Path output = Path.of("docs/architecture/maps/generated/modulith");
        Files.createDirectories(output);

        // withOutputFolder is relative to the module working directory (platform-app/),
        // so the canonical docs path is resolved from the module root.
        Documenter.Options options = Documenter.Options.defaults()
                .withOutputFolder("docs/architecture/maps/generated/modulith");

        new Documenter(modules, options)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();

        // Canonicalize: sort each contiguous Rel(...) block deterministically.
        canonicalizePlantUmlRelationOrdering(output);

        // Verify output landed and is non-empty.
        try (Stream<Path> files = Files.list(output)) {
            long count = files.count();
            org.junit.jupiter.api.Assertions.assertTrue(count > 0,
                    "Documenter must produce non-empty output under docs/architecture/maps/generated/modulith/, found " + count);
        }
    }

    /**
     * The Spring Modulith Documenter emits {@code Rel(...)} lines in
     * iteration-dependent order (observed: semantically identical relation sets,
     * unstable line ordering across clean generations). Sorting each contiguous
     * block lexicographically makes generation byte-deterministic without
     * changing any relation semantics.
     */
    private static void canonicalizePlantUmlRelationOrdering(Path outputDir) throws IOException {
        try (Stream<Path> files = Files.walk(outputDir)) {
            for (Path p : files.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(".puml")).toList()) {
                List<String> lines = Files.readAllLines(p);
                List<String> out = new ArrayList<>(lines.size());
                List<String> relBlock = new ArrayList<>();
                for (String line : lines) {
                    if (line.startsWith("Rel(")) {
                        relBlock.add(line);
                    } else {
                        flushRelBlock(relBlock, out);
                        out.add(line);
                    }
                }
                flushRelBlock(relBlock, out);
                Files.write(p, out);
            }
        }
    }

    private static void flushRelBlock(List<String> relBlock, List<String> out) {
        if (!relBlock.isEmpty()) {
            relBlock.sort(Comparator.naturalOrder());
            out.addAll(relBlock);
            relBlock.clear();
        }
    }
}
