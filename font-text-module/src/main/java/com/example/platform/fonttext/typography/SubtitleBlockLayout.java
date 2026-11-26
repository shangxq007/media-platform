package com.example.platform.fonttext.typography;

import com.example.platform.fonttext.text.TextContent;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.GraphicAttribute;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.AttributedString;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;

/** Deterministic layout profile for authored subtitles. Reference raster width is 320. */
public final class SubtitleBlockLayout {
    public static final String PROFILE = "noto-sc-color-emoji-v1";
    public static final int REFERENCE_WIDTH = 320;
    public static final int MAX_PNG_BYTES = 72_000;
    public static final int MAX_DESTINATION_WIDTH = 1920;
    // Maximum authored raster is 304×388 logical pixels. Even at 6×, an
    // uncompressible RGBA PNG with scanline/filter overhead fits this bound.
    public static final int MAX_NATIVE_PNG_BYTES = 18 * 1024 * 1024;
    private static final String ROOT = "/subtitle-cue-v1/";
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private SubtitleBlockLayout() {}

    public static void validateShape(String text, int width, int size) {
        if (text == null || text.length() > 384 || text.isBlank() || width < 40 || width > 304 || size < 12 || size > 44) {
            throw new IllegalArgumentException("Subtitle requires nonblank text, width 40–304 and font size 12–44");
        }
        if (text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r')) {
            throw new IllegalArgumentException("Subtitle text contains an unsupported control character");
        }
        new TextContent(text);
    }

    public static byte[] png(String text, int width, int size) {
        return png(text, width, size, REFERENCE_WIDTH);
    }

    /** Layout is always decided in the original 320-unit space, before painting. */
    public static byte[] png(String text, int width, int size, int destinationWidth) {
        validateShape(text, width, size);
        if (destinationWidth < 1 || destinationWidth > MAX_DESTINATION_WIDTH) {
            throw new IllegalArgumentException("Subtitle destination width is outside the raster budget");
        }
        double scale = destinationWidth / (double) REFERENCE_WIDTH;
        Font font = Resources.FONT.deriveFont((float) size);
        var frc = new java.awt.font.FontRenderContext(null, true, true);
        var lines = new ArrayList<TextLayout>();
        for (String paragraph : text.split("\\R", -1)) {
            if (paragraph.isEmpty()) {
                lines.add(null);
            } else {
                var content = paragraph(paragraph, font, size);
                var lineMeasurer = new LineBreakMeasurer(content.getIterator(), BreakIterator.getLineInstance(Locale.ROOT), frc);
                int last = content.getIterator().getEndIndex();
                while (lineMeasurer.getPosition() < last) {
                    TextLayout line = lineMeasurer.nextLayout(width - 4);
                    if (line.getVisibleAdvance() > width - 4 + 0.001) {
                        throw new IllegalArgumentException("Subtitle width is too narrow for a glyph");
                    }
                    lines.add(line);
                    if (lines.size() > 6) throw new IllegalArgumentException("Subtitle layout exceeds six lines");
                }
            }
            if (lines.size() > 6) throw new IllegalArgumentException("Subtitle layout exceeds six lines");
        }
        int lineHeight = (int) Math.ceil(size * 1.45);
        int baseline = (int) Math.ceil(size * 1.10);
        // Keep line wrapping, baseline spacing, and emoji placement in logical units.
        // Scale the paint transform before drawing TextLayout so the outline is
        // sampled at destination resolution. Emoji draw from the pinned native strike.
        int rasterWidth = Math.max(1, (int) Math.round(width * scale));
        int rasterHeight = Math.max(1, (int) Math.round((lines.size() * lineHeight + 4) * scale));
        BufferedImage block = new BufferedImage(rasterWidth, rasterHeight, BufferedImage.TYPE_INT_ARGB);
        var graphics = block.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setColor(Color.WHITE);
            graphics.scale(scale, scale);
            for (int row = 0; row < lines.size(); row++) {
                TextLayout line = lines.get(row);
                if (line != null) line.draw(graphics, 2, 2 + baseline + row * lineHeight);
            }
        } finally {
            graphics.dispose();
        }
        try {
            var bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(block, "png", bytes)) throw new IllegalStateException("PNG encoder unavailable");
            int budget = destinationWidth <= REFERENCE_WIDTH ? MAX_PNG_BYTES : MAX_NATIVE_PNG_BYTES;
            if (bytes.size() > budget) throw new IllegalArgumentException("Subtitle raster exceeds the supported block budget");
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Subtitle raster could not be encoded", failure);
        }
    }

    private static AttributedString paragraph(String paragraph, Font font, int size) {
        var display = new StringBuilder();
        var inline = new HashMap<Integer, String>();
        var matcher = GRAPHEME.matcher(paragraph);
        while (matcher.find()) {
            String grapheme = matcher.group();
            String candidate = grapheme.codePoints().filter(cp -> cp != 0xfe0f)
                    .mapToObj(Integer::toHexString).collect(java.util.stream.Collectors.joining("-"));
            if (Resources.EMOJI.containsKey(candidate) && grapheme.indexOf(0xfe0e) < 0) {
                inline.put(display.length(), candidate);
                display.append('\ufffc');
            } else {
                if (font.canDisplayUpTo(grapheme) != -1 || grapheme.codePoints().anyMatch(
                        cp -> cp == 0x200d || cp == 0xfe0f || cp >= 0x1f000 && cp <= 0x1faff)) {
                    throw new IllegalArgumentException("Subtitle font profile does not cover the sequence U+"
                            + grapheme.codePoints().mapToObj(Integer::toHexString).collect(java.util.stream.Collectors.joining("+")));
                }
                display.append(grapheme);
            }
        }
        var styled = new AttributedString(display.toString());
        styled.addAttribute(TextAttribute.FONT, font);
        for (var graphic : inline.entrySet()) {
            try {
                BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(Resources.EMOJI.get(graphic.getValue())));
                styled.addAttribute(TextAttribute.CHAR_REPLACEMENT, new Emoji(image, size),
                        graphic.getKey(), graphic.getKey() + 1);
            } catch (IOException failure) {
                throw new IllegalStateException("Bundled emoji raster could not be decoded", failure);
            }
        }
        return styled;
    }

    private static final class Emoji extends GraphicAttribute {
        private final BufferedImage image;
        private final int size;
        private final int width;

        Emoji(BufferedImage image, int size) {
            super(ROMAN_BASELINE);
            this.image = image;
            this.size = size;
            this.width = Math.round(size * image.getWidth() / (float) image.getHeight());
        }

        @Override public float getAscent() { return size * 0.85f; }
        @Override public float getDescent() { return size - getAscent(); }
        @Override public float getAdvance() { return width; }
        @Override public void draw(Graphics2D graphics, float x, float y) {
            graphics.drawImage(image, Math.round(x), Math.round(y - getAscent()), width, size, null);
        }
    }

    private static final class Resources {
        private static final Font FONT = loadFont();
        private static final Map<String, byte[]> EMOJI = loadEmoji();

        private static InputStream resource(String name, String expectedDigest) throws IOException {
            try (var source = SubtitleBlockLayout.class.getResourceAsStream(ROOT + name)) {
                if (source == null) throw new IllegalStateException("Missing bundled subtitle font resource " + name);
                var bytes = source.readAllBytes();
                var digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
                if (!java.util.HexFormat.of().formatHex(digest).equals(expectedDigest)) {
                    throw new IllegalStateException("Bundled subtitle font digest mismatch " + name);
                }
                return new java.io.ByteArrayInputStream(bytes);
            } catch (java.security.NoSuchAlgorithmException failure) {
                throw new IllegalStateException(failure);
            }
        }

        private static Font loadFont() {
            try (var bytes = resource("MP-Cue-Sans.otf", "65d9d8466d9d74d1a365f2e57b15a644c6af7da37899d7e643dcf15936ce8cfa")) {
                return Font.createFont(Font.TRUETYPE_FONT, bytes);
            } catch (IOException | java.awt.FontFormatException failure) {
                throw new IllegalStateException("Bundled subtitle font could not be loaded", failure);
            }
        }

        private static Map<String, byte[]> loadEmoji() {
            var images = new HashMap<String, byte[]>();
            try (var zip = new ZipInputStream(resource("color-emoji.zip", "c189ed682d4960f2eefa73e4db0c4b1a1d3d19ddcb1f943bd1e8344d33dadd3a"))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    images.put(entry.getName().replace(".png", ""), zip.readAllBytes());
                }
            } catch (IOException failure) {
                throw new IllegalStateException("Bundled emoji strike could not be loaded", failure);
            }
            return Map.copyOf(images);
        }
    }
}
