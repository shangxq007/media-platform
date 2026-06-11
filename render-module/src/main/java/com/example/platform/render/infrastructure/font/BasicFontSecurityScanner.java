package com.example.platform.render.infrastructure.font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BasicFontSecurityScanner implements FontSecurityScanner {
    private static final Logger log = LoggerFactory.getLogger(BasicFontSecurityScanner.class);

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".ttf", ".otf", ".woff2");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "font/ttf", "font/otf", "font/woff2",
            "application/font-sfnt", "application/x-font-ttf", "application/x-font-otf",
            "application/octet-stream"
    );
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".exe", ".dll", ".so", ".dylib", ".zip", ".tar", ".gz", ".rar",
            ".svg", ".eot", ".woff", ".ttc", ".otc", ".dfont"
    );

    private static final byte[] TTF_MAGIC = {0x00, 0x01, 0x00, 0x00};
    private static final byte[] OTF_MAGIC = {'O', 'T', 'T', 'O'};
    private static final byte[] WOFF2_MAGIC = {'w', 'O', 'F', 'F'};

    @Override
    public String scannerName() {
        return "BasicFontSecurityScanner";
    }

    @Override
    public boolean productionSafe() {
        return true;
    }

    @Override
    public FontSecurityResult scan(Path fontFile) {
        List<String> warnings = new ArrayList<>();

        if (fontFile == null || !Files.exists(fontFile)) {
            return FontSecurityResult.rejected(scannerName(), List.of("File does not exist"));
        }

        String fileName = fontFile.getFileName().toString().toLowerCase();

        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return FontSecurityResult.rejected(scannerName(), List.of("Path traversal detected"));
        }

        for (String blocked : BLOCKED_EXTENSIONS) {
            if (fileName.endsWith(blocked)) {
                return FontSecurityResult.rejected(scannerName(),
                        List.of("Blocked file extension: " + blocked));
            }
        }

        String extension = getExtension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return FontSecurityResult.rejected(scannerName(),
                    List.of("Extension not whitelisted: " + extension));
        }

        long fileSize;
        try {
            fileSize = Files.size(fontFile);
        } catch (Exception e) {
            return FontSecurityResult.rejected(scannerName(), List.of("Cannot read file size: " + e.getMessage()));
        }

        if (fileSize > MAX_FILE_SIZE) {
            return FontSecurityResult.rejected(scannerName(),
                    List.of("File too large: " + fileSize + " bytes (max " + MAX_FILE_SIZE + ")"));
        }

        if (fileSize == 0) {
            return FontSecurityResult.rejected(scannerName(), List.of("File is empty"));
        }

        String sha256;
        try {
            sha256 = computeSha256(fontFile);
        } catch (Exception e) {
            return FontSecurityResult.rejected(scannerName(), List.of("Cannot compute SHA256: " + e.getMessage()));
        }

        boolean magicBytesValid = false;
        String mimeType = "application/octet-stream";
        try {
            byte[] header = Files.readAllBytes(fontFile);
            if (header.length >= 4) {
                if (matchesMagic(header, TTF_MAGIC)) {
                    magicBytesValid = extension.equals(".ttf");
                    mimeType = "font/ttf";
                } else if (matchesMagic(header, OTF_MAGIC)) {
                    magicBytesValid = extension.equals(".otf");
                    mimeType = "font/otf";
                } else if (matchesMagic(header, WOFF2_MAGIC)) {
                    magicBytesValid = extension.equals(".woff2");
                    mimeType = "font/woff2";
                } else {
                    warnings.add("Magic bytes do not match expected font format");
                }
            }
        } catch (Exception e) {
            warnings.add("Cannot read magic bytes: " + e.getMessage());
        }

        if (!magicBytesValid) {
            warnings.add("Magic bytes validation failed for " + extension);
        }

        log.info("Font security scan passed: {} (sha256={}, size={})", fileName, sha256, fileSize);
        return FontSecurityResult.passed(scannerName(), sha256, mimeType);
    }

    @Override
    public FontSecurityResult scan(InputStream fontData, String fileName) {
        try {
            Path tempFile = Files.createTempFile("font-security-scan-", getExtension(fileName));
            try {
                Files.copy(fontData, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return scan(tempFile);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (Exception e) {
            return FontSecurityResult.rejected(scannerName(), List.of("Cannot process input stream: " + e.getMessage()));
        }
    }

    private String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0) return "";
        return fileName.substring(lastDot).toLowerCase();
    }

    private boolean matchesMagic(byte[] header, byte[] magic) {
        for (int i = 0; i < magic.length; i++) {
            if (header[i] != magic[i]) return false;
        }
        return true;
    }

    private String computeSha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(file));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
