package com.example.platform.shared.capability.registry;

import com.example.platform.shared.capability.CapabilityErrorCode;

/**
 * Exception for capability registry operations.
 *
 * <p><strong>Contract only:</strong> This defines the exception for registry operations.
 * Runtime execution is not implemented.</p>
 */
public class CapabilityRegistryException extends RuntimeException {

    private final CapabilityErrorCode errorCode;

    public CapabilityRegistryException(CapabilityErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CapabilityRegistryException(CapabilityErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public CapabilityErrorCode getErrorCode() {
        return errorCode;
    }
}
