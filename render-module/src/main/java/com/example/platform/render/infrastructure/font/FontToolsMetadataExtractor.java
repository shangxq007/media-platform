package com.example.platform.render.infrastructure.font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Feature-flagged FontTools metadata extractor.
 *
 * Enabled via: render.font.tools.enabled=true
 *
 * Requires fontTools Python package:
 *   pip install fonttools
 *
 * Future implementation will use:
 *   TTFont(font_path) to read:
 *     - name table (family, subfamily, postScriptName)
 *     - cmap table (character map)
 *     - OS/2 table (weight, style)
 *     - head table (unitsPerEm, bounding box)
 *     - post table (PostScript info)
 *     - GSUB/GPOS tables (OpenType features)
 *     - fvar table (variable font axes)
 */
public class FontToolsMetadataExtractor implements FontMetadataExtractor {
    private static final Logger log = LoggerFactory.getLogger(FontToolsMetadataExtractor.class);

    private boolean enabled = false;
    private Path fontToolsScript = Path.of("scripts/extract_font_metadata.py");

    public FontToolsMetadataExtractor enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public FontToolsMetadataExtractor fontToolsScript(Path script) {
        this.fontToolsScript = script;
        return this;
    }

    @Override
    public String extractorName() {
        return "FontToolsMetadataExtractor";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public FontMetadata extract(Path fontFile) {
        if (!enabled) {
            log.warn("FontToolsMetadataExtractor is disabled. Enable via render.font.tools.enabled=true");
            return emptyMetadata(fontFile);
        }

        if (!Files.exists(fontToolsScript)) {
            log.error("FontTools script not found: {}", fontToolsScript);
            return emptyMetadata(fontFile);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("python3", fontToolsScript.toString(), fontFile.toString());
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String output = new String(process.getInputStream().readAllBytes());
            String errors = new String(process.getErrorStream().readAllBytes());

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("FontTools extraction timed out for: {}", fontFile);
                return emptyMetadata(fontFile);
            }

            if (process.exitValue() != 0) {
                log.error("FontTools extraction failed: {}", errors);
                return emptyMetadata(fontFile);
            }

            return parseMetadata(output, fontFile);
        } catch (Exception e) {
            log.error("FontTools extraction error for {}: {}", fontFile, e.getMessage());
            return emptyMetadata(fontFile);
        }
    }

    @Override
    public FontMetadata extract(InputStream fontData, String fileName) {
        try {
            Path tempFile = Files.createTempFile("font-tools-extract-", ".ttf");
            try {
                Files.copy(fontData, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return extract(tempFile);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (Exception e) {
            log.error("FontTools extraction error for stream {}: {}", fileName, e.getMessage());
            return emptyMetadata(Path.of(fileName));
        }
    }

    private FontMetadata parseMetadata(String jsonOutput, Path fontFile) {
        log.debug("FontTools output: {}", jsonOutput);
        return emptyMetadata(fontFile);
    }

    private FontMetadata emptyMetadata(Path fontFile) {
        String fileName = fontFile.getFileName().toString();
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        return new FontMetadata(
                baseName, null, null, null, null,
                Files.exists(fontFile) ? fileName : null, 0, null,
                false, false, false, false, false, false, false, false,
                Set.of(), Map.of(), Map.of()
        );
    }
}
