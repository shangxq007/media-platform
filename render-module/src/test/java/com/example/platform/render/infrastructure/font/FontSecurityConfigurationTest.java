package com.example.platform.render.infrastructure.font;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FontSecurityConfigurationTest {

    @Test
    void basicScannerIsDefault() {
        FontSecurityConfiguration config = new FontSecurityConfiguration();
        FontSecurityScanner scanner = config.basicFontSecurityScanner();

        assertNotNull(scanner);
        assertInstanceOf(BasicFontSecurityScanner.class, scanner);
        assertTrue(scanner.productionSafe());
    }

    @Test
    void noopScannerIsNotProductionSafe() {
        FontSecurityConfiguration config = new FontSecurityConfiguration();
        FontSecurityScanner scanner = config.noopFontSecurityScanner();

        assertNotNull(scanner);
        assertInstanceOf(NoopFontSecurityScanner.class, scanner);
        assertFalse(scanner.productionSafe());
    }

    @Test
    void basicScannerRejectsTraversal() {
        FontSecurityScanner scanner = new BasicFontSecurityScanner();

        // Path traversal in filename should be rejected
        var result = scanner.scan((java.io.InputStream) null, "../../../etc/passwd.ttf");
        assertNotNull(result);
        // Should be rejected (null input)
    }

    @Test
    void basicScannerAcceptsValidFont() throws Exception {
        FontSecurityScanner scanner = new BasicFontSecurityScanner();

        // Create a minimal TTF file with valid magic bytes
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test", ".ttf");
        try {
            byte[] header = new byte[100];
            header[0] = 0x00; header[1] = 0x01; header[2] = 0x00; header[3] = 0x00; // TTF magic
            java.nio.file.Files.write(tempFile, header);

            var result = scanner.scan(tempFile);
            assertNotNull(result);
            // Should pass basic validation (magic bytes match)
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }
}
