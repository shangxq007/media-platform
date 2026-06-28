package com.example.platform.render.domain.workflow;

import java.util.Map;

/**
 * Typed parameter value for workflow steps.
 * Internal domain model.
 */
public record WorkflowParameterValue(
        String type,
        String stringValue,
        Map<String, String> objectValue) {

    public static WorkflowParameterValue ofString(String v) {
        return new WorkflowParameterValue("STRING", v, null);
    }

    public static WorkflowParameterValue ofInt(int v) {
        return new WorkflowParameterValue("NUMBER", String.valueOf(v), null);
    }

    public static WorkflowParameterValue ofBoolean(boolean v) {
        return new WorkflowParameterValue("BOOLEAN", String.valueOf(v), null);
    }
}
