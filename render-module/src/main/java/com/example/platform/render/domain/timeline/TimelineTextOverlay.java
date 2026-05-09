package com.example.platform.render.domain.timeline;

/**
 * Text overlay specification for a timeline.
 *
 * <p>Text overlays are rendered on top of video tracks at a specific position
 * and time range.</p>
 *
 * @param id            unique overlay identifier
 * @param text          the text content to render
 * @param fontFamily    font family (e.g., "DejaVu Sans")
 * @param fontSize      font size in points
 * @param color         text color (e.g., "#FFFFFF")
 * @param positionX     horizontal position (pixels or percentage)
 * @param positionY     vertical position (pixels or percentage)
 * @param startTime     start time on the timeline in seconds
 * @param duration      duration in seconds
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

    /**
     * Creates a simple text overlay.
     */
    public static TimelineTextOverlay of(String id, String text, double startTime, double duration) {
        return new TimelineTextOverlay(id, text, "DejaVu Sans", 24, "#FFFFFF",
                "center", "bottom", startTime, duration, null);
    }
}
