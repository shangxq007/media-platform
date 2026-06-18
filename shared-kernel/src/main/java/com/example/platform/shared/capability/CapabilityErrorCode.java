package com.example.platform.shared.capability;

/**
 * Error codes for capability operations.
 *
 * <p><strong>Contract only:</strong> This defines the error code vocabulary.
 * No runtime error handling is implemented.</p>
 */
public enum CapabilityErrorCode {
    // General errors
    UNKNOWN_ERROR,
    INVALID_REQUEST,
    PERMISSION_DENIED,
    NOT_FOUND,
    CONFLICT,

    // Provider errors
    PROVIDER_NOT_FOUND,
    PROVIDER_DISABLED,
    PROVIDER_TIMEOUT,
    PROVIDER_ERROR,

    // Extension point errors
    EXTENSION_POINT_NOT_FOUND,
    EXTENSION_POINT_NOT_SUPPORTED,

    // Credential errors
    CREDENTIAL_NOT_FOUND,
    CREDENTIAL_EXPIRED,
    CREDENTIAL_INVALID,

    // Artifact errors
    ARTIFACT_NOT_FOUND,
    ARTIFACT_ACCESS_DENIED,

    // Flow errors
    FLOW_NOT_FOUND,
    FLOW_DISABLED,
    FLOW_EXECUTION_FAILED,

    // System action errors
    ACTION_NOT_FOUND,
    ACTION_TIMEOUT,
    ACTION_EXECUTION_FAILED,

    // Rate limiting
    RATE_LIMIT_EXCEEDED,
    QUOTA_EXCEEDED
}
