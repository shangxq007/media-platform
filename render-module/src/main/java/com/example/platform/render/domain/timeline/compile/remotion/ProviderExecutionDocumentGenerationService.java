package com.example.platform.render.domain.timeline.compile.remotion;

import com.example.platform.render.domain.timeline.compile.NormalizedTimeline;
import com.example.platform.render.domain.timeline.compile.execution.ProviderExecutionDocumentDraft;
import com.example.platform.render.domain.timeline.compile.execution.ProviderExecutionDocumentDraftType;
import java.util.List;

/**
 * Internal service for generating provider execution documents.
 *
 * <p>v0: Only supports RemotionInputProps generation.
 * FFmpeg/MLT/GPAC/Blender/Natron/GStreamer drafts are skipped.</p>
 *
 * <p>Internal only — not exposed in public APIs.</p>
 */
public class ProviderExecutionDocumentGenerationService {

    private final RemotionProviderExecutionDocumentGenerator remotionGenerator;

    public ProviderExecutionDocumentGenerationService() {
        this.remotionGenerator = new RemotionProviderExecutionDocumentGenerator();
    }

    /**
     * Generate execution documents for a list of drafts.
     *
     * @param drafts   the provider execution document drafts
     * @param timeline the normalized timeline
     * @return list of generation results (one per draft)
     */
    public List<ProviderExecutionDocumentGenerationResult> generate(
            List<ProviderExecutionDocumentDraft> drafts, NormalizedTimeline timeline) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        return drafts.stream()
                .map(draft -> generateSingle(draft, timeline))
                .toList();
    }

    /**
     * Generate a single execution document from a draft.
     */
    public ProviderExecutionDocumentGenerationResult generateSingle(
            ProviderExecutionDocumentDraft draft, NormalizedTimeline timeline) {
        if (draft == null) {
            return new ProviderExecutionDocumentGenerationResult(
                    null, null, null, null,
                    ProviderExecutionDocumentGenerationStatus.FAILED_CLOSED,
                    false, false, List.of("Draft is null"), null, java.util.Map.of());
        }

        // Route to Remotion generator for Remotion drafts
        if (draft.documentType() == ProviderExecutionDocumentDraftType.REMOTION_INPUT_PROPS_DOCUMENT) {
            return remotionGenerator.generate(draft, timeline);
        }

        // All other draft types are skipped in v0
        return new ProviderExecutionDocumentGenerationResult(
                null, draft.draftId(), draft.providerName(), draft.documentType().name(),
                ProviderExecutionDocumentGenerationStatus.SKIPPED_NON_REMOTION,
                false, false, List.of("Document type not supported in v0: " + draft.documentType()),
                null, java.util.Map.of());
    }
}
