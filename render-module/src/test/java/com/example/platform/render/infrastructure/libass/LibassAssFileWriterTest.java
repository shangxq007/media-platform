package com.example.platform.render.infrastructure.libass;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.TimelineTextOverlay;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibassAssFileWriterTest {

    private final LibassAssFileWriter writer = new LibassAssFileWriter();

    @TempDir
    Path tempDir;

    @Test
    void writesHeader(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("test.ass");
        writer.write(target, List.of(), 1920, 1080);

        String content = Files.readString(target);
        assertTrue(content.contains("[Script Info]"));
        assertTrue(content.contains("ScriptType: v4.00+"));
        assertTrue(content.contains("PlayResX: 1920"));
        assertTrue(content.contains("PlayResY: 1080"));
    }

    @Test
    void writesStyle(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("test.ass");
        writer.write(target, List.of(), 1920, 1080);

        String content = Files.readString(target);
        assertTrue(content.contains("[V4+ Styles]"));
        assertTrue(content.contains("Style: Default"));
    }

    @Test
    void writesDialogueRows(@TempDir Path tempDir) throws IOException {
        List<TimelineTextOverlay> overlays = List.of(
                TimelineTextOverlay.of("1", "Hello", 1.0, 3.0),
                TimelineTextOverlay.of("2", "World", 5.0, 2.0)
        );

        Path target = tempDir.resolve("test.ass");
        writer.write(target, overlays, 1920, 1080);

        String content = Files.readString(target);
        assertTrue(content.contains("[Events]"));
        assertTrue(content.contains("Dialogue:"));
        assertTrue(content.contains("Hello"));
        assertTrue(content.contains("World"));
    }

    @Test
    void escapesMaliciousText(@TempDir Path tempDir) throws IOException {
        List<TimelineTextOverlay> overlays = List.of(
                TimelineTextOverlay.of("1", "{\\pos(0,0)}Malicious", 1.0, 3.0)
        );

        Path target = tempDir.resolve("test.ass");
        writer.write(target, overlays, 1920, 1080);

        String content = Files.readString(target);
        // The override tag should be escaped
        assertFalse(content.contains("{\\pos"), "Override tag should be escaped");
        assertTrue(content.contains("Malicious"), "Text content should be preserved");
    }

    @Test
    void preservesUnicode(@TempDir Path tempDir) throws IOException {
        List<TimelineTextOverlay> overlays = List.of(
                TimelineTextOverlay.of("1", "你好世界 🎉", 1.0, 3.0)
        );

        Path target = tempDir.resolve("test.ass");
        writer.write(target, overlays, 1920, 1080);

        String content = Files.readString(target);
        assertTrue(content.contains("你好世界"));
        assertTrue(content.contains("🎉"));
    }

    @Test
    void handlesMultiLineCue(@TempDir Path tempDir) throws IOException {
        List<TimelineTextOverlay> overlays = List.of(
                TimelineTextOverlay.of("1", "Line one\nLine two", 1.0, 3.0)
        );

        Path target = tempDir.resolve("test.ass");
        writer.write(target, overlays, 1920, 1080);

        String content = Files.readString(target);
        // The sanitizer converts \n to \N, then removes backslashes except \N/\n/\h
        // So the result should contain \N for line breaks
        assertTrue(content.contains("Line one"), "First line should be present");
        assertTrue(content.contains("Line two"), "Second line should be present");
    }

    @Test
    void commasDoNotCorruptFields(@TempDir Path tempDir) throws IOException {
        List<TimelineTextOverlay> overlays = List.of(
                TimelineTextOverlay.of("1", "Hello, World!", 1.0, 3.0)
        );

        Path target = tempDir.resolve("test.ass");
        writer.write(target, overlays, 1920, 1080);

        String content = Files.readString(target);
        // Dialogue line format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        // The text portion can contain commas — they don't corrupt the field structure
        assertTrue(content.contains("Hello, World!"));
    }

    @Test
    void skipsNullAndBlankText(@TempDir Path tempDir) throws IOException {
        List<TimelineTextOverlay> overlays = List.of(
                TimelineTextOverlay.of("1", null, 1.0, 3.0),
                TimelineTextOverlay.of("2", "", 2.0, 3.0),
                TimelineTextOverlay.of("3", "   ", 3.0, 3.0),
                TimelineTextOverlay.of("4", "Valid", 4.0, 3.0)
        );

        Path target = tempDir.resolve("test.ass");
        writer.write(target, overlays, 1920, 1080);

        String content = Files.readString(target);
        // Only "Valid" should appear as a Dialogue line
        long dialogueCount = content.lines().filter(l -> l.startsWith("Dialogue:")).count();
        assertEquals(1, dialogueCount, "Only non-blank text should generate Dialogue lines");
        assertTrue(content.contains("Valid"));
    }

    @Test
    void styleDefaultsAreSafe(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("test.ass");
        writer.write(target, List.of(), 1920, 1080);

        String content = Files.readString(target);
        // Default style should use a safe font
        assertTrue(content.contains("DejaVu Sans"), "Default font should be DejaVu Sans");
        // Should not contain user-controllable style injection
        assertFalse(content.contains("Style: Default,;"));
    }
}
