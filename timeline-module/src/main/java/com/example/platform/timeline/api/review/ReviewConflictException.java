package com.example.platform.timeline.api.review;
/** The persisted review state cannot accept the requested transition. */
public final class ReviewConflictException extends RuntimeException {
 public ReviewConflictException(String message){super(message);}
}
