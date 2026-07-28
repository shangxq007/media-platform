package com.example.platform.storage.opendal;

import com.example.platform.render.domain.storage.error.StorageError;

/**
 * Internal storage exception for OpenDAL adapter errors.
 *
 * <p>This exception type is internal to the OpenDAL adapter module and must never
 * cross the {@link com.example.platform.render.domain.storage.provider.StorageProvider}
 * SPI boundary. All errors returned through the SPI are mapped to
 * {@link StorageError.Error} via {@link OpenDalErrorMapper}.
 *
 * <p>OpenDAL native error types and stack traces are intentionally not exposed.
 */
public class OpenDalStorageException extends RuntimeException {

    private final StorageError.ErrorCode platformErrorCode;

    public OpenDalStorageException(StorageError.ErrorCode platformErrorCode, String message) {
        super(redact(message));
        this.platformErrorCode = platformErrorCode;
    }

    public OpenDalStorageException(StorageError.ErrorCode platformErrorCode, String message, Throwable cause) {
        super(redact(message), cause);
        this.platformErrorCode = platformErrorCode;
    }

    public StorageError.ErrorCode platformErrorCode() {
        return platformErrorCode;
    }

    /**
     * Redacts any potential credentials, signed URLs, or sensitive tokens from messages.
     * This is a defense-in-depth measure; callers should still avoid passing sensitive data.
     */
    private static String redact(String message) {
        if (message == null) {
            return null;
        }
        // Redact common credential patterns: X-Amz-Credential, Signature, access_token, etc.
        return message
                .replaceAll("(?i)(X-Amz-Credential|AWSAccessKeyId|access_token|secret)=[^&\\s]+", "$1=[REDACTED]")
                .replaceAll("(?i)(Signature)=[^&\\s]+", "$1=[REDACTED]");
    }

    @Override
    public String toString() {
        // Ensure OpenDAL exception class name is not leaked
        return "OpenDalStorageException{code=" + platformErrorCode + ", message=" + getMessage() + "}";
    }
}
