package com.example.platform.timeline.api.composition;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
/** Published Timeline owner contract; implemented by the existing single authority. */
public interface TimelinePatches {
PatchResult applyPatch(String timelineJson, List<PatchOperation> operations);

public record PatchOperation(String op, String path, JsonNode value) {}

public record PatchResult(
            boolean success,
            String timelineJson,
            List<String> appliedOps,
            List<String> errors,
            List<String> warnings) {

        public static PatchResult success(String json, List<String> applied) {
            return new PatchResult(true, json, applied, List.of(), List.of());
        }

        public static PatchResult failed(List<String> errors) {
            return new PatchResult(false, null, List.of(), errors, List.of());
        }
    }
}
