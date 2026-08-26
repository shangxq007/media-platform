package com.example.platform.render.infrastructure.font;

import com.example.platform.sandbox.LocalSandboxProcess;
import com.example.platform.sandbox.SandboxCancellation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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
            Path workspace = fontFile.toAbsolutePath().normalize().getParent();
            var process = LocalSandboxProcess.execute(
                    List.of("python3", fontToolsScript.toString(), fontFile.toString()),
                    workspace, workspace,
                    Set.of(fontToolsScript.toAbsolutePath().normalize(), fontFile.toAbsolutePath().normalize()),
                    Map.of("PATH", "/usr/bin:/bin", "LANG", "C"),
                    java.time.Duration.ofSeconds(30), 1L << 20, SandboxCancellation.never());
            if (process.failure().isPresent()) {
                log.error("FontTools extraction failed: {}", process.failure().orElseThrow());
                return emptyMetadata(fontFile);
            }
            return parseMetadata(process.stdout().utf8(), fontFile);
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
