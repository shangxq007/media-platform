package com.example.platform.render.domain.timeline.compile.remotion;

import com.example.platform.render.domain.timeline.compile.NormalizedTimeline;
import com.example.platform.render.domain.timeline.compile.execution.ProviderExecutionDocumentDraft;
import com.example.platform.render.domain.timeline.compile.execution.ProviderExecutionDocumentDraftType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Generates Remotion execution documents from ProviderExecutionDocumentDrafts.
 *
 * <p>Internal only — document generation POC. Does not execute Remotion.</p>
 */
public class RemotionProviderExecutionDocumentGenerator {

    private final RemotionInputPropsGenerator propsGenerator;
    private final RemotionInputPropsSerializer serializer;
    private final RemotionInputPropsValidator validator;

    public RemotionProviderExecutionDocumentGenerator() {
        this.propsGenerator = new RemotionInputPropsGenerator();
        this.serializer = new RemotionInputPropsSerializer();
        this.validator = new RemotionInputPropsValidator();
    }

    /**
     * Generate a Remotion execution document from a draft and timeline.
     *
     * @param draft    the provider execution document draft
     * @param timeline the normalized timeline
     * @return the generation result
     */
    public ProviderExecutionDocumentGenerationResult generate(
            ProviderExecutionDocumentDraft draft, NormalizedTimeline timeline) {

        // Verify draft type
        if (draft == null) {
            return rejectedResult(null, null, "Draft must not be null",
                    ProviderExecutionDocumentGenerationStatus.FAILED_CLOSED);
        }
        if (draft.documentType() != ProviderExecutionDocumentDraftType.REMOTION_INPUT_PROPS_DOCUMENT) {
            return skippedResult(draft, "Non-Remotion draft type: " + draft.documentType());
        }

        // Generate props
        RemotionInputProps props;
        try {
            props = propsGenerator.generate(timeline);
        } catch (IllegalArgumentException e) {
            return rejectedResult(draft, null, "Generation failed: " + e.getMessage(),
                    ProviderExecutionDocumentGenerationStatus.REJECTED_INVALID);
        }

        // Validate
        RemotionInputPropsValidator.ValidationResult validation = validator.validate(props);
        if (!validation.valid()) {
            return rejectedResult(draft, null,
                    "Validation failed: " + String.join("; ", validation.issues()),
                    ProviderExecutionDocumentGenerationStatus.REJECTED_INVALID);
        }

        // Serialize
        String serialized = serializer.serialize(props);
        String contentHash = computeHash(serialized);
        String documentId = ProviderExecutionDocumentGenerationResult.computeDocumentId(
                draft.draftId(), "remotion", "REMOTION_INPUT_PROPS_DOCUMENT", contentHash);

        ProviderExecutionDocumentGenerationStatus status =
                validation.issues().isEmpty()
                        ? ProviderExecutionDocumentGenerationStatus.GENERATED
                        : ProviderExecutionDocumentGenerationStatus.GENERATED_WITH_WARNINGS;

        return new ProviderExecutionDocumentGenerationResult(
                documentId,
                draft.draftId(),
                "remotion",
                "REMOTION_INPUT_PROPS_DOCUMENT",
                status,
                false, // generationReady always false in v0
                true,
                validation.issues(),
                serialized,
                Map.of("provider", "remotion", "schemaVersion", RemotionInputProps.SCHEMA_VERSION));
    }

    private ProviderExecutionDocumentGenerationResult skippedResult(
            ProviderExecutionDocumentDraft draft, String reason) {
        return new ProviderExecutionDocumentGenerationResult(
                null, draft != null ? draft.draftId() : null,
                draft != null ? draft.providerName() : null,
                draft != null ? draft.documentType().name() : null,
                ProviderExecutionDocumentGenerationStatus.SKIPPED_NON_REMOTION,
                false, false, List.of(reason), null, Map.of());
    }

    private ProviderExecutionDocumentGenerationResult rejectedResult(
            ProviderExecutionDocumentDraft draft, String documentId, String reason,
            ProviderExecutionDocumentGenerationStatus status) {
        return new ProviderExecutionDocumentGenerationResult(
                documentId, draft != null ? draft.draftId() : null,
                draft != null ? draft.providerName() : null,
                draft != null ? draft.documentType().name() : null,
                status, false, false, List.of(reason), null, Map.of());
    }

    private String computeHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (Exception e) {
            return "hash-error";
        }
    }
}
