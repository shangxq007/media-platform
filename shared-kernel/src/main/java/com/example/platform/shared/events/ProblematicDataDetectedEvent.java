package com.example.platform.shared.events;

import java.time.Instant;
import java.util.Map;

/**
 * Published when problematic data is detected.
 */
public record ProblematicDataDetectedEvent(
        String recordId,
        String dataType,
        String dataId,
        String problematicType,
        String severity,
        String detectionRule,
        String description,
        Map<String, Object> context,
        Instant detectedAt
) {}
