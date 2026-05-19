package com.example.platform.render.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(SubtitleBurnInService.class);

    private final FontRegistryService fontRegistryService;

    @Value("${app.fonts.dir:/tmp/platform/fonts}")
    private String fontsDir;

    public SubtitleBurnInService(FontRegistryService fontRegistryService) {
        this.fontRegistryService = fontRegistryService;
    }

    public String buildSubtitleFilter(List<Map<String, Object>> subtitleTracks) {
        StringBuilder filter = new StringBuilder();
        int drawtextIdx = 0;

        for (Map<String, Object> track : subtitleTracks) {
            if (!Boolean.TRUE.equals(track.get("burnIn"))) continue;

            List<Map<String, Object>> cues = (List<Map<String, Object>>) track.getOrDefault("cues", List.of());
            String fontId = (String) track.getOrDefault("fontId", null);
            String glyphSubsetFile = (String) track.getOrDefault("glyphSubsetFile", null);

            StringBuilder trackText = new StringBuilder();
            for (Map<String, Object> cue : cues) {
                String text = (String) cue.getOrDefault("text", "");
                if (text != null && !text.isEmpty()) trackText.append(text);
            }

            String fontFilePath = null;
            if (glyphSubsetFile != null && Files.exists(Path.of(glyphSubsetFile))) {
                fontFilePath = glyphSubsetFile;
            } else if (fontRegistryService != null && fontsDir != null) {
                @SuppressWarnings("unchecked")
                List<String> fallbackIds = (List<String>) track.getOrDefault("fallbackFontIds", List.of());
                fontFilePath = fontRegistryService.resolveFontWithFallback(fontId, fallbackIds, trackText.toString());
            }

            if (fontFilePath != null && fontRegistryService != null) {
                List<String> missingGlyphs = fontRegistryService.findMissingGlyphs(fontFilePath, trackText.toString());
                if (!missingGlyphs.isEmpty()) {
                    log.warn("Font {} missing {} glyphs", fontFilePath, missingGlyphs.size());
                }
            }

            String fontParam = fontFilePath != null ? ":fontfile=" + fontFilePath : "";

            for (Map<String, Object> cue : cues) {
                String text = (String) cue.getOrDefault("text", "");
                if (text == null || text.isEmpty()) continue;

                double startTime = ((Number) cue.getOrDefault("startTime", 0.0)).doubleValue();
                double endTime = ((Number) cue.getOrDefault("endTime", 0.0)).doubleValue();
                text = text.replace("'", "'\\\\''").replace(":", "\\:").replace("\n", "\\N");

                if (drawtextIdx > 0) filter.append(",");
                filter.append(String.format(
                        "drawtext=text='%s':fontsize=24:fontcolor=white:box=1:boxcolor=black@0.5" +
                        ":x=(w-text_w)/2:y=h-text_h-20:enable='between(t,%.1f,%.1f)'%s",
                        text, startTime, endTime, fontParam
                ));
                drawtextIdx++;
            }
        }
        return filter.toString();
    }

    public String buildSubtitleFilter(List<Map<String, Object>> subtitleTracks, RenderPreset preset) {
        StringBuilder filter = new StringBuilder();
        int cueIndex = 0;

        for (Map<String, Object> subTrack : subtitleTracks) {
            if (!Boolean.TRUE.equals(subTrack.get("burnIn"))) continue;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cues = (List<Map<String, Object>>) subTrack.getOrDefault("cues", List.of());
            if (cues.isEmpty()) continue;

            String fontFile = resolveTrackFontFile(subTrack);
            String fontParam = (fontFile != null && !fontFile.isEmpty()) ? ":fontfile=" + fontFile : "";

            for (Map<String, Object> cue : cues) {
                String text = (String) cue.getOrDefault("text", "");
                if (text == null || text.isEmpty()) continue;

                double cueStart = ((Number) cue.getOrDefault("startTime", 0.0)).doubleValue();
                double cueEnd = ((Number) cue.getOrDefault("endTime", 0.0)).doubleValue();

                text = text.replace("'", "'\\\\''")
                        .replace(":", "\\:")
                        .replace(",", "\\,")
                        .replace("%", "%%");

                if (cueIndex > 0) filter.append(",");

                filter.append(String.format(
                        "drawtext=text='%s':fontsize=24:fontcolor=white:box=1:boxcolor=black@0.5" +
                        ":x=(w-text_w)/2:y=h-text_h-20:enable='between(t,%.1f,%.1f)'%s",
                        text, cueStart, cueEnd, fontParam
                ));
                cueIndex++;
            }
        }

        return filter.toString();
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

    private String resolveTrackFontFile(Map<String, Object> subTrack) {
        String fontId = (String) subTrack.get("fontId");
        if (fontId == null) return null;

        @SuppressWarnings("unchecked")
        List<String> fallbackIds = (List<String>) subTrack.getOrDefault("fallbackFontIds", List.of());

        String fontPath = Path.of(System.getProperty("java.io.tmpdir"), "fonts", fontId + ".ttf").toString();
        if (Files.exists(Path.of(fontPath))) {
            return fontPath;
        }

        for (String fallbackId : fallbackIds) {
            String fallbackPath = Path.of(System.getProperty("java.io.tmpdir"), "fonts", fallbackId + ".ttf").toString();
            if (Files.exists(Path.of(fallbackPath))) {
                return fallbackPath;
            }
        }

        return null;
    }
}
