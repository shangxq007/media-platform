package com.example.platform.render.domain.timeline.compile.remotion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Result of provider execution document generation.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * @param documentId          deterministic document identifier
 * @param draftId             source draft identifier
 * @param providerName        target provider name
 * @param documentType        type of document
 * @param generationStatus    generation outcome
 * @param generationReady     whether document is ready for execution (false in v0)
 * @param validationPassed    whether validation passed
 * @param validationIssues    validation issues (empty if passed)
 * @param serializedDocument  deterministic JSON (null if not generated)
 * @param metadata            safe metadata
 */
public record ProviderExecutionDocumentGenerationResult(
        String documentId,
        String draftId,
        String providerName,
        String documentType,
        ProviderExecutionDocumentGenerationStatus generationStatus,
        boolean generationReady,
        boolean validationPassed,
        List<String> validationIssues,
        String serializedDocument,
        Map<String, String> metadata) {

    /**
     * Returns true if generation succeeded (with or without warnings).
     */
    public boolean isGenerated() {
        return generationStatus == ProviderExecutionDocumentGenerationStatus.GENERATED
                || generationStatus == ProviderExecutionDocumentGenerationStatus.GENERATED_WITH_WARNINGS;
    }

    /**
     * Returns true if generation was rejected or failed.
     */
    public boolean isRejected() {
        return generationStatus == ProviderExecutionDocumentGenerationStatus.REJECTED_UNSUPPORTED
                || generationStatus == ProviderExecutionDocumentGenerationStatus.REJECTED_INVALID
                || generationStatus == ProviderExecutionDocumentGenerationStatus.FAILED_CLOSED;
    }

    /**
     * Returns true if generation was skipped.
     */
    public boolean isSkipped() {
        return generationStatus == ProviderExecutionDocumentGenerationStatus.SKIPPED_NON_REMOTION;
    }

    /**
     * Compute deterministic document ID.
     */
    public static String computeDocumentId(String draftId, String providerName,
                                             String documentType, String contentHash) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((draftId != null ? draftId : "").getBytes(StandardCharsets.UTF_8));
            md.update((providerName != null ? providerName : "").getBytes(StandardCharsets.UTF_8));
            md.update((documentType != null ? documentType : "").getBytes(StandardCharsets.UTF_8));
            md.update((contentHash != null ? contentHash : "").getBytes(StandardCharsets.UTF_8));
            return "edd-" + HexFormat.of().formatHex(md.digest()).substring(0, 24);
        } catch (Exception e) {
            return "edd-" + draftId;
        }
    }
}
