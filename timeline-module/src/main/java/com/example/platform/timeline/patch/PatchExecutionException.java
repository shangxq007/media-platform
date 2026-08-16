package com.example.platform.timeline.patch;

/**
 * Exception thrown during patch execution (precondition failure, etc).
 */
public class PatchExecutionException extends RuntimeException {
    public PatchExecutionException(String message) {
        super(message);
    }
}
