package com.example.platform.extension.domain;

/**
 * Exception thrown by extension execution.
 */
public class ExtensionExecutionException extends Exception {

    private final String extensionKey;
    private final String errorCode;

    public ExtensionExecutionException(String extensionKey, String errorCode, String message) {
        super(message);
        this.extensionKey = extensionKey;
        this.errorCode = errorCode;
    }

    public ExtensionExecutionException(String extensionKey, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.extensionKey = extensionKey;
        this.errorCode = errorCode;
    }

    public String getExtensionKey() { return extensionKey; }
    public String getErrorCode() { return errorCode; }
}
