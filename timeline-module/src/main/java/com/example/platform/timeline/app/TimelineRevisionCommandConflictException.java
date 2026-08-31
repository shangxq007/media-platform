package com.example.platform.timeline.app;

/** Durable canonical revision command id was reused incompatibly. */
public final class TimelineRevisionCommandConflictException extends RuntimeException {
    public TimelineRevisionCommandConflictException(String message) {
        super(message);
    }
}
