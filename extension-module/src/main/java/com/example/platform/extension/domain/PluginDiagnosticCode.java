package com.example.platform.extension.domain;

/**
 * Stable validation diagnostic codes (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>PLG-001 through PLG-016 with stable ordering. Each diagnostic includes
 * code, severity, field/path, stable order and a safe message. Raw descriptor
 * payloads and stack traces are never exposed in diagnostics.</p>
 */
public enum PluginDiagnosticCode {

    PLG_001("PLG-001", "PLUGIN_ID_INVALID", "pluginId", "plugin id is blank or has an invalid format"),
    PLG_002("PLG-002", "PLUGIN_VERSION_INVALID", "pluginVersion", "plugin version is not a valid semantic version"),
    PLG_003("PLG-003", "API_VERSION_UNSUPPORTED", "platformApiVersion", "platform API version is not supported"),
    PLG_004("PLG-004", "VENDOR_BLANK", "vendor", "vendor must not be blank"),
    PLG_005("PLG-005", "CAPABILITIES_EMPTY", "capabilities", "capability list must not be empty"),
    PLG_006("PLG-006", "CAPABILITY_DUPLICATE", "capabilities", "duplicate capability id within plugin"),
    PLG_007("PLG-007", "HANDLED_OBJECTS_EMPTY", "handledObjects", "handled-object list must not be empty"),
    PLG_008("PLG-008", "HANDLED_OBJECT_UNKNOWN", "handledObjects", "handled-object type id is not recognized"),
    PLG_009("PLG-009", "INVOCATION_CONTRACT_INVALID", "invocationContract", "invocation contract values are invalid"),
    PLG_010("PLG-010", "PERMISSION_UNKNOWN", "permissions", "unknown permission id"),
    PLG_011("PLG-011", "RESOURCE_INVALID", "resourceRequirements", "resource requirement bounds are invalid"),
    PLG_012("PLG-012", "RUNTIME_REQUIREMENT_INVALID", "runtimeRequirements", "runtime requirement is not supported"),
    PLG_013("PLG-013", "GUARANTEE_ILLEGAL", "guarantees", "guarantee declaration is illegal (platform authority cannot be granted)"),
    PLG_014("PLG-014", "IMPLEMENTATION_UNAVAILABLE", "providerImplementation", "provider implementation is not available"),
    PLG_015("PLG-015", "DUPLICATE_PLUGIN", "pluginId", "plugin id is already registered"),
    PLG_018("PLG-018", "CAPABILITY_IMPLEMENTATION_DUPLICATE", "capabilityImplementationId",
            "capability implementation id is already registered"),
    PLG_017("PLG-017", "CAPABILITY_NAMESPACE_INVALID", "capabilities",
            "capability id violates the platform-reserved / vendor reverse-DNS namespace rules"),
    PLG_016("PLG-016", "TRUST_REJECTED", "runtimeRequirements.trustLevel", "trust level is not accepted for registration");

    private final String code;
    private final String name;
    private final String fieldPath;
    private final String message;

    PluginDiagnosticCode(String code, String name, String fieldPath, String message) {
        this.code = code;
        this.name = name;
        this.fieldPath = fieldPath;
        this.message = message;
    }

    /** Stable machine-readable code (e.g. {@code PLG-001}). */
    public String code() {
        return code;
    }

    /** Stable symbolic name (e.g. {@code PLUGIN_ID_INVALID}). */
    public String symbolicName() {
        return name;
    }

    /** Default field/path for the diagnostic. */
    public String fieldPath() {
        return fieldPath;
    }

    /** Safe, stable message (no raw payloads or stack traces). */
    public String message() {
        return message;
    }
}
