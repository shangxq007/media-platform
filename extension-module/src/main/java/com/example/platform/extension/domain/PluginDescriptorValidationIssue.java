package com.example.platform.extension.domain;

/**
 * Validation diagnostic (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>Machine-readable, stable-ordered validation issue. Deliberately separate
 * from {@code TimelineDiagnostic} — the plugin registry domain must not be
 * coupled to the Timeline domain.</p>
 *
 * @param code      stable diagnostic code (PLG-001..PLG-016)
 * @param severity  ERROR or WARNING
 * @param fieldPath affected field/path
 * @param message   safe message (no raw payloads or stack traces)
 * @param order     stable sequence number within the validation result
 */
public record PluginDescriptorValidationIssue(
        PluginDiagnosticCode code,
        Severity severity,
        String fieldPath,
        String message,
        int order) {

    public enum Severity {
        ERROR,
        WARNING
    }

    public static PluginDescriptorValidationIssue error(PluginDiagnosticCode code, String fieldPath, int order) {
        return new PluginDescriptorValidationIssue(code, Severity.ERROR, fieldPath, code.message(), order);
    }

    public static PluginDescriptorValidationIssue error(
            PluginDiagnosticCode code, String fieldPath, String message, int order) {
        return new PluginDescriptorValidationIssue(code, Severity.ERROR, fieldPath, message, order);
    }
}
