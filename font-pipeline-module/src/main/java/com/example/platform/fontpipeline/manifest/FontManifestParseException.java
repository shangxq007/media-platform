package com.example.platform.fontpipeline.manifest;

/** Raised when font bytes cannot be parsed into a partial manifest (malformed or invalid input). */
public class FontManifestParseException extends RuntimeException {

    public FontManifestParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public FontManifestParseException(String message) {
        super(message);
    }
}
