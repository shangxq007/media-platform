package com.example.platform.fontpipeline;

/** Raised when a font processing pipeline run cannot produce a complete manifest. */
public class FontProcessingException extends RuntimeException {

    public FontProcessingException(String message) {
        super(message);
    }

    public FontProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
