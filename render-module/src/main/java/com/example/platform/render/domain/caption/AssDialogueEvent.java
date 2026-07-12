package com.example.platform.render.domain.caption;

/**
 * Typed ASS dialogue event — one timed subtitle line for libass rendering.
 *
 * <p>Maps from {@link CaptionSegmentSpec} to ASS Dialogue fields.
 * All fields are bounded and text is sanitized.</p>
 *
 * @param start    ASS StartTime as H:MM:SS.cc
 * @param end      ASS EndTime as H:MM:SS.cc
 * @param text     sanitized caption text (no raw ASS override codes)
 */
public record AssDialogueEvent(
        String start,
        String end,
        String text
) {

    /**
     * Maximum length of a single dialogue text field.
     */
    public static final int MAX_TEXT_LENGTH = 10000;

    /**
     * Returns true if the dialogue event has valid bounded fields.
     */
    public boolean isValid() {
        return start != null && !start.isBlank()
                && end != null && !end.isBlank()
                && text != null && !text.isBlank()
                && text.length() <= MAX_TEXT_LENGTH
                && start.matches("\\d:\\d{2}:\\d{2}\\.\\d{2}")
                && end.matches("\\d:\\d{2}:\\d{2}\\.\\d{2}");
    }
}
