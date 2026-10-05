package com.example.platform.render.infrastructure.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.example.platform.operation.operation.TextOperationPlanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * Wiring test: a focused Spring context ({@link FontPipelineConfiguration} + {@link FontCatalogBuilder}
 * + {@link FontResolutionConfiguration}) must produce a {@link TextOperationPlanner.FontResolutionInput}
 * whose catalog comes from the configured font directory.
 *
 * <p>This uses a minimal {@link AnnotationConfigApplicationContext} instead of the full
 * {@code PlatformApplication} context, which would drag in the whole application (datasource,
 * migrations, providers). No application configuration file is modified.
 */
class FontResolutionConfigurationIntegrationTest {

    private static final Path OTS = Path.of("/usr/bin/ots-sanitize");
    private static final Path SAMPLE = Path.of("/usr/share/fonts/truetype/Roboto-Light.ttf");

    @TempDir
    Path tempDir;

    @Test
    void emptyFontDirectoryStillProvidesResolutionInputWithEmptyCatalog() {
        try (AnnotationConfigApplicationContext context = contextFor(tempDir)) {
            TextOperationPlanner.FontResolutionInput input =
                    context.getBean(TextOperationPlanner.FontResolutionInput.class);

            assertNotNull(input);
            assertNotNull(input.resolver());
            assertNotNull(input.catalog());
            assertTrue(input.catalog().entries().isEmpty());
        }
    }

    @Test
    void realFontDirectoryProvidesNonEmptyCatalog() throws IOException {
        assumeTrue(Files.isExecutable(OTS), "ots-sanitize not available at " + OTS);
        assumeTrue(Files.isRegularFile(SAMPLE), "font sample not available at " + SAMPLE);
        Files.copy(SAMPLE, tempDir.resolve("sample.ttf"));

        try (AnnotationConfigApplicationContext context = contextFor(tempDir)) {
            TextOperationPlanner.FontResolutionInput input =
                    context.getBean(TextOperationPlanner.FontResolutionInput.class);

            assertNotNull(input.catalog());
            assertEquals(1, input.catalog().entries().size());
        }
    }

    private static AnnotationConfigApplicationContext contextFor(Path fontsDir) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test-fonts", Map.of("app.fonts.dir", fontsDir.toString())));
        context.register(FontPipelineConfiguration.class, FontCatalogBuilder.class,
                FontResolutionConfiguration.class);
        context.refresh();
        return context;
    }
}
