package com.example.platform.shared.capability;

import java.util.Map;

/**
 * Represents a trigger for automation flows.
 *
 * <p><strong>Contract only:</strong> This defines the trigger model shape.
 * No trigger execution is implemented.</p>
 */
public record AutomationTrigger(
    TriggerType type,
    Map<String, Object> config
) {
    public enum TriggerType {
        EVENT, SCHEDULE, MANUAL
    }
}
