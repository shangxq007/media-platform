package com.example.platform.storage.opendal;

import com.example.platform.storage.contract.error.StorageError;
import org.apache.opendal.OpenDALException;

import java.io.IOException;
import java.util.Objects;

/**
 * Maps OpenDAL native errors to platform {@link StorageError.ErrorCode} values.
 *
 * <p>OpenDAL exception types and stack traces are NEVER exposed through the SPI.
 * Credentials, signed URLs, and bucket internals in error messages are redacted.
 */
public final class OpenDalErrorMapper {

    private OpenDalErrorMapper() {}

    /**
     * Maps an OpenDAL exception to a platform error code.
     *
     * @param error the OpenDAL exception
     * @return corresponding platform StorageError.ErrorCode
     */
    public static StorageError.ErrorCode map(Throwable error) {
        Objects.requireNonNull(error, "error must not be null");

        if (error instanceof OpenDALException openDalEx) {
            return mapOpenDalCode(openDalEx.getCode());
        }

        // Map standard Java IO exceptions
        if (error instanceof java.io.FileNotFoundException) {
            return StorageError.ErrorCode.STORAGE_OBJECT_NOT_FOUND;
        }
        if (error instanceof java.nio.file.FileAlreadyExistsException) {
            return StorageError.ErrorCode.STORAGE_OBJECT_ALREADY_EXISTS;
        }
        if (error instanceof SecurityException) {
            return StorageError.ErrorCode.STORAGE_DELETE_NOT_AUTHORIZED;
        }
        if (error instanceof IOException ioEx) {
            String msg = ioEx.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return StorageError.ErrorCode.STORAGE_OBJECT_NOT_FOUND;
            }
        }

        // Default: unexpected / not implemented
        return StorageError.ErrorCode.STORAGE_NOT_IMPLEMENTED;
    }

    /**
     * Maps a specific OpenDAL error code to a platform error code.
     */
    private static StorageError.ErrorCode mapOpenDalCode(OpenDALException.Code code) {
        return switch (code) {
            case NotFound -> StorageError.ErrorCode.STORAGE_OBJECT_NOT_FOUND;
            case AlreadyExists -> StorageError.ErrorCode.STORAGE_OBJECT_ALREADY_EXISTS;
            case PermissionDenied -> StorageError.ErrorCode.STORAGE_DELETE_NOT_AUTHORIZED;
            case RateLimited -> StorageError.ErrorCode.STORAGE_PROVIDER_UNAVAILABLE;
            case Unsupported -> StorageError.ErrorCode.STORAGE_NOT_IMPLEMENTED;
            case ConfigInvalid -> StorageError.ErrorCode.STORAGE_NAMESPACE_INVALID;
            case IsADirectory, NotADirectory, IsSameFile, ConditionNotMatch, RangeNotSatisfied -> StorageError.ErrorCode.STORAGE_NOT_IMPLEMENTED;
            case Unexpected -> StorageError.ErrorCode.STORAGE_NOT_IMPLEMENTED;
        };
    }

    /**
     * Sanitizes an error message, removing any potential credential or internal path leakage.
     */
    public static String sanitizeMessage(String message) {
        if (message == null) return "unknown";
        // Redact credentials and signed URLs
        return message
                .replaceAll("(?i)(access_key|secret_key|session_token|authorization)=[^&\\s]+", "$1=[REDACTED]")
                .replaceAll("(?i)(X-Amz-Credential|Signature|AWSAccessKeyId)=[^&\\s]+", "$1=[REDACTED]")
                .replaceAll("(?i)(token|password)=[^&\\s]+", "$1=[REDACTED]");
    }
}
