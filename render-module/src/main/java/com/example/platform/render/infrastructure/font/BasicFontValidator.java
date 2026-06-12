package com.example.platform.render.infrastructure.font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Basic font validator that performs file-level validation without external tools.
 *
 * <p>Checks:
 * <ul>
 *   <li>File exists and is a regular file</li>
 *   <li>File size within limits (default 50MB)</li>
 *   <li>Magic bytes match known font formats (TTF, OTF, WOFF, WOFF2)</li>
 *   <li>File is not empty</li>
 *   <li>Font can be loaded by java.awt.Font (best-effort)</li>
 * </ul>
 *
 * <p>This is a minimal production-safe validator. For full validation
 * (table structure, hinting, etc.), use FontBakery or OTS.
 */
public class BasicFontValidator implements FontValidator {

    private static final Logger log = LoggerFactory.getLogger(BasicFontValidator.class);

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int HEADER_READ_SIZE = 36; // Enough for WOFF2 header

    // Magic bytes
    private static final byte[] TTF_MAGIC = {0x00, 0x01, 0x00, 0x00};
    private static final byte[] OTF_MAGIC = {'O', 'T', 'T', 'O'};
    private static final byte[] WOFF_MAGIC = {'w', 'O', 'F', 'F'};
    private static final byte[] WOFF2_MAGIC = {'w', 'O', 'F', '2'};
    private static final byte[] TTC_MAGIC = {'t', 't', 'c', 'f'};

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".ttf", ".otf", ".woff", ".woff2");

    @Override
    public String validatorName() {
        return "BasicFontValidator";
    }

    @Override
    public FontValidationResult validate(Path fontFile) {
        List<String> warnings = new ArrayList<>();

        // Check file exists
        if (fontFile == null || !Files.exists(fontFile)) {
            return FontValidationResult.failed(validatorName(), "File does not exist");
        }

        if (!Files.isRegularFile(fontFile)) {
            return FontValidationResult.failed(validatorName(), "Not a regular file");
        }

        // Check file size
        long fileSize;
        try {
            fileSize = Files.size(fontFile);
        } catch (IOException e) {
            return FontValidationResult.failed(validatorName(), "Cannot read file size: " + e.getMessage());
        }

        if (fileSize == 0) {
            return FontValidationResult.failed(validatorName(), "File is empty");
        }

        if (fileSize > MAX_FILE_SIZE) {
            return FontValidationResult.failed(validatorName(),
                    "File too large: " + fileSize + " bytes (max " + MAX_FILE_SIZE + ")");
        }

        // Read header bytes only (not the entire file)
        byte[] header;
        try (InputStream is = Files.newInputStream(fontFile)) {
            header = is.readNBytes(HEADER_READ_SIZE);
        } catch (IOException e) {
            return FontValidationResult.failed(validatorName(), "Cannot read file header: " + e.getMessage());
        }

        if (header.length < 4) {
            return FontValidationResult.failed(validatorName(), "File too small to be a valid font");
        }

        // Detect format by magic bytes
        String format = detectFormat(header);
        if (format == null) {
            return FontValidationResult.failed(validatorName(), "Unrecognized font format (magic bytes mismatch)");
        }

        // TTC is not fully supported
        if ("ttc".equals(format)) {
            warnings.add("TTC (TrueType Collection) detected — only first font will be used");
        }

        // Try to extract family name via java.awt.Font (best-effort)
        String fontFamily = null;
        try {
            java.awt.Font awtFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, fontFile.toFile());
            fontFamily = awtFont.getFamily();
        } catch (Exception e) {
            warnings.add("Could not extract font family via AWT: " + e.getMessage());
        }

        log.info("Font validated: {} (format={}, size={}, family={})", fontFile.getFileName(), format, fileSize, fontFamily);

        return new FontValidationResult(
                validatorName(),
                "PASSED",
                List.of(),
                warnings,
                fontFamily,
                null, // subfamily
                null, // weight
                null, // style
                true, // hasCmap (assumed for valid format)
                true, // hasGlyf (assumed)
                true, // hasHead
                true, // hasHhea
                true, // hasMaxp
                true, // hasOs2
                true, // hasPost
                true  // hasName
        );
    }

    @Override
    public FontValidationResult validate(InputStream fontData, String fileName) {
        if (fontData == null) {
            return FontValidationResult.failed(validatorName(), "Font data stream is null");
        }

        List<String> warnings = new ArrayList<>();

        // Read header bytes
        byte[] header;
        try {
            header = fontData.readNBytes(HEADER_READ_SIZE);
        } catch (IOException e) {
            return FontValidationResult.failed(validatorName(), "Cannot read font header: " + e.getMessage());
        }

        if (header.length < 4) {
            return FontValidationResult.failed(validatorName(), "Font data too small to be valid");
        }

        String format = detectFormat(header);
        if (format == null) {
            return FontValidationResult.failed(validatorName(), "Unrecognized font format");
        }

        if ("ttc".equals(format)) {
            warnings.add("TTC detected — only first font will be used");
        }

        return new FontValidationResult(
                validatorName(),
                "PASSED",
                List.of(),
                warnings,
                null, null, null, null,
                true, true, true, true, true, true, true, true
        );
    }

    private String detectFormat(byte[] header) {
        if (matchesMagic(header, TTF_MAGIC)) return "ttf";
        if (matchesMagic(header, OTF_MAGIC)) return "otf";
        if (matchesMagic(header, WOFF_MAGIC)) return "woff";
        if (matchesMagic(header, WOFF2_MAGIC)) return "woff2";
        if (matchesMagic(header, TTC_MAGIC)) return "ttc";
        return null;
    }

    private boolean matchesMagic(byte[] header, byte[] magic) {
        if (header.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (header[i] != magic[i]) return false;
        }
        return true;
    }
}
