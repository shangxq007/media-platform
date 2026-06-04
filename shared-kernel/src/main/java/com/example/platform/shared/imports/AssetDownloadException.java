package com.example.platform.shared.imports;

/**
 * Exception thrown when an asset download fails during project import.
 */
public class AssetDownloadException extends RuntimeException {

    private final String reasonCode;

    public AssetDownloadException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public AssetDownloadException(String reasonCode, String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
