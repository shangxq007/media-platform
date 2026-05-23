package com.example.platform.render.domain.timeline;

/**
 * Well-known {@code metadata} / {@code platformExtensions} keys for Internal Timeline 1.0.
 */
public final class TimelinePlatformMetadata {

    public static final String AI_SOURCE = "platform.ai.source";
    public static final String AI_PROMPT_EXCERPT = "platform.ai.promptExcerpt";
    public static final String AI_EDIT_SESSION_ID = "platform.ai.editSessionId";
    public static final String AI_PARENT_JOB_ID = "platform.ai.parentJobId";
    public static final String AI_INTENT = "platform.ai.intent";
    public static final String AI_CONVERSATION_ID = "platform.ai.conversationId";
    public static final String AI_LAST_INSTRUCTION = "platform.ai.lastInstruction";
    public static final String AI_LAST_MODEL = "platform.ai.lastModel";
    public static final String TARGET_SEGMENT_IDS = "platform.targetSegmentIds";

    public static final String EXT_AI_PROPOSALS = "aiProposals";

    private TimelinePlatformMetadata() {}
}
