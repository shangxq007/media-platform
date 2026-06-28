package com.example.platform.render.domain.timeline.compile.execution;

import java.util.Map;

/**
 * A draft of a provider execution document.
 *
 * <p>Internal only — represents a planned document without
 * generating actual content (no commands, no XML, no props).</p>
 *
 * <p>This draft is created during provider binding to record
 * what kind of document would be needed for execution.
 * Actual document generation is future work.</p>
 *
 * @param draftId         deterministic draft identifier
 * @param bindingNodeId   the binding node this draft serves
 * @param providerName    target provider name
 * @param documentType    type of document
 * @param requirement     document requirements
 * @param metadata        additional metadata
 * @param generationReady whether all prerequisites for generation are met
 */
public record ProviderExecutionDocumentDraft(
        String draftId,
        String bindingNodeId,
        String providerName,
        ProviderExecutionDocumentDraftType documentType,
        ProviderExecutionDocumentRequirement requirement,
        Map<String, String> metadata,
        boolean generationReady) {

    /**
     * Creates a draft for a binding node.
     */
    public static ProviderExecutionDocumentDraft forNode(
            String draftId, String bindingNodeId, String providerName,
            ProviderExecutionDocumentDraftType documentType) {
        return new ProviderExecutionDocumentDraft(
                draftId, bindingNodeId, providerName, documentType,
                ProviderExecutionDocumentRequirement.of(documentType, providerName),
                Map.of(), false);
    }

    /**
     * Returns true if this draft is ready for document generation.
     * v0 always returns false (no generation implemented).
     */
    public boolean isReadyForGeneration() {
        return generationReady;
    }
}
