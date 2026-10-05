package com.example.platform.fontpipeline.manifest;

/** Raised when two partial font manifests cannot be merged into a consistent canonical manifest. */
public class FontManifestMergeException extends RuntimeException {

    public FontManifestMergeException(String message) {
        super(message);
    }

    public FontManifestMergeException(String message, Throwable cause) {
        super(message, cause);
    }
}
