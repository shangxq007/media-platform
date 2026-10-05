package com.example.platform.render.infrastructure.font;

import com.example.platform.fontpipeline.FontPipelineOrchestrator;
import com.example.platform.fontpipeline.FontProcessingRequest;
import com.example.platform.fontpipeline.FontProcessingResult;
import com.example.platform.fonttext.resolution.ValidatedFontCatalogSnapshot;
import com.example.platform.fonttext.resource.ValidatedFontExecutionReference;
import com.example.platform.fonttext.security.FontSecurityState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FONT-V1 Phase 6b: builds the startup {@link ValidatedFontCatalogSnapshot} from the font files in
 * {@code ${app.fonts.dir}} by running each file through the Phase 6a pipeline.
 *
 * <p>{@code ${app.fonts.dir}} defaults to {@code /tmp/platform/fonts} (the pre-existing convention).
 * The directory is scanned once at startup and never refreshed: there is no watcher, scheduler,
 * cache invalidation or runtime refresh in this class.
 *
 * <p><b>Failure policy.</b> A missing or empty directory yields an empty catalog and does not fail
 * startup. A single font that fails any pipeline stage is logged at WARN and skipped. If every font
 * fails, the result is an empty catalog and startup still continues.
 *
 * <p><b>Scan.</b> Only the top level of the directory is read (no recursion); files with the
 * {@code .ttf}, {@code .otf} or {@code .ttc} extension are processed. V1 processes TTC face 0 only —
 * multi-face expansion is future work.
 */
@Component
public class FontCatalogBuilder {

    private static final Logger log = LoggerFactory.getLogger(FontCatalogBuilder.class);

    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".ttf", ".otf", ".ttc");

    private final FontPipelineOrchestrator orchestrator;
    private final String fontsDir;

    public FontCatalogBuilder(
            FontPipelineOrchestrator orchestrator,
            @Value("${app.fonts.dir:/tmp/platform/fonts}") String fontsDir) {
        this.orchestrator = orchestrator;
        this.fontsDir = fontsDir;
    }

    /**
     * Build the validated catalog once from {@code ${app.fonts.dir}}.
     *
     * @return a snapshot of the successfully processed font files (possibly empty)
     */
    public ValidatedFontCatalogSnapshot build() {
        Path directory = Path.of(fontsDir);
        if (!Files.isDirectory(directory)) {
            log.info("Font catalog: directory {} does not exist; using empty catalog", directory);
            return new ValidatedFontCatalogSnapshot(List.of());
        }

        List<Path> fontFiles = listFontFiles(directory);
        if (fontFiles.isEmpty()) {
            log.info("Font catalog: no .ttf/.otf/.ttc files in {}; using empty catalog", directory);
            return new ValidatedFontCatalogSnapshot(List.of());
        }

        List<ValidatedFontCatalogSnapshot.Entry> entries = new ArrayList<>();
        for (Path fontFile : fontFiles) {
            try {
                entries.add(process(fontFile));
            } catch (Exception e) {
                log.warn("Font catalog: skipping {}: {}", fontFile, e.toString());
            }
        }

        if (entries.isEmpty()) {
            log.warn("Font catalog: all {} font file(s) in {} failed; using empty catalog",
                    fontFiles.size(), directory);
            return new ValidatedFontCatalogSnapshot(List.of());
        }

        log.info("Font catalog: built {} entr{} from {} ({} skipped)",
                entries.size(), entries.size() == 1 ? "y" : "ies", directory,
                fontFiles.size() - entries.size());
        return new ValidatedFontCatalogSnapshot(entries);
    }

    private ValidatedFontCatalogSnapshot.Entry process(Path fontFile) throws IOException {
        byte[] rawBytes = Files.readAllBytes(fontFile);
        FontProcessingRequest request =
                FontProcessingRequest.ofSingleFace(rawBytes, mediaTypeFor(fontFile));
        FontProcessingResult result = orchestrator.process(request);
        ValidatedFontExecutionReference reference = new ValidatedFontExecutionReference(
                result.sourceDigest(),
                result.contentDigest(),
                FontSecurityState.SANITIZED,
                result.manifest().format(),
                result.manifest().faceIndex());
        return new ValidatedFontCatalogSnapshot.Entry(reference, result.manifest());
    }

    private List<Path> listFontFiles(Path directory) {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> hasSupportedExtension(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            log.warn("Font catalog: cannot list {}: {}", directory, e.toString());
            return List.of();
        }
    }

    private static boolean hasSupportedExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static String mediaTypeFor(Path fontFile) {
        String lower = fontFile.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".otf")) {
            return "font/otf";
        }
        if (lower.endsWith(".ttc")) {
            return "font/collection";
        }
        return "font/ttf";
    }
}
