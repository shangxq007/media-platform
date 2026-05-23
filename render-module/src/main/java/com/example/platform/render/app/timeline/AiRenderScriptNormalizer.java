package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Converts AI chat output into Internal Timeline Schema 1.0 when possible, enabling incremental re-render.
 */
@Service
public class AiRenderScriptNormalizer {

    private final TimelineSpecResolver timelineSpecResolver;
    private final InternalTimelineAdapter internalTimelineAdapter;
    private final InternalTimelineWriter internalTimelineWriter;
    private final InternalTimelineMetadataEnricher metadataEnricher;

    public AiRenderScriptNormalizer(TimelineSpecResolver timelineSpecResolver,
                                    InternalTimelineAdapter internalTimelineAdapter,
                                    InternalTimelineWriter internalTimelineWriter,
                                    InternalTimelineMetadataEnricher metadataEnricher) {
        this.timelineSpecResolver = timelineSpecResolver;
        this.internalTimelineAdapter = internalTimelineAdapter;
        this.internalTimelineWriter = internalTimelineWriter;
        this.metadataEnricher = metadataEnricher;
    }

    /**
     * @return Internal Timeline 1.0 JSON, or best-effort wrapper for legacy paths
     */
    public String normalize(String tenantId, String projectId, String rawAiOutput) {
        return normalize(tenantId, projectId, rawAiOutput, AiTimelineEditContext.of(tenantId, projectId));
    }

    public String normalize(String tenantId, String projectId, String rawAiOutput, AiTimelineEditContext context) {
        if (rawAiOutput == null || rawAiOutput.isBlank()) {
            throw new IllegalArgumentException("AI output is empty");
        }
        String trimmed = rawAiOutput.trim();
        if (timelineSpecResolver.isInternalTimelineJson(trimmed)) {
            return metadataEnricher.enrichJson(trimmed, context, "ai-direct");
        }
        var fromJson = internalTimelineAdapter.toSpec(trimmed);
        if (fromJson.isPresent()) {
            String json = internalTimelineWriter.toJson(enrichMetadata(fromJson.get(), context, trimmed, "normalized"));
            return metadataEnricher.enrichJson(json, context, "normalized");
        }
        if (looksLikeJson(trimmed)) {
            return trimmed;
        }
        return metadataEnricher.enrichJson(
                internalTimelineWriter.toJson(buildPromptPlaceholder(context, trimmed)),
                context,
                "prompt-placeholder");
    }

    private TimelineSpec enrichMetadata(
            TimelineSpec spec, AiTimelineEditContext context, String raw, String source) {
        Map<String, String> meta = new LinkedHashMap<>(spec.metadata() != null ? spec.metadata() : Map.of());
        meta.putAll(metadataEnricher.toMetadataMap(context, source));
        if (raw.length() > 500) {
            meta.put(com.example.platform.render.domain.timeline.TimelinePlatformMetadata.AI_PROMPT_EXCERPT,
                    raw.substring(0, 500));
        } else {
            meta.put(com.example.platform.render.domain.timeline.TimelinePlatformMetadata.AI_PROMPT_EXCERPT, raw);
        }
        return new TimelineSpec(
                spec.id(),
                spec.name(),
                spec.description(),
                spec.tracks(),
                spec.textOverlays(),
                spec.outputSpec(),
                spec.totalDuration(),
                meta);
    }

    private TimelineSpec buildPromptPlaceholder(AiTimelineEditContext context, String prompt) {
        String projectId = context.projectId() != null ? context.projectId() : "project";
        String id = "tl-ai-" + projectId;
        Map<String, String> meta = new LinkedHashMap<>(metadataEnricher.toMetadataMap(context, "prompt-placeholder"));
        String excerpt = prompt.length() > 500 ? prompt.substring(0, 500) : prompt;
        meta.put(com.example.platform.render.domain.timeline.TimelinePlatformMetadata.AI_PROMPT_EXCERPT, excerpt);
        TimelineSpec spec = TimelineSpec.create(id, "AI Generated", TimelineOutputSpec.mp4_1080p30());
        return new TimelineSpec(
                spec.id(),
                "AI: " + (excerpt.length() > 80 ? excerpt.substring(0, 80) + "…" : excerpt),
                prompt,
                spec.tracks(),
                spec.textOverlays(),
                spec.outputSpec(),
                30.0,
                meta);
    }

    private static boolean looksLikeJson(String s) {
        return s.startsWith("{") || s.startsWith("[");
    }
}
