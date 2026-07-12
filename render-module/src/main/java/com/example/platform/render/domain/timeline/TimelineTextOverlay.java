package com.example.platform.render.domain.timeline;

/**
 * Text overlay specification for a timeline.
 *
 * <p>Text overlays are rendered on top of video tracks at a specific position
 * and time range.</p>
 *
 * @param id            unique overlay identifier
 * @param text          the text content to render (plain text, max 500 chars)
 * @param fontFamily    font family name (e.g., "DejaVu Sans"), not a file path
 * @param fontSize      font size in points (8-160)
 * @param color         text color (e.g., "#FFFFFF")
 * @param positionX     horizontal position (pixels or percentage)
 * @param positionY     vertical position (pixels or percentage)
 * @param startTime     start time on the timeline in seconds (>= 0)
 * @param duration      duration in seconds (> 0)
 * @param backgroundColor background color (null = transparent)
 */
public record TimelineTextOverlay(
        String id,
        String text,
        String fontFamily,
        int fontSize,
        String color,
        String positionX,
        String positionY,
        double startTime,
        double duration,
        String backgroundColor) {

    public TimelineTextOverlay {
        if (text != null && text.length() > 500) {
            throw new IllegalArgumentException("Text overlay text exceeds max length (500 chars)");
        }
        if (fontSize < 8 || fontSize > 160) {
            throw new IllegalArgumentException("fontSize must be between 8 and 160");
        }
        if (duration <= 0) {
            throw new IllegalArgumentException("duration must be positive");
        }
        if (startTime < 0) {
            throw new IllegalArgumentException("startTime must be non-negative");
        }
    }

    /**
     * Creates a simple text overlay.
     */
    public static TimelineTextOverlay of(String id, String text, double startTime, double duration) {
        return new TimelineTextOverlay(id, text, "DejaVu Sans", 24, "#FFFFFF",
                "center", "bottom", startTime, duration, null);
    }
}
