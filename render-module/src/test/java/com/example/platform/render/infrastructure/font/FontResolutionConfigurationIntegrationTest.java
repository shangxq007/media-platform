package com.example.platform.render.infrastructure.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.resolution.TechnicalFontResolver;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.typography.FontFamilyName;
import com.example.platform.fonttext.typography.FontSelectionIntent;
import com.example.platform.fonttext.typography.OpticalSizingIntent;
import com.example.platform.operation.operation.TextOperationPlanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

            // The pre-existing resolver gate now resolves against the real font in the catalog.
            String family = input.catalog().entries().get(0).manifest().familyName();
            assertNotNull(family);
            FontSelectionIntent intent = new FontSelectionIntent(
                    List.of(new FontFamilyName(family)),
                    FontSelectionIntent.WeightIntent.NORMAL,
                    FontSelectionIntent.StretchIntent.NORMAL,
                    FontSelectionIntent.SlantIntent.NORMAL,
                    OpticalSizingIntent.disabled(),
                    List.of());
            FontFallbackPolicy fallback = new FontFallbackPolicy(
                    List.of(new FontFamilyName(family)), List.of(), List.of(), List.of());

            TechnicalFontResolver.Result result = input.resolver()
                    .resolve(new TextContent("Hello"), intent, fallback, input.catalog(), null);

            assertTrue(result.diagnostics().isEmpty(),
                    "resolver gate should pass with a populated catalog, got " + result.diagnostics());
            assertEquals(1, result.runs().size());
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
