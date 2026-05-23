package com.example.platform.render.app.timeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Optional {@code platformExtensions.aiProposals} block for human-in-the-loop AI edits.
 */
public final class InternalTimelineAiProposals {

    public static final String PLATFORM_EXTENSIONS = "platformExtensions";
    public static final String AI_PROPOSALS = "aiProposals";

    private InternalTimelineAiProposals() {}

    public static String appendProposal(
            String timelineJson,
            String proposalId,
            String status,
            String summary,
            JsonNode patchDocument) {
        try {
            JsonNode root = InternalTimelineJson.parse(timelineJson);
            if (!root.isObject()) {
                return timelineJson;
            }
            ObjectNode doc = (ObjectNode) root;
            ObjectNode extensions = doc.has(PLATFORM_EXTENSIONS) && doc.get(PLATFORM_EXTENSIONS).isObject()
                    ? (ObjectNode) doc.get(PLATFORM_EXTENSIONS)
                    : InternalTimelineJson.mapper().createObjectNode();
            ArrayNode proposals = extensions.has(AI_PROPOSALS) && extensions.get(AI_PROPOSALS).isArray()
                    ? (ArrayNode) extensions.get(AI_PROPOSALS)
                    : InternalTimelineJson.mapper().createArrayNode();
            ObjectNode entry = InternalTimelineJson.mapper().createObjectNode();
            entry.put("id", proposalId);
            entry.put("status", status != null ? status : "PENDING");
            entry.put("summary", summary != null ? summary : "");
            entry.put("createdAt", Instant.now().toString());
            if (patchDocument != null) {
                entry.set("patch", patchDocument);
            }
            proposals.add(entry);
            extensions.set(AI_PROPOSALS, proposals);
            doc.set(PLATFORM_EXTENSIONS, extensions);
            return InternalTimelineJson.write(doc);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to append AI proposal: " + e.getMessage(), e);
        }
    }

    public static Optional<JsonNode> findProposal(String timelineJson, String proposalId) {
        try {
            JsonNode root = InternalTimelineJson.parse(timelineJson);
            JsonNode proposals = root.path(PLATFORM_EXTENSIONS).path(AI_PROPOSALS);
            if (!proposals.isArray()) {
                return Optional.empty();
            }
            for (JsonNode p : proposals) {
                if (proposalId.equals(p.path("id").asText())) {
                    return Optional.of(p);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static List<JsonNode> listProposals(String timelineJson) {
        try {
            JsonNode root = InternalTimelineJson.parse(timelineJson);
            JsonNode proposals = root.path(PLATFORM_EXTENSIONS).path(AI_PROPOSALS);
            if (!proposals.isArray()) {
                return List.of();
            }
            List<JsonNode> out = new java.util.ArrayList<>();
            for (JsonNode p : proposals) {
                out.add(p);
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String updateProposalStatus(String timelineJson, String proposalId, String newStatus) {
        try {
            JsonNode root = InternalTimelineJson.parse(timelineJson);
            if (!root.isObject()) {
                return timelineJson;
            }
            ObjectNode doc = (ObjectNode) root;
            JsonNode proposals = doc.path(PLATFORM_EXTENSIONS).path(AI_PROPOSALS);
            if (!proposals.isArray()) {
                throw new IllegalArgumentException("No aiProposals on timeline");
            }
            ArrayNode arr = (ArrayNode) proposals;
            boolean found = false;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode p = arr.get(i);
                if (proposalId.equals(p.path("id").asText())) {
                    ObjectNode updated = p.deepCopy();
                    updated.put("status", newStatus);
                    updated.put("resolvedAt", Instant.now().toString());
                    arr.set(i, updated);
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Proposal not found: " + proposalId);
            }
            return InternalTimelineJson.write(doc);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to update proposal status: " + e.getMessage(), e);
        }
    }
}
