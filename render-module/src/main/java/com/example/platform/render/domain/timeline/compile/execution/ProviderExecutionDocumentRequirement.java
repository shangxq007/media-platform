package com.example.platform.render.domain.timeline.compile.execution;

import java.util.List;
import java.util.Map;

/**
 * Requirements for generating a provider execution document.
 *
 * <p>Internal only — captures what the document would need
 * without generating actual content.</p>
 *
 * @param documentType    type of document to generate
 * @param providerName    target provider name
 * @param requiredInputs  input artifacts required
 * @param outputFormat    expected output format
 * @param parameters      provider-specific parameters
 * @param constraints     execution constraints (timeout, etc.)
 */
public record ProviderExecutionDocumentRequirement(
        ProviderExecutionDocumentDraftType documentType,
        String providerName,
        List<String> requiredInputs,
        String outputFormat,
        Map<String, String> parameters,
        Map<String, String> constraints) {

    /**
     * Creates a simple requirement.
     */
    public static ProviderExecutionDocumentRequirement of(
            ProviderExecutionDocumentDraftType type, String provider) {
        return new ProviderExecutionDocumentRequirement(
                type, provider, List.of(), "mp4", Map.of(), Map.of());
    }

    /**
     * Creates a requirement with inputs and format.
     */
    public static ProviderExecutionDocumentRequirement withInputs(
            ProviderExecutionDocumentDraftType type, String provider,
            List<String> inputs, String format) {
        return new ProviderExecutionDocumentRequirement(
                type, provider, inputs, format, Map.of(), Map.of());
    }
}
