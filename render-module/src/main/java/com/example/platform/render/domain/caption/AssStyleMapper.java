package com.example.platform.render.domain.caption;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Maps {@link CaptionTemplateSpec} typed domain model to ASS subtitle format
 * parameters for libass rendering.
 *
 * <p>Produces bounded, safe {@link AssStyleParams} and {@link AssDialogueEvent}
 * records. Does NOT produce raw ASS style strings.</p>
 *
 * <p>Constraints:
 * <ul>
 *   <li>No raw FFmpeg commands</li>
 *   <li>No raw ASS style strings</li>
 *   <li>No provider/storage calls</li>
 *   <li>All output is bounded and validated</li>
 * </ul>
 */
public class AssStyleMapper {

    /**
     * ASS numpad alignment mapping from placement + text-align.
     *
     * <pre>
     * Placement     | left | center | right
     * --------------|------|--------|------
     * TOP_CENTER    |  7   |   8    |  9
     * CENTER        |  4   |   5    |  6
     * BOTTOM_CENTER |  1   |   2    |  3
     * </pre>
     */
    private static final Set<String> ALLOWED_ALIGNMENTS = Set.of("left", "center", "right");

    /**
     * Map a validated {@link CaptionTemplateSpec} to ASS style parameters.
     *
     * @param template caption template specification (uses defaults if null)
     * @return bounded ASS style parameters
     */
    public AssStyleParams mapToAssStyle(CaptionTemplateSpec template) {
        CaptionStyleSpec style = (template != null && template.style() != null)
                ? template.style()
                : CaptionStyleSpec.defaults();

        String fontFamily = resolveFontFamily(style);
        int fontSize = resolveFontSize(style);
        int bold = resolveBold(style);
        long primaryColor = resolvePrimaryColor(style);
        long outlineColor = resolveOutlineColor(style);
        int outlineWidth = resolveOutlineWidth(style);
        int alignment = resolveAlignment(style);

        AssStyleParams params = new AssStyleParams(
                fontFamily, fontSize, bold,
                primaryColor, outlineColor, outlineWidth,
                alignment, 20, 20, 40);

        if (!params.isWithinBounds()) {
            throw new IllegalStateException("ASS style params out of bounds: " + params);
        }

        return params;
    }

    /**
     * Map caption segments to ASS dialogue events.
     *
     * @param segments caption segments with timing
     * @return list of bounded, sanitized ASS dialogue events
     */
    public List<AssDialogueEvent> mapToDialogueEvents(List<CaptionSegmentSpec> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }

        List<AssDialogueEvent> events = new ArrayList<>(segments.size());
        for (CaptionSegmentSpec seg : segments) {
            String start = formatAssTime(seg.startSeconds());
            String end = formatAssTime(seg.endSeconds());
            String safeText = sanitizeDialogueText(seg.text());

            AssDialogueEvent event = new AssDialogueEvent(start, end, safeText);
            if (!event.isValid()) {
                throw new IllegalStateException("Invalid ASS dialogue event: " + event);
            }
            events.add(event);
        }
        return List.copyOf(events);
    }

    // --- Resolution helpers ---

    private String resolveFontFamily(CaptionStyleSpec style) {
        if (style.font() != null && style.font().family() != null) {
            return style.font().family();
        }
        return "DejaVu Sans";
    }

    private int resolveFontSize(CaptionStyleSpec style) {
        if (style.fontSize() > 0) {
            return Math.max(AssStyleParams.MIN_FONT_SIZE,
                    Math.min(AssStyleParams.MAX_FONT_SIZE, style.fontSize()));
        }
        return 24;
    }

    private int resolveBold(CaptionStyleSpec style) {
        if (style.font() != null && style.font().weight() >= 700) {
            return 1;
        }
        return 0;
    }

    private long resolvePrimaryColor(CaptionStyleSpec style) {
        if (style.font() != null && style.font().color() != null) {
            return hexToAssColor(style.font().color());
        }
        return hexToAssColor("#FFFFFF");
    }

    private long resolveOutlineColor(CaptionStyleSpec style) {
        if (style.font() != null && style.font().outlineColor() != null) {
            return hexToAssColor(style.font().outlineColor());
        }
        return hexToAssColor("#000000");
    }

    private int resolveOutlineWidth(CaptionStyleSpec style) {
        if (style.font() != null) {
            return Math.max(0, Math.min(AssStyleParams.MAX_OUTLINE_WIDTH, style.font().outlineWidth()));
        }
        return 2;
    }

    private int resolveAlignment(CaptionStyleSpec style) {
        int row = placementToRow(style.placement());
        int col = textAlignToCol(style.textAlign());
        // ASS numpad layout:
        //   7 8 9  (top row=2)
        //   4 5 6  (center row=1)
        //   1 2 3  (bottom row=0)
        // col: left=0, center=1, right=2
        // alignment = row * 3 + col + 1
        return row * 3 + col + 1;
    }

    /**
     * Map placement to logical row: 0=bottom, 1=center, 2=top.
     */
    private int placementToRow(CaptionPlacement placement) {
        if (placement == null) return 0; // BOTTOM
        return switch (placement) {
            case BOTTOM_CENTER -> 0;
            case CENTER -> 1;
            case TOP_CENTER -> 2;
        };
    }

    /**
     * Map text-align to ASS numpad column: left=0, center=1, right=2.
     */
    private int textAlignToCol(String textAlign) {
        if (textAlign == null || !ALLOWED_ALIGNMENTS.contains(textAlign.toLowerCase())) {
            return 1; // center default (0-indexed)
        }
        return switch (textAlign.toLowerCase()) {
            case "left" -> 0;
            case "right" -> 2;
            default -> 1;
        };
    }

    // --- Color conversion ---

    /**
     * Convert #RRGGBB hex to ASS &amp;HAABBGGRR unsigned int.
     *
     * <p>ASS color format is ABGR with full opacity (alpha=00).
     * Input must be a valid 6-digit hex color.</p>
     *
     * @param hex color in #RRGGBB format
     * @return ASS color as unsigned 32-bit integer (&amp;H00BBGGRR)
     */
    long hexToAssColor(String hex) {
        if (hex == null || !hex.matches("^#[0-9a-fA-F]{6}$")) {
            throw new IllegalArgumentException("Invalid hex color: " + hex);
        }
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        // ASS: alpha=00 (fully opaque), then B, G, R
        return (long) b << 16 | (long) g << 8 | (long) r;
    }

    // --- Time formatting ---

    /**
     * Format seconds to ASS time: H:MM:SS.cc (centiseconds).
     *
     * @param seconds time in seconds
     * @return ASS-formatted time string
     */
    String formatAssTime(double seconds) {
        if (seconds < 0) seconds = 0;
        int totalCentiseconds = (int) Math.round(seconds * 100);
        int cs = totalCentiseconds % 100;
        int totalSeconds = totalCentiseconds / 100;
        int s = totalSeconds % 60;
        int totalMinutes = totalSeconds / 60;
        int m = totalMinutes % 60;
        int h = totalMinutes / 60;
        return String.format("%d:%02d:%02d.%02d", h, m, s, cs);
    }

    // --- Text sanitization ---

    /**
     * Sanitize dialogue text for safe ASS output.
     *
     * <p>Escapes backslashes and newlines to prevent injection of ASS override
     * codes. Defense-in-depth on top of the validator.</p>
     *
     * @param text raw caption text (pre-validated)
     * @return sanitized text safe for ASS dialogue field
     */
    String sanitizeDialogueText(String text) {
        if (text == null) return "";
        // Escape backslash first (must be before other escapes)
        String safe = text.replace("\\", "\\\\");
        // Convert newlines to ASS line break
        safe = safe.replace("\n", "\\N");
        safe = safe.replace("\r", "");
        return safe;
    }
}
