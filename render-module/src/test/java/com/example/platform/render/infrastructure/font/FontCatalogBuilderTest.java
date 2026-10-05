package com.example.platform.render.infrastructure.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.example.platform.fontpipeline.DefaultFontPipelineOrchestrator;
import com.example.platform.fontpipeline.FontPipelineOrchestrator;
import com.example.platform.fontpipeline.manifest.DefaultFontBoxManifestAdapter;
import com.example.platform.fontpipeline.manifest.DefaultFontFaceManifestAssembler;
import com.example.platform.fontpipeline.manifest.DefaultLwjglFreeTypeManifestAdapter;
import com.example.platform.fontpipeline.sanitize.OtsFontSanitizer;
import com.example.platform.fonttext.resolution.ValidatedFontCatalogSnapshot;
import com.example.platform.fonttext.security.FontSecurityState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link FontCatalogBuilder}. The directory-level cases need no external tools; the
 * font-processing cases drive the real Phase 6a pipeline (real {@code /usr/bin/ots-sanitize} inside
 * the bubblewrap sandbox) and are skipped when OTS or the system sample is absent.
 */
class FontCatalogBuilderTest {

    private static final Path OTS = Path.of("/usr/bin/ots-sanitize");
    private static final Path SAMPLE = Path.of("/usr/share/fonts/truetype/Roboto-Light.ttf");

    @TempDir
    Path tempDir;

    @Test
    void emptyDirectoryYieldsEmptyCatalog() {
        ValidatedFontCatalogSnapshot snapshot = builderFor(tempDir).build();

        assertNotNull(snapshot);
        assertTrue(snapshot.entries().isEmpty());
    }

    @Test
    void missingDirectoryYieldsEmptyCatalogWithoutException() {
        Path missing = tempDir.resolve("does-not-exist");

        ValidatedFontCatalogSnapshot snapshot = builderFor(missing).build();

        assertNotNull(snapshot);
        assertTrue(snapshot.entries().isEmpty());
    }

    @Test
    void nonFontFilesAreIgnored() throws IOException {
        Files.write(tempDir.resolve("readme.txt"), "not a font".getBytes(StandardCharsets.UTF_8));

        assertTrue(builderFor(tempDir).build().entries().isEmpty());
    }

    @Test
    void validTtfYieldsOneCatalogEntry() throws IOException {
        requireOtsAndSample();
        Files.copy(SAMPLE, tempDir.resolve("sample.ttf"));

        ValidatedFontCatalogSnapshot snapshot = builderFor(tempDir).build();

        assertEquals(1, snapshot.entries().size());
        ValidatedFontCatalogSnapshot.Entry entry = snapshot.entries().get(0);
        assertNotNull(entry.reference());
        assertNotNull(entry.manifest());
        assertEquals(FontSecurityState.SANITIZED, entry.reference().securityState());
        assertEquals(entry.manifest().contentDigest(), entry.reference().validatedExecutionContentDigest());
    }

    @Test
    void malformedFontIsSkippedWithoutException() throws IOException {
        requireOtsAndSample();
        Files.write(tempDir.resolve("broken.ttf"), Arrays.copyOf(Files.readAllBytes(SAMPLE), 2000));

        ValidatedFontCatalogSnapshot snapshot = builderFor(tempDir).build();

        assertNotNull(snapshot);
        assertTrue(snapshot.entries().isEmpty());
    }

    @Test
    void malformedFontIsSkippedWhileValidFontIsKept() throws IOException {
        requireOtsAndSample();
        Files.copy(SAMPLE, tempDir.resolve("good.ttf"));
        Files.write(tempDir.resolve("broken.ttf"), Arrays.copyOf(Files.readAllBytes(SAMPLE), 2000));

        ValidatedFontCatalogSnapshot snapshot = builderFor(tempDir).build();

        assertEquals(1, snapshot.entries().size());
    }

    private static void requireOtsAndSample() {
        assumeTrue(Files.isExecutable(OTS), "ots-sanitize not available at " + OTS);
        assumeTrue(Files.isRegularFile(SAMPLE), "font sample not available at " + SAMPLE);
    }

    private static FontCatalogBuilder builderFor(Path fontsDir) {
        FontPipelineOrchestrator orchestrator = new DefaultFontPipelineOrchestrator(
                new OtsFontSanitizer(),
                new DefaultFontBoxManifestAdapter(),
                new DefaultLwjglFreeTypeManifestAdapter(),
                new DefaultFontFaceManifestAssembler());
        return new FontCatalogBuilder(orchestrator, fontsDir.toString());
    }
}
