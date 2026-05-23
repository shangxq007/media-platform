package com.example.platform.render.app.timeline;

import com.example.platform.render.app.TimelinePatchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Human-in-the-loop AI proposals stored under {@code platformExtensions.aiProposals}.
 */
@Service
public class AiTimelineProposalService {

    private final TimelinePatchService timelinePatchService;

    public AiTimelineProposalService(TimelinePatchService timelinePatchService) {
        this.timelinePatchService = timelinePatchService;
    }

    public List<AiProposalView> listProposals(String timelineJson) {
        return InternalTimelineAiProposals.listProposals(timelineJson).stream()
                .map(AiProposalView::from)
                .toList();
    }

    public String appendPendingPatchProposal(
            String timelineJson, String summary, List<TimelinePatchService.PatchOperation> operations) {
        String id = "prop-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ObjectNode patch = InternalTimelineJson.mapper().createObjectNode();
        ArrayNode ops = InternalTimelineJson.mapper().createArrayNode();
        for (TimelinePatchService.PatchOperation op : operations) {
            ObjectNode item = InternalTimelineJson.mapper().createObjectNode();
            item.put("op", op.op());
            item.put("path", op.path());
            if (op.value() != null) {
                item.set("value", op.value());
            }
            ops.add(item);
        }
        patch.set("operations", ops);
        return InternalTimelineAiProposals.appendProposal(timelineJson, id, "PENDING", summary, patch);
    }

    public ResolveResult adopt(String timelineJson, String proposalId) {
        JsonNode proposal = InternalTimelineAiProposals.findProposal(timelineJson, proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found: " + proposalId));
        String status = proposal.path("status").asText("PENDING");
        if ("REJECTED".equalsIgnoreCase(status)) {
            throw new IllegalStateException("Proposal already rejected: " + proposalId);
        }
        if ("ACCEPTED".equalsIgnoreCase(status)) {
            return new ResolveResult(timelineJson, proposalId, "ACCEPTED", true, List.of());
        }
        String applied = timelineJson;
        JsonNode patch = proposal.get("patch");
        if (patch != null && patch.has("operations")) {
            List<TimelinePatchService.PatchOperation> ops = parseOperations(patch.get("operations"));
            TimelinePatchService.PatchResult result = timelinePatchService.applyPatch(timelineJson, ops);
            if (!result.success()) {
                throw new IllegalStateException("Failed to apply proposal patch: " + result.errors());
            }
            applied = result.timelineJson();
        }
        String updated = InternalTimelineAiProposals.updateProposalStatus(applied, proposalId, "ACCEPTED");
        List<TimelinePatchService.PatchOperation> appliedOps = List.of();
        if (patch != null && patch.has("operations")) {
            appliedOps = parseOperations(patch.get("operations"));
        }
        return new ResolveResult(updated, proposalId, "ACCEPTED", true, appliedOps);
    }

    public ResolveResult reject(String timelineJson, String proposalId) {
        InternalTimelineAiProposals.findProposal(timelineJson, proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found: " + proposalId));
        String updated = InternalTimelineAiProposals.updateProposalStatus(timelineJson, proposalId, "REJECTED");
        return new ResolveResult(updated, proposalId, "REJECTED", false, List.of());
    }

    private static List<TimelinePatchService.PatchOperation> parseOperations(JsonNode array) {
        List<TimelinePatchService.PatchOperation> ops = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return ops;
        }
        for (JsonNode item : array) {
            String op = item.path("op").asText("");
            String path = item.path("path").asText("");
            JsonNode value = item.get("value");
            if (!op.isBlank() && !path.isBlank()) {
                ops.add(new TimelinePatchService.PatchOperation(op, path, value));
            }
        }
        return ops;
    }

    public record AiProposalView(
            String id, String status, String summary, String createdAt, int operationCount) {

        static AiProposalView from(JsonNode node) {
            int opCount = 0;
            JsonNode patch = node.get("patch");
            if (patch != null && patch.path("operations").isArray()) {
                opCount = patch.get("operations").size();
            }
            return new AiProposalView(
                    node.path("id").asText(""),
                    node.path("status").asText("PENDING"),
                    node.path("summary").asText(""),
                    node.path("createdAt").asText(""),
                    opCount);
        }
    }

    public record ResolveResult(
            String timelineJson,
            String proposalId,
            String status,
            boolean applied,
            List<TimelinePatchService.PatchOperation> patchOperations) {}
}
