package com.example.platform.render.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SubtitleBurnInService {
    private final FontRegistryService fontRegistryService;

    @Value("${app.fonts.dir:/tmp/platform/fonts}")
    private String fontsDir;

    public SubtitleBurnInService(FontRegistryService fontRegistryService) {
        this.fontRegistryService = fontRegistryService;
    }

    public BufferedImage burnInFrame(BufferedImage image, int frame, int total,
                                      List<Map<String, Object>> cues) {
        if (cues == null || cues.isEmpty()) return image;

        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        Graphics2D g = result.createGraphics();
        g.drawImage(image, 0, 0, null);

        float fps = 30.0f;
        float currentTime = frame / fps;

        for (Map<String, Object> cue : cues) {
            String text = (String) cue.getOrDefault("text", "");
            if (text == null || text.isEmpty()) continue;

            double cueStart = ((Number) cue.getOrDefault("startTime", 0.0)).doubleValue();
            double cueEnd = ((Number) cue.getOrDefault("endTime", 0.0)).doubleValue();

            if (currentTime >= cueStart && currentTime <= cueEnd) {
                int w = image.getWidth(), h = image.getHeight();
                g.setFont(new Font("SansSerif", Font.BOLD, 28));
                FontMetrics fm = g.getFontMetrics();
                int textX = (w - fm.stringWidth(text)) / 2;
                int textY = h - 50;

                g.setColor(new Color(0, 0, 0, 180));
                g.drawString(text, textX + 2, textY + 2);
                g.setColor(new Color(0, 0, 0, 120));
                g.fillRoundRect(textX - 10, textY - fm.getAscent() - 4,
                        fm.stringWidth(text) + 20, fm.getHeight() + 8, 8, 8);
                g.setColor(Color.WHITE);
                g.drawString(text, textX, textY);
            }
        }

        g.dispose();
        return result;
    }

    public List<String> checkSubtitleCompatibility(List<Map<String, Object>> subtitleTracks) {
        List<String> warnings = new ArrayList<>();
        if (fontsDir == null) return warnings;

        for (Map<String, Object> track : subtitleTracks) {
            String fontId = (String) track.getOrDefault("fontId", null);
            if (fontId == null) continue;

            Path fontPath = Path.of(fontsDir, fontId + ".ttf");
            if (!Files.exists(fontPath)) {
                fontPath = Path.of(fontsDir, fontId + ".otf");
            }

            if (!Files.exists(fontPath)) {
                @SuppressWarnings("unchecked")
                List<String> fallbackIds = (List<String>) track.getOrDefault("fallbackFontIds", List.of());
                if (fallbackIds.isEmpty()) {
                    warnings.add("SUBTITLE_FONT_MISSING: " + fontId);
                }
            }

            if (fontRegistryService != null && Files.exists(fontPath)) {
                List<Map<String, Object>> cues = (List<Map<String, Object>>) track.getOrDefault("cues", List.of());
                StringBuilder allText = new StringBuilder();
                for (Map<String, Object> cue : cues) {
                    String text = (String) cue.getOrDefault("text", "");
                    if (text != null) allText.append(text);
                }
                List<String> missing = fontRegistryService.findMissingGlyphs(fontPath.toString(), allText.toString());
                if (!missing.isEmpty()) {
                    warnings.add("SUBTITLE_GLYPH_MISSING: font=" + fontId + " missing=" + missing.size());
                }
            }
        }
        return warnings;
    }

    public String resolveFontFile(String fontFilePath) {
        if (fontFilePath == null) return null;
        Path path = Path.of(fontFilePath);
        return Files.exists(path) ? fontFilePath : null;
    }

}
