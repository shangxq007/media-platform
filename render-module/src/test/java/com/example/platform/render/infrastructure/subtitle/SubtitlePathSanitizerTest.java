package com.example.platform.render.infrastructure.subtitle;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SubtitlePathSanitizerTest {

    @Test
    void validStorageKeyAccepted() {
        String result = SubtitlePathSanitizer.sanitize("tenant-1/project-1/subs/en.srt", "/data");
        assertNotNull(result);
        assertEquals("tenant-1/project-1/subs/en.srt", result);
    }

    @Test
    void relativePathAccepted() {
        String result = SubtitlePathSanitizer.sanitize("subtitles/en.srt", null);
        assertNotNull(result);
        assertEquals("subtitles/en.srt", result);
    }

    @Test
    void absolutePathRejected() {
        assertNull(SubtitlePathSanitizer.sanitize("/etc/passwd", null));
    }

    @Test
    void absolutePathUnderStorageRootAccepted() {
        String result = SubtitlePathSanitizer.sanitize("/data/platform/subs/en.srt", "/data/platform");
        assertNotNull(result);
    }

    @Test
    void absolutePathOutsideStorageRootRejected() {
        assertNull(SubtitlePathSanitizer.sanitize("/etc/passwd", "/data/platform"));
    }

    @Test
    void traversalRejected() {
        assertNull(SubtitlePathSanitizer.sanitize("../secret.srt", null));
        assertNull(SubtitlePathSanitizer.sanitize("subs/../../../etc/passwd", null));
    }

    @Test
    void encodedTraversalRejected() {
        assertNull(SubtitlePathSanitizer.sanitize("..%2Fsecret.srt", null));
        assertNull(SubtitlePathSanitizer.sanitize("%2e%2e/secret.srt", null));
        assertNull(SubtitlePathSanitizer.sanitize("subs/%2e%2e/secret.srt", null));
    }

    @Test
    void backslashTraversalRejected() {
        assertNull(SubtitlePathSanitizer.sanitize("..\\secret.srt", null));
        assertNull(SubtitlePathSanitizer.sanitize("subs\\..\\secret.srt", null));
    }

    @Test
    void fileSchemeRejected() {
        assertNull(SubtitlePathSanitizer.sanitize("file:///etc/passwd", null));
        assertNull(SubtitlePathSanitizer.sanitize("file:///data/subs/en.srt", null));
    }

    @Test
    void httpSchemeRejected() {
        assertNull(SubtitlePathSanitizer.sanitize("http://evil.com/subs.srt", null));
        assertNull(SubtitlePathSanitizer.sanitize("https://evil.com/subs.srt", null));
    }

    @Test
    void ffmpegProtocolRejected() {
        assertNull(SubtitlePathSanitizer.sanitize("concat:file1|file2", null));
        assertNull(SubtitlePathSanitizer.sanitize("data:text/plain;base64,SGVsbG8=", null));
    }

    @Test
    void filterSeparatorRejected() {
        assertNull(SubtitlePathSanitizer.sanitize("subs/en.srt,force_style=FontName=Arial", null));
        assertNull(SubtitlePathSanitizer.sanitize("subs/en.srt[0]", null));
    }

    @Test
    void nullByteRejected() {
        assertNull(SubtitlePathSanitizer.sanitize("subs/en\0.srt", null));
    }

    @Test
    void windowsDriveRejected() {
        assertNull(SubtitlePathSanitizer.sanitize("C:\\Windows\\Fonts\\evil.ttf", null));
        assertNull(SubtitlePathSanitizer.sanitize("D:/data/subs.srt", null));
    }

    @Test
    void nullInputReturnsNull() {
        assertNull(SubtitlePathSanitizer.sanitize(null, null));
    }

    @Test
    void blankInputReturnsNull() {
        assertNull(SubtitlePathSanitizer.sanitize("   ", null));
    }

    @Test
    void createSafeTempPathGeneratesUniqueNames() {
        Path tempDir = Path.of("/tmp");
        Path path1 = SubtitlePathSanitizer.createSafeTempPath(tempDir, ".srt");
        Path path2 = SubtitlePathSanitizer.createSafeTempPath(tempDir, ".srt");

        assertNotEquals(path1, path2, "Temp paths should be unique");
        assertTrue(path1.getFileName().toString().startsWith("subtitle-"));
        assertTrue(path1.getFileName().toString().endsWith(".srt"));
    }
}
