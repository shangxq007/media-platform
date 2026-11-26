package com.example.platform.fonttext.typography;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SubtitleBlockLayoutTest {

    @Test
    void largest_profile_extent_keeps_six_logical_lines_at_delivery_scale() throws Exception {
        String row = "鬱永🌻🦋🦚🍉";
        String dense = String.join("\n", java.util.Collections.nCopies(6, row));
        var logical = SubtitleBlockLayout.png(dense, 304, 44);
        assertArrayEquals(logical, SubtitleBlockLayout.png(dense, 304, 44, 320));
        assertTrue(logical.length <= SubtitleBlockLayout.MAX_PNG_BYTES);
        var nativePng = SubtitleBlockLayout.png(dense, 304, 44, 1920);
        var image = ImageIO.read(new ByteArrayInputStream(nativePng));
        assertEquals(1824, image.getWidth());
        assertEquals(2328, image.getHeight());
        assertTrue(nativePng.length > 131072 && nativePng.length < SubtitleBlockLayout.MAX_NATIVE_PNG_BYTES);
        assertEquals(0, image.getRGB(1823, 2327) >>> 24);
        assertThrows(IllegalArgumentException.class, () -> SubtitleBlockLayout.png(row, 304, 44, 1921));
    }

    @Test
    void explicitAndWidthDrivenBreaksUseTheSamePinnedGlyphs() throws Exception {
        String literal = "春天来了 🌻\uFE0F\nSpring is here";
        var explicit = ImageIO.read(new ByteArrayInputStream(SubtitleBlockLayout.png(literal, 200, 24)));
        var wrapped = ImageIO.read(new ByteArrayInputStream(SubtitleBlockLayout.png(literal.replace('\n', ' '), 160, 24)));
        assertEquals(74, explicit.getHeight());
        assertEquals(explicit.getHeight(), wrapped.getHeight());
        int sunflowerPixels = 0;
        for (int y = 0; y < explicit.getHeight(); y++) {
            for (int x = 0; x < wrapped.getWidth(); x++) {
                int pixel = explicit.getRGB(x, y);
                assertEquals(pixel, wrapped.getRGB(x, y));
                if (x > 100 && y < 36 && (pixel >>> 24) > 150 && (pixel >> 16 & 255) > 140
                        && (pixel >> 8 & 255) > 80 && (pixel & 255) < 125) sunflowerPixels++;
            }
        }
        assertTrue(sunflowerPixels > 30);
    }

    @Test
    void pinnedEmojiSequencesRenderAndUnsupportedGlyphsAreRejected() {
        assertArrayEquals(SubtitleBlockLayout.png("🌻", 100, 24), SubtitleBlockLayout.png("🌻\uFE0F", 100, 24));
        assertTrue(SubtitleBlockLayout.png("👩‍💻", 100, 24).length > 100);
        assertThrows(IllegalArgumentException.class, () -> SubtitleBlockLayout.png("\u0378", 100, 24));
        assertThrows(IllegalArgumentException.class, () -> SubtitleBlockLayout.png("春\n".repeat(7), 100, 24));
    }
}
