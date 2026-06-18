package com.example.platform.shared.capability.flow;

import com.example.platform.shared.capability.AutomationFlow;
import com.example.platform.shared.capability.execution.SystemActionExecutionContext;

import java.util.Map;
import java.util.Objects;

/**
 * Request for automation flow dry-run execution.
 *
 * <p><strong>Contract only:</strong> This defines the dry-run request shape.
 * Runtime execution is not implemented.</p>
 */
public record AutomationFlowDryRunRequest(
    AutomationFlow flow,
    SystemActionExecutionContext context,
    Map<String, Object> input,
    Map<String, Object> options
) {
    public AutomationFlowDryRunRequest {
        if (flow == null) {
            throw new IllegalArgumentException("flow must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        input = input != null ? Map.copyOf(input) : Map.of();
        options = options != null ? Map.copyOf(options) : Map.of();
    }
}
