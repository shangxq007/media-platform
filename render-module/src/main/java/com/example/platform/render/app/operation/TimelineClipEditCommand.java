package com.example.platform.render.app.operation;

import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import java.util.List;

/**
 * Shared typed transport command; invokes the canonical Operation authority.
 *
 * <p>MOVE_CLIP can also select a same-type destination track.
 * SPLIT_CLIP requires an exact split time and the caller-supplied right clip identity so the
 * canonical plan (and its idempotent replay) is deterministic.

 */
public record TimelineClipEditCommand(
        String type,
        String baseRevisionId,
        String baseContentHash,
        String clipId,
        String delta,
        Boolean absolute,
        String edge,
        String commandId,
        String splitTime,
        String rightClipId,
        String destinationTrackId) {

    private static final List<String> TYPES =
            List.of("MOVE_CLIP", "TRIM_CLIP", "DELETE_CLIP", "SPLIT_CLIP");

    public TimelineClipEditCommand {
        requireId(baseRevisionId, "baseRevisionId");
        requireHash(baseContentHash, "baseContentHash");
        requireId(clipId, "clipId");
        requireId(commandId, "commandId");
        if (type == null || !TYPES.contains(type)) {
            throw new IllegalArgumentException("unsupported clip edit type");
        }
        if (destinationTrackId != null) {
            if (!"MOVE_CLIP".equals(type)) {
                throw new IllegalArgumentException("destination track is only valid for MOVE_CLIP");
            }
            requireId(destinationTrackId, "destinationTrackId");
        }
        if ("SPLIT_CLIP".equals(type)) {
            if (splitTime == null || splitTime.length() > 64
                    || !splitTime.matches("[+]?[0-9]+(?:/[1-9][0-9]*)?")) {
                throw new IllegalArgumentException("exact rational splitTime required");
            }
            requireId(rightClipId, "rightClipId");
            if (delta != null || absolute != null || edge != null) {
                throw new IllegalArgumentException("SPLIT_CLIP does not accept move/trim parameters");
            }
        } else {
            if (splitTime != null || rightClipId != null) {
                throw new IllegalArgumentException("split fields are only valid for SPLIT_CLIP");
            }
            if (!"DELETE_CLIP".equals(type)) {
                if (delta == null || delta.length() > 64
                        || !delta.matches("[+]?[0-9]+(?:/[1-9][0-9]*)?")) {
                    throw new IllegalArgumentException("exact rational delta required");
                }
            }
            if ("MOVE_CLIP".equals(type) && absolute == null) {
                throw new IllegalArgumentException("absolute flag required for MOVE_CLIP");
            }
            if ("MOVE_CLIP".equals(type) && edge != null) {
                throw new IllegalArgumentException("MOVE_CLIP does not accept trim edge");
            }
            if ("TRIM_CLIP".equals(type)
                    && (edge == null || !List.of("START", "END").contains(edge))) {
                throw new IllegalArgumentException("trim edge must be START or END");
            }
            if ("TRIM_CLIP".equals(type) && absolute != null) {
                throw new IllegalArgumentException("TRIM_CLIP does not accept absolute flag");
            }
            if ("DELETE_CLIP".equals(type)
                    && (delta != null || absolute != null || edge != null)) {
                throw new IllegalArgumentException("DELETE_CLIP does not accept time parameters");
            }
        }
    }

    public OperationRequest toOperationRequest(String projectId) {
        var definition = switch (type) {
            case "MOVE_CLIP" -> destinationTrackId == null ? OperationDefinition.V1.MOVE : OperationDefinition.V3.RELOCATE_CLIP;
            case "TRIM_CLIP" -> OperationDefinition.V1.TRIM;
            case "DELETE_CLIP" -> OperationDefinition.V1.DELETE;
            case "SPLIT_CLIP" -> OperationDefinition.V3.SPLIT_CLIP;
            default -> throw new IllegalArgumentException("unsupported clip edit type");
        };
        OperationParameters parameters = switch (type) {
            case "MOVE_CLIP" -> destinationTrackId == null
                    ? new OperationParameters.MoveParameters(parseTime(delta), absolute)
                    : new OperationParameters.RelocateClipParameters(parseTime(delta), absolute, destinationTrackId);
            case "TRIM_CLIP" -> new OperationParameters.TrimParameters(
                    OperationParameters.TrimParameters.TrimEdge.valueOf(edge), parseTime(delta));
            case "DELETE_CLIP" -> new OperationParameters.NoParameters();
            case "SPLIT_CLIP" -> new OperationParameters.SplitParameters(
                    TimelineClipId.of(rightClipId), parseTime(splitTime));
            default -> throw new IllegalArgumentException("unsupported clip edit type");
        };
        return new OperationRequest(definition.definitionId(), definition.version(),
                new OperationTargetRequest.ClipSelectionTargetRequest(projectId,
                        new SelectionSpec.ExplicitObjectSelection(List.of(TimelineClipId.of(clipId))),
                        SelectionSpec.ExpansionPolicy.EXACT),
                parameters, baseRevisionId, baseContentHash, null);
    }

    private static void requireId(String value, String field) {
        if (value == null || value.length() > 64 || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException(field + " has invalid syntax");
        }
    }

    private static void requireHash(String value, String field) {
        if (value == null || !value.matches("(?:[A-Za-z0-9+/]{43}=|[0-9a-fA-F]{64})")) {
            throw new IllegalArgumentException(field + " must be a canonical SHA-256 Timeline hash");
        }
    }

    private static MediaTime parseTime(String value) {
        return MediaTime.parse(value.contains("/") || "0".equals(value) ? value : value + "/1");
    }
}
