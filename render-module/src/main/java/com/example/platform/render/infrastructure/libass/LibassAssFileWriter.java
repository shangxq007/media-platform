package com.example.platform.render.infrastructure.libass;

import com.example.platform.render.domain.timeline.TimelineTextOverlay;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes minimal ASS subtitle files for FFmpeg {@code -vf ass=} burn-in (L6 libass path).
 */
public class LibassAssFileWriter {

    public Path write(Path target, List<TimelineTextOverlay> overlays, int playResX, int playResY)
            throws IOException {
        Files.createDirectories(target.getParent());
        StringBuilder ass = new StringBuilder();
        ass.append("[Script Info]\n");
        ass.append("Title: Platform L6 libass\n");
        ass.append("ScriptType: v4.00+\n");
        ass.append("PlayResX: ").append(playResX).append("\n");
        ass.append("PlayResY: ").append(playResY).append("\n");
        ass.append("\n[V4+ Styles]\n");
        ass.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, "
                + "Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, "
                + "Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n");
        ass.append("Style: Default,DejaVu Sans,24,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,"
                + "0,0,0,0,100,100,0,0,1,2,0,2,10,10,30,1\n");
        ass.append("\n[Events]\n");
        ass.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");

        if (overlays != null) {
            for (TimelineTextOverlay overlay : overlays) {
                if (overlay.text() == null || overlay.text().isBlank()) {
                    continue;
                }
                String start = formatAssTime(overlay.startTime());
                String end = formatAssTime(overlay.startTime() + overlay.duration());
                String text = AssTextSanitizer.sanitize(overlay.text());
                ass.append("Dialogue: 0,").append(start).append(",").append(end)
                        .append(",Default,,0,0,0,,").append(text).append("\n");
            }
        }

        Files.writeString(target, ass.toString(), StandardCharsets.UTF_8);
        return target;
    }

    private String formatAssTime(double seconds) {
        int h = (int) (seconds / 3600);
        int m = (int) ((seconds % 3600) / 60);
        double s = seconds % 60;
        return String.format("%d:%02d:%05.2f", h, m, s);
    }
}
