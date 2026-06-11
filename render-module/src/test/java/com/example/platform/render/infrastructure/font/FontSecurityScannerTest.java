package com.example.platform.render.infrastructure.font;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FontSecurityScannerTest {

    private final BasicFontSecurityScanner basicScanner = new BasicFontSecurityScanner();
    private final NoopFontSecurityScanner noopScanner = new NoopFontSecurityScanner();

    @Test
    void basicScannerIsProductionSafe() {
        assertTrue(basicScanner.productionSafe());
    }

    @Test
    void noopScannerIsNotProductionSafe() {
        assertFalse(noopScanner.productionSafe());
    }

    @Test
    void basicScannerAcceptsTtf() throws Exception {
        Path tempFile = createTempFileWithContent(".ttf", new byte[]{0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
        try {
            FontSecurityResult result = basicScanner.scan(tempFile);
            assertEquals("PASSED", result.scanStatus());
            assertTrue(result.extensionWhitelisted());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void basicScannerAcceptsOtf() throws Exception {
        Path tempFile = createTempFileWithContent(".otf", new byte[]{'O', 'T', 'T', 'O', 0x00, 0x00, 0x00, 0x00});
        try {
            FontSecurityResult result = basicScanner.scan(tempFile);
            assertEquals("PASSED", result.scanStatus());
            assertTrue(result.extensionWhitelisted());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void basicScannerAcceptsWoff2() throws Exception {
        Path tempFile = createTempFileWithContent(".woff2", new byte[]{'w', 'O', 'F', 'F', 0x00, 0x00, 0x00, 0x00});
        try {
            FontSecurityResult result = basicScanner.scan(tempFile);
            assertEquals("PASSED", result.scanStatus());
            assertTrue(result.extensionWhitelisted());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void basicScannerRejectsExe() throws Exception {
        Path tempFile = createTempFileWithContent(".exe", new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
        try {
            FontSecurityResult result = basicScanner.scan(tempFile);
            assertEquals("REJECTED", result.scanStatus());
            assertFalse(result.extensionWhitelisted());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void basicScannerRejectsZip() throws Exception {
        Path tempFile = createTempFileWithContent(".zip", new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00});
        try {
            FontSecurityResult result = basicScanner.scan(tempFile);
            assertEquals("REJECTED", result.scanStatus());
            assertFalse(result.extensionWhitelisted());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void basicScannerRejectsSvg() throws Exception {
        Path tempFile = createTempFileWithContent(".svg", new byte[]{'<', 's', 'v', 'g', 0x00, 0x00, 0x00, 0x00});
        try {
            FontSecurityResult result = basicScanner.scan(tempFile);
            assertEquals("REJECTED", result.scanStatus());
            assertFalse(result.extensionWhitelisted());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void basicScannerRejectsEot() throws Exception {
        Path tempFile = createTempFileWithContent(".eot", new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
        try {
            FontSecurityResult result = basicScanner.scan(tempFile);
            assertEquals("REJECTED", result.scanStatus());
            assertFalse(result.extensionWhitelisted());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void basicScannerRejectsLargeFile() throws Exception {
        Path tempFile = createTempFileWithContent(".ttf", new byte[]{0x00, 0x01, 0x00, 0x00});
        Path largeFile = Files.createTempFile("large-font-",
                ".ttf");
        try {
            byte[] largeContent = new byte[51 * 1024 * 1024];
            largeContent[0] = 0x00;
            largeContent[1] = 0x01;
            largeContent[2] = 0x00;
            largeContent[3] = 0x00;
            Files.write(largeFile, largeContent);
            FontSecurityResult result = basicScanner.scan(largeFile);
            assertEquals("REJECTED", result.scanStatus());
            assertTrue(result.warnings().stream().anyMatch(w -> w.contains("too large")));
        } finally {
            Files.deleteIfExists(largeFile);
        }
    }

    @Test
    void basicScannerComputesSha256() throws Exception {
        Path tempFile = createTempFileWithContent(".ttf", new byte[]{0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
        try {
            FontSecurityResult result = basicScanner.scan(tempFile);
            assertNotNull(result.sha256());
            assertFalse(result.sha256().isEmpty());
            assertEquals(64, result.sha256().length());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void noopScannerDoesNotSilentPass() {
        FontSecurityResult result = noopScanner.scan(new ByteArrayInputStream(new byte[0]), "test.ttf");
        assertFalse(result.productionSafe());
        assertEquals("WARNING_PASS", result.scanStatus());
        assertFalse(result.warnings().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("NOT production-safe") || w.contains("does not perform real security checks")));
    }

    private Path createTempFileWithContent(String extension, byte[] content) throws Exception {
        Path tempFile = Files.createTempFile("font-test-", extension);
        Files.write(tempFile, content);
        return tempFile;
    }
}
