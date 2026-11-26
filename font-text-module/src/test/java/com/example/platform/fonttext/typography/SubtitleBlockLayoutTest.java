package com.example.platform.fonttext.typography;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SubtitleBlockLayoutTest {
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
