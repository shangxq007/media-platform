package com.example.platform.render.domain.timeline.compile;

/**
 * Normalized caption/subtitle layer derived from TimelineTextOverlay.
 *
 * <p>Represents a single text overlay that will be rendered as a caption/subtitle
 * on top of the video. v0 supports basic text overlay; advanced caption features
 * (word-level timing, animation) are future work.</p>
 *
 * @param layerId          source text overlay identifier
 * @param text             text content to render
 * @param fontFamily       font family name
 * @param fontSize         font size in points
 * @param color            text color (hex, e.g., "#FFFFFF")
 * @param positionX        horizontal position
 * @param positionY        vertical position
 * @param startTime        start time on the timeline in seconds
 * @param duration         duration in seconds
 * @param backgroundColor  background color (null = transparent)
 */
public record NormalizedCaptionLayer(
        String layerId,
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
     * Returns true if this caption layer has valid timing.
     */
    public boolean hasValidTiming() {
        return startTime >= 0 && duration > 0;
    }
}
