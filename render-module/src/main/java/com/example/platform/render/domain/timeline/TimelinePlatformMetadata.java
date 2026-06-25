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

    // Platform asset registry metadata keys (OTIO + XMP Phase 1)
    public static final String PLATFORM_PROJECT_ID = "platform.project_id";
    public static final String PLATFORM_ASSET_REGISTRY_URI = "platform.asset_registry_uri";
    public static final String PLATFORM_ASSET_ID = "platform.asset_id";
    public static final String PLATFORM_ASSET_VERSION = "platform.asset_version";
    public static final String PLATFORM_XMP_URI = "platform.xmp_uri";
    public static final String PLATFORM_ENTITY_REF = "platform.entity_ref";
    public static final String PLATFORM_EFFECT_FAMILY = "platform.effect_family";
    public static final String PLATFORM_PROVIDER_HINT = "platform.provider_hint";
    public static final String PLATFORM_CAPABILITY_CODE = "platform.capability_code";
    public static final String PLATFORM_SEMANTIC_TYPE = "platform.semantic_type";
    public static final String PLATFORM_REVIEW_STATUS = "platform.review_status";

    private TimelinePlatformMetadata() {}
}
