package com.example.platform.timeline.semantics.selection;

/**
 * Typed fail-closed rejection for a selection that references a clip absent from the
 * authoritative base revision.
 *
 * <p>Extends {@link IllegalArgumentException} so existing callers keep their catch clauses,
 * but carries its own type so callers can map the failure by type instead of inferring the
 * category from a message string.
 */
public final class TimelineSelectionException extends IllegalArgumentException {

    public TimelineSelectionException(String message) {
        super(message);
    }
}
