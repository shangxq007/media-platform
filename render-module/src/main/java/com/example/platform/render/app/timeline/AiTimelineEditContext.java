package com.example.platform.render.app.timeline;

/**
 * Provenance and multi-turn edit linkage stored on Internal Timeline metadata.
 */
public record AiTimelineEditContext(
        String tenantId,
        String projectId,
        String editSessionId,
        String parentJobId,
        String intent,
        String conversationId,
        String lastInstruction,
        String lastModel,
        boolean humanInTheLoop) {

    public static AiTimelineEditContext of(String tenantId, String projectId) {
        return new AiTimelineEditContext(tenantId, projectId, null, null, null, null, null, null, false);
    }

    public static AiTimelineEditContext fromSubmit(
            String tenantId,
            String projectId,
            String editSessionId,
            String parentJobId,
            String intent,
            String instruction) {
        return new AiTimelineEditContext(
                tenantId,
                projectId,
                editSessionId,
                parentJobId,
                intent,
                null,
                instruction,
                null,
                false);
    }
}
