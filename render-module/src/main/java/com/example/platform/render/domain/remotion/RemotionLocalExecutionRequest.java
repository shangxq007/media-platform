package com.example.platform.render.domain.remotion;

import com.example.platform.render.domain.timeline.compile.remotion.ProviderExecutionDocumentGenerationResult;
import java.util.List;
import java.util.Map;

/**
 * Request for Remotion local execution.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * <p>v0: All requests are refused. No execution occurs.</p>
 *
 * @param documentGenerationResult the document generation result
 * @param runtimeAvailability     runtime availability diagnostics
 * @param providerReadiness       provider readiness diagnostics
 * @param executionPolicy         execution policy
 * @param sandboxPolicy           sandbox policy
 * @param commandPlan              command plan (null if not yet created)
 * @param safeMetadata             safe metadata only
 */
public record RemotionLocalExecutionRequest(
        ProviderExecutionDocumentGenerationResult documentGenerationResult,
        RemotionRuntimeAvailability runtimeAvailability,
        RemotionProviderReadiness providerReadiness,
        RemotionExecutionPolicy executionPolicy,
        RemotionSandboxPolicy sandboxPolicy,
        RemotionExecutionCommandPlan commandPlan,
        Map<String, String> safeMetadata) {

    /**
     * Returns true if the request has a valid document generation result.
     */
    public boolean hasDocument() {
        return documentGenerationResult != null && documentGenerationResult.isGenerated();
    }

    /**
     * Returns true if the document is Remotion type.
     */
    public boolean isRemotionDocument() {
        return documentGenerationResult != null
                && documentGenerationResult.documentType() != null
                && documentGenerationResult.documentType().contains("REMOTION");
    }
}
