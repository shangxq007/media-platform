package com.example.platform.render.app.operation;

import com.example.platform.operation.batch.BatchStepDescriptor;
import com.example.platform.operation.batch.OperationBatchPlan;
import com.example.platform.timeline.api.revision.RevisionProvenance;

/**
 * Deterministic, format-versioned provenance document for one batch commit.
 *
 * <p>It is stored verbatim in {@code timeline_revision.change_summary_json} inside
 * the revision transaction. The format is bounded by {@link RevisionProvenance}
 * and contains only ordered step identities plus the batch digest — never a
 * second state snapshot.
 */
public final class BatchCommitProvenance {

    public static final String FORMAT_VERSION = "operation-batch-provenance-v1";

    private BatchCommitProvenance() {
    }

    public static RevisionProvenance of(OperationBatchPlan<?> plan, String message) {
        StringBuilder json = new StringBuilder(256 + plan.steps().size() * 220);
        json.append('{');
        field(json, "formatVersion", FORMAT_VERSION).append(',');
        field(json, "batchId", plan.batchId()).append(',');
        field(json, "batchDigest", plan.batchDigest()).append(',');
        field(json, "baseRevisionId", plan.baseRevisionId()).append(',');
        field(json, "finalCandidateHash", plan.finalCandidateHash()).append(',');
        json.append("\"message\":");
        if (message == null) {
            json.append("null");
        } else {
            string(json, message);
        }
        json.append(",\"steps\":[");
        for (int index = 0; index < plan.steps().size(); index++) {
            BatchStepDescriptor step = plan.steps().get(index);
            if (index > 0) {
                json.append(',');
            }
            json.append('{');
            json.append("\"index\":").append(step.index()).append(',');
            field(json, "definitionId", step.definitionId()).append(',');
            field(json, "version", step.version()).append(',');
            field(json, "parameterDigest", step.parameterDigest()).append(',');
            field(json, "targetIdentity", step.targetIdentity()).append(',');
            field(json, "stepPlanDigest", step.stepPlanDigest()).append(',');
            field(json, "candidateHash", step.candidateHash());
            json.append('}');
        }
        json.append("]}");
        return new RevisionProvenance(FORMAT_VERSION, message, json.toString());
    }

    private static StringBuilder field(StringBuilder json, String name, String value) {
        json.append('"').append(name).append("\":");
        string(json, value);
        return json;
    }

    private static void string(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }
}
