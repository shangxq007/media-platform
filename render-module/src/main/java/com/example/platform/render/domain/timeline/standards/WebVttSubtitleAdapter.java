package com.example.platform.render.domain.timeline.standards;

import com.example.platform.render.domain.timeline.TimelineTextOverlay;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebVTT import/export for HLS/DASH soft subtitles (Web 播放).
 */
public final class WebVttSubtitleAdapter {

    private static final Pattern CUE_TIME = Pattern.compile(
            "(\\d{2}):(\\d{2}):(\\d{2}\\.\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}\\.\\d{3})");

    private WebVttSubtitleAdapter() {
    }

    public static List<TimelineTextOverlay> parse(String vttContent) {
        List<TimelineTextOverlay> overlays = new ArrayList<>();
        if (vttContent == null || vttContent.isBlank()) {
            return overlays;
        }
        String body = vttContent.replace("\uFEFF", "").trim();
        if (body.startsWith("WEBVTT")) {
            int idx = body.indexOf('\n');
            body = idx >= 0 ? body.substring(idx + 1) : "";
        }
        String[] blocks = body.split("\n\n+");
        int index = 0;
        for (String block : blocks) {
            String[] lines = block.trim().split("\n");
            if (lines.length < 2) {
                continue;
            }
            int timeLineIdx = lines[0].contains("-->") ? 0 : (lines.length > 1 && lines[1].contains("-->") ? 1 : -1);
            if (timeLineIdx < 0) {
                continue;
            }
            Matcher m = CUE_TIME.matcher(lines[timeLineIdx].trim());
            if (!m.find()) {
                continue;
            }
            double start = toSeconds(m.group(1), m.group(2), m.group(3));
            double end = toSeconds(m.group(4), m.group(5), m.group(6));
            StringBuilder text = new StringBuilder();
            for (int i = timeLineIdx + 1; i < lines.length; i++) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(lines[i].trim());
            }
            if (!text.isEmpty()) {
                overlays.add(TimelineTextOverlay.of("vtt-" + (++index), text.toString(), start,
                        Math.max(0.1, end - start)));
            }
        }
        return overlays;
    }

    public static String toWebVtt(List<TimelineTextOverlay> overlays, String language) {
        StringBuilder sb = new StringBuilder("WEBVTT");
        if (language != null && !language.isBlank()) {
            sb.append("\nLanguage: ").append(language);
        }
        sb.append("\n\n");
        for (TimelineTextOverlay o : overlays) {
            sb.append(formatCueTime(o.startTime())).append(" --> ")
                    .append(formatCueTime(o.startTime() + o.duration())).append('\n');
            sb.append(o.text() != null ? o.text() : "").append("\n\n");
        }
        return sb.toString();
    }

    private static double toSeconds(String h, String m, String sFrac) {
        double s = Double.parseDouble(sFrac.replace(',', '.'));
        return Integer.parseInt(h) * 3600.0 + Integer.parseInt(m) * 60.0 + s;
    }

    private static String formatCueTime(double seconds) {
        int h = (int) (seconds / 3600);
        int m = (int) ((seconds % 3600) / 60);
        double s = seconds % 60;
        return String.format("%02d:%02d:%06.3f", h, m, s);
    }
}
