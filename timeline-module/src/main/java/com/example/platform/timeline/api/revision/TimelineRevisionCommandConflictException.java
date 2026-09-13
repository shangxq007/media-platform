package com.example.platform.timeline.api.revision;

/** Durable canonical revision command id was reused incompatibly. */
public final class TimelineRevisionCommandConflictException extends RuntimeException {
    public TimelineRevisionCommandConflictException(String message) {
        super(message);
    }
}
