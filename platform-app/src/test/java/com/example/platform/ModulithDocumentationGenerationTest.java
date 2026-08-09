package com.example.platform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import java.nio.file.Files;
import java.nio.file.Path;
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
 * introspection.</p>
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

        // Verify output landed and is non-empty.
        try (Stream<Path> files = Files.list(output)) {
            long count = files.count();
            org.junit.jupiter.api.Assertions.assertTrue(count > 0,
                    "Documenter must produce non-empty output under docs/architecture/maps/generated/modulith/, found " + count);
        }
    }
}
