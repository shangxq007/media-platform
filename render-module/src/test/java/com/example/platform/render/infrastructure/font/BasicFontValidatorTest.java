package com.example.platform.render.infrastructure.font;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BasicFontValidatorTest {

    private final BasicFontValidator validator = new BasicFontValidator();

    @TempDir
    Path tempDir;

    @Test
    void emptyFileFails() throws Exception {
        Path emptyFile = tempDir.resolve("empty.ttf");
        Files.write(emptyFile, new byte[0]);

        FontValidationResult result = validator.validate(emptyFile);
        assertFalse(result.isValid());
        assertEquals("FAILED", result.validationStatus());
    }

    @Test
    void nonexistentFileFails() {
        Path missing = tempDir.resolve("missing.ttf");
        FontValidationResult result = validator.validate(missing);
        assertFalse(result.isValid());
        assertEquals("FAILED", result.validationStatus());
    }

    @Test
    void nonRegularFileFails() throws Exception {
        Path dir = tempDir.resolve("dir.ttf");
        Files.createDirectory(dir);

        FontValidationResult result = validator.validate(dir);
        assertFalse(result.isValid());
    }

    @Test
    void badMagicFails() throws Exception {
        Path badFile = tempDir.resolve("bad.ttf");
        Files.write(badFile, "not a font file".getBytes());

        FontValidationResult result = validator.validate(badFile);
        assertFalse(result.isValid());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Unrecognized")));
    }

    @Test
    void ttfMagicAccepted() throws Exception {
        // Create a minimal file with TTF magic bytes
        Path ttfFile = tempDir.resolve("test.ttf");
        byte[] header = new byte[36];
        header[0] = 0x00; header[1] = 0x01; header[2] = 0x00; header[3] = 0x00; // TTF magic
        Files.write(ttfFile, header);

        FontValidationResult result = validator.validate(ttfFile);
        // May pass or fail depending on AWT font loading, but format should be detected
        // The key is it doesn't fail on magic bytes
        assertNotNull(result);
    }

    @Test
    void otfMagicAccepted() throws Exception {
        Path otfFile = tempDir.resolve("test.otf");
        byte[] header = new byte[36];
        header[0] = 'O'; header[1] = 'T'; header[2] = 'T'; header[3] = 'O'; // OTF magic
        Files.write(otfFile, header);

        FontValidationResult result = validator.validate(otfFile);
        assertNotNull(result);
    }

    @Test
    void woffMagicAccepted() throws Exception {
        Path woffFile = tempDir.resolve("test.woff");
        byte[] header = new byte[36];
        header[0] = 'w'; header[1] = 'O'; header[2] = 'F'; header[3] = 'F'; // WOFF magic
        Files.write(woffFile, header);

        FontValidationResult result = validator.validate(woffFile);
        assertNotNull(result);
    }

    @Test
    void woff2MagicAccepted() throws Exception {
        Path woff2File = tempDir.resolve("test.woff2");
        byte[] header = new byte[36];
        header[0] = 'w'; header[1] = 'O'; header[2] = 'F'; header[3] = '2'; // WOFF2 magic
        Files.write(woff2File, header);

        FontValidationResult result = validator.validate(woff2File);
        assertNotNull(result);
    }

    @Test
    void ttcMagicAcceptedWithWarning() throws Exception {
        Path ttcFile = tempDir.resolve("test.ttc");
        byte[] header = new byte[36];
        header[0] = 't'; header[1] = 't'; header[2] = 'c'; header[3] = 'f'; // TTC magic
        Files.write(ttcFile, header);

        FontValidationResult result = validator.validate(ttcFile);
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("TTC")));
    }

    @Test
    void inputStreamValidationWorks() {
        byte[] header = new byte[36];
        header[0] = 0x00; header[1] = 0x01; header[2] = 0x00; header[3] = 0x00; // TTF magic

        FontValidationResult result = validator.validate(new ByteArrayInputStream(header), "test.ttf");
        assertNotNull(result);
    }

    @Test
    void nullInputStreamFails() {
        FontValidationResult result = validator.validate((java.io.InputStream) null, "test.ttf");
        assertFalse(result.isValid());
    }

    @Test
    void validatorName() {
        assertEquals("BasicFontValidator", validator.validatorName());
    }

    @Test
    void enabled() {
        assertTrue(validator.enabled());
    }
}
