package com.example.platform.render.app.timeline;

import com.example.platform.ai.api.AiGatewayPort;
import com.example.platform.ai.domain.ChatResult;
import com.example.platform.render.app.TimelinePatchService;
import com.example.platform.render.domain.timeline.TimelinePlatformMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Multi-turn timeline editing: load base document → AI (timeline-edit) → patch or replace → enrich metadata.
 */
@Service
public class AiTimelineEditService {

    private static final Logger log = LoggerFactory.getLogger(AiTimelineEditService.class);
    private static final int PROMPT_TIMELINE_EXCERPT_MAX = 12_000;

    private final AiGatewayPort aiGatewayPort;
    private final TimelinePatchService timelinePatchService;
    private final TimelineSpecResolver timelineSpecResolver;
    private final InternalTimelineMetadataEnricher metadataEnricher;
    private final BaseJobTimelineLoader baseJobTimelineLoader;
    private final TimelineConversionService timelineConversionService;
    private final AiTimelineProposalService aiTimelineProposalService;

    public AiTimelineEditService(
            AiGatewayPort aiGatewayPort,
            TimelinePatchService timelinePatchService,
            TimelineSpecResolver timelineSpecResolver,
            InternalTimelineMetadataEnricher metadataEnricher,
            BaseJobTimelineLoader baseJobTimelineLoader,
            TimelineConversionService timelineConversionService,
            AiTimelineProposalService aiTimelineProposalService) {
        this.aiGatewayPort = aiGatewayPort;
        this.timelinePatchService = timelinePatchService;
        this.timelineSpecResolver = timelineSpecResolver;
        this.metadataEnricher = metadataEnricher;
        this.baseJobTimelineLoader = baseJobTimelineLoader;
        this.timelineConversionService = timelineConversionService;
        this.aiTimelineProposalService = aiTimelineProposalService;
    }

    public AiTimelineEditResult editTimeline(
            String baseTimelineJson,
            String instruction,
            AiTimelineEditContext context) {
        if (baseTimelineJson == null || baseTimelineJson.isBlank()) {
            throw new IllegalArgumentException("baseTimelineJson is required");
        }
        String internalBase = timelineConversionService.ensureInternalTimelineJson(baseTimelineJson);
        ChatResult ai = aiGatewayPort.chat("timeline-edit", buildPrompt(internalBase, instruction, context));
        AiTimelineEditResponseParser.Parsed parsed =
                AiTimelineEditResponseParser.parse(ai.content(), timelineSpecResolver);
        boolean humanInTheLoop = context.humanInTheLoop();
        String resultJson;
        boolean appliedPatch;
        String pendingProposalId = null;
        if (humanInTheLoop && parsed instanceof AiTimelineEditResponseParser.Parsed.PatchOps patch) {
            resultJson = aiTimelineProposalService.appendPendingPatchProposal(
                    internalBase, instruction, patch.operations());
            appliedPatch = false;
            pendingProposalId = aiTimelineProposalService.listProposals(resultJson).stream()
                    .filter(p -> "PENDING".equalsIgnoreCase(p.status()))
                    .map(AiTimelineProposalService.AiProposalView::id)
                    .reduce((a, b) -> b)
                    .orElse(null);
        } else {
            resultJson = applyParsed(internalBase, parsed);
            appliedPatch = parsed instanceof AiTimelineEditResponseParser.Parsed.PatchOps;
        }
        AiTimelineEditContext enriched = new AiTimelineEditContext(
                context.tenantId(),
                context.projectId(),
                context.editSessionId(),
                context.parentJobId(),
                context.intent(),
                context.conversationId(),
                instruction,
                ai.model(),
                humanInTheLoop);
        resultJson = metadataEnricher.enrichJson(resultJson, enriched, "ai-timeline-edit");
        log.info("AiTimelineEditService: edit complete tenant={} session={} model={} hitl={}",
                context.tenantId(), context.editSessionId(), ai.model(), humanInTheLoop);
        return new AiTimelineEditResult(
                resultJson,
                ai.provider(),
                ai.model(),
                appliedPatch,
                aiTimelineProposalService.listProposals(resultJson),
                pendingProposalId);
    }

    public AiTimelineEditResult editFromBaseJob(
            String tenantId,
            String baseJobId,
            String instruction,
            AiTimelineEditContext context) {
        String base = baseJobTimelineLoader
                .loadInternalTimelineJson(baseJobId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No Internal Timeline 1.0 found for baseJobId=" + baseJobId));
        AiTimelineEditContext ctx = new AiTimelineEditContext(
                context.tenantId(),
                context.projectId(),
                context.editSessionId(),
                baseJobId,
                context.intent(),
                context.conversationId(),
                instruction,
                context.lastModel(),
                context.humanInTheLoop());
        return editTimeline(base, instruction, ctx);
    }

    private String applyParsed(String base, AiTimelineEditResponseParser.Parsed parsed) {
        if (parsed instanceof AiTimelineEditResponseParser.Parsed.FullTimeline full) {
            return full.timelineJson();
        }
        AiTimelineEditResponseParser.Parsed.PatchOps patch =
                (AiTimelineEditResponseParser.Parsed.PatchOps) parsed;
        TimelinePatchService.PatchResult result =
                timelinePatchService.applyPatch(base, patch.operations());
        if (!result.success()) {
            throw new IllegalStateException("Timeline patch failed: " + result.errors());
        }
        return result.timelineJson();
    }

    static String buildPrompt(String baseTimelineJson, String instruction, AiTimelineEditContext context) {
        String excerpt = baseTimelineJson.length() > PROMPT_TIMELINE_EXCERPT_MAX
                ? baseTimelineJson.substring(0, PROMPT_TIMELINE_EXCERPT_MAX) + "\n... [truncated]"
                : baseTimelineJson;
        StringBuilder sb = new StringBuilder();
        sb.append("You edit Internal Timeline Schema 1.0 JSON for a video platform.\n");
        sb.append("Respond with EITHER:\n");
        sb.append("1) Full valid timeline JSON (schemaVersion 1.0), OR\n");
        sb.append("2) JSON object {\"operations\":[{\"op\":\"replace|add|remove\",\"path\":\"/...\",\"value\":...}]}\n");
        sb.append("Use stable entity ids in paths. Do not modify security or set cachePolicy.reusable=true.\n\n");
        if (context.intent() != null && !context.intent().isBlank()) {
            sb.append("Intent: ").append(context.intent()).append("\n");
        }
        if (context.parentJobId() != null && !context.parentJobId().isBlank()) {
            sb.append("Parent job: ").append(context.parentJobId()).append("\n");
        }
        sb.append("\nUser instruction:\n").append(instruction).append("\n\n");
        sb.append("Current timeline JSON:\n").append(excerpt);
        return sb.toString();
    }

    public record AiTimelineEditResult(
            String timelineJson,
            String provider,
            String model,
            boolean appliedPatch,
            java.util.List<AiTimelineProposalService.AiProposalView> proposals,
            String pendingProposalId) {}
}
