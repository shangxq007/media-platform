package com.example.platform.render.domain.timeline.standards;

import com.example.platform.render.domain.timeline.TimelineTextOverlay;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SRT subtitle import (普通对白 / 交付字幕).
 */
public final class SrtSubtitleAdapter {

    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})");

    private SrtSubtitleAdapter() {
    }

    public static List<TimelineTextOverlay> parse(String srtContent) {
        List<TimelineTextOverlay> overlays = new ArrayList<>();
        if (srtContent == null || srtContent.isBlank()) {
            return overlays;
        }
        try (BufferedReader reader = new BufferedReader(new StringReader(srtContent))) {
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                Matcher m = TIME_PATTERN.matcher(line.trim());
                if (!m.find()) {
                    continue;
                }
                double start = toSeconds(m.group(1), m.group(2), m.group(3), m.group(4));
                double end = toSeconds(m.group(5), m.group(6), m.group(7), m.group(8));
                StringBuilder text = new StringBuilder();
                while ((line = reader.readLine()) != null && !line.isBlank()) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append(line.trim());
                }
                if (!text.isEmpty()) {
                    overlays.add(new TimelineTextOverlay(
                            "srt-" + (++index),
                            text.toString(),
                            "DejaVu Sans",
                            24,
                            "#FFFFFF",
                            "center",
                            "bottom",
                            start,
                            Math.max(0.1, end - start),
                            null));
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse SRT: " + e.getMessage(), e);
        }
        return overlays;
    }

    public static String toSrt(List<TimelineTextOverlay> overlays) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (TimelineTextOverlay o : overlays) {
            sb.append(i++).append('\n');
            sb.append(formatTime(o.startTime())).append(" --> ")
                    .append(formatTime(o.startTime() + o.duration())).append('\n');
            sb.append(o.text() != null ? o.text().replace("\n", "\n") : "").append("\n\n");
        }
        return sb.toString();
    }

    private static double toSeconds(String h, String m, String s, String ms) {
        return Integer.parseInt(h) * 3600.0
                + Integer.parseInt(m) * 60.0
                + Integer.parseInt(s)
                + Integer.parseInt(ms) / 1000.0;
    }

    private static String formatTime(double seconds) {
        int h = (int) (seconds / 3600);
        int m = (int) ((seconds % 3600) / 60);
        int s = (int) (seconds % 60);
        int ms = (int) ((seconds - Math.floor(seconds)) * 1000);
        return String.format("%02d:%02d:%02d,%03d", h, m, s, ms);
    }
}
